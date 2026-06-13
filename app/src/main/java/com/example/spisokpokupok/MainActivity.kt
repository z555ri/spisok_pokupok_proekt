package com.example.spisokpokupok

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spisokpokupok.ui.theme.SpisokPokupokTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "shopping_data")

private object ShoppingKeys {
    val items = stringPreferencesKey("items")
    val darkTheme = booleanPreferencesKey("dark_theme")
}

data class ShoppingUiState(
    val text: String = "",
    val items: List<String> = emptyList(),
    val isDarkTheme: Boolean = false
)

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val textFlow = MutableStateFlow("")

    private val savedDataFlow = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            Pair(readItems(preferences), preferences[ShoppingKeys.darkTheme] ?: false)
        }

    val uiState: StateFlow<ShoppingUiState> = combine(textFlow, savedDataFlow) { text, savedData ->
        ShoppingUiState(
            text = text,
            items = savedData.first,
            isDarkTheme = savedData.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ShoppingUiState()
    )

    fun onTextChange(text: String) {
        textFlow.value = text
    }

    fun addItem() {
        val item = textFlow.value.trim()
        if (item.isEmpty()) return

        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                val currentItems = readItems(preferences).toMutableList()
                currentItems.add(item)
                preferences[ShoppingKeys.items] = JSONArray(currentItems).toString()
            }
            textFlow.value = ""
        }
    }

    fun removeItem(index: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                val currentItems = readItems(preferences).toMutableList()
                if (index in currentItems.indices) {
                    currentItems.removeAt(index)
                    preferences[ShoppingKeys.items] = JSONArray(currentItems).toString()
                }
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[ShoppingKeys.darkTheme] = enabled
            }
        }
    }

    private fun readItems(preferences: Preferences): List<String> {
        val itemsJson = preferences[ShoppingKeys.items] ?: "[]"
        val jsonArray = JSONArray(itemsJson)
        return List(jsonArray.length()) { index ->
            jsonArray.getString(index)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: ShoppingViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            SpisokPokupokTheme(darkTheme = state.isDarkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ShoppingScreen(
                        state = state,
                        onTextChange = viewModel::onTextChange,
                        onAddClick = viewModel::addItem,
                        onRemoveClick = viewModel::removeItem,
                        onThemeCheckedChange = viewModel::setDarkTheme,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    onTextChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onRemoveClick: (Int) -> Unit,
    onThemeCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Список покупок",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Темная тема")
            Switch(
                checked = state.isDarkTheme,
                onCheckedChange = onThemeCheckedChange
            )
        }

        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Новый товар") },
            singleLine = true
        )

        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Добавить")
        }

        if (state.items.isEmpty()) {
            Text("Список пока пуст")
        } else {
            state.items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onRemoveClick(index) }) {
                        Text("Удалить")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
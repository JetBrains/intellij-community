@file:OptIn(ExperimentalFoundationApi::class)

package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.singleWindowApplication
import kotlinx.coroutines.launch
import org.jetbrains.jewel.themes.darcula.IntelliJThemeLight
import org.jetbrains.jewel.themes.darcula.LocalPalette
import org.jetbrains.jewel.themes.darcula.components.Button
import org.jetbrains.jewel.themes.darcula.components.FocusableLazyColumn
import org.jetbrains.jewel.themes.darcula.components.Text
import org.jetbrains.jewel.themes.darcula.components.rememberFocusableLazyListState
import java.awt.TextField

fun main() = singleWindowApplication {
    IntelliJThemeLight {
        val state = rememberFocusableLazyListState()
        val scope = rememberCoroutineScope()
        var goto by remember { mutableStateOf("0") }
        Column {
            TextField(value = goto, onValueChange = { goto = it })
            FocusableLazyColumn(
                modifier = Modifier.background(LocalPalette.current.background).fillMaxSize(),
                state = state
            ) {
                stickyHeader {
                    Button({ scope.launch { goto.toIntOrNull()?.let { state.focusItem(it) } } }) {
                        Text("Go to $goto")
                    }
                }
                repeat(100) {
                    item {
                        Text("Hello $it", color = Color.Black)
                    }
                }
            }
        }
    }
}

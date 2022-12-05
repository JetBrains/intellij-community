@file:OptIn(ExperimentalTime::class, ExperimentalComposeUiApi::class, ExperimentalSplitPaneApi::class)

package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.themes.darcula.IntelliJTheme
import org.jetbrains.jewel.themes.darcula.components.Checkbox
import org.jetbrains.jewel.themes.darcula.components.Surface
import org.jetbrains.jewel.themes.darcula.components.Tab
import org.jetbrains.jewel.themes.darcula.components.TabRow
import org.jetbrains.jewel.themes.darcula.components.TabScope
import org.jetbrains.jewel.themes.darcula.components.Text
import org.jetbrains.jewel.themes.darcula.components.rememberTabContainerState
import kotlin.time.ExperimentalTime

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
fun main() = singleWindowApplication {
    var isDarkTheme by remember { mutableStateOf(true) }
    IntelliJTheme(isDarkTheme) {
        Surface {
            Column {
                Row(Modifier.focusable()) {
                    Text("Dark theme:")
                    Checkbox(checked = isDarkTheme, onCheckedChange = { isDarkTheme = it })
                }
                val tabState = rememberTabContainerState("1")
                TabRow(tabState, ) {
                    Section("1", "One")
                    Section("2", "Two")
                    Section("3", "Three")
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    when (tabState.selectedKey) {
                        "1" -> Text("Content of One")
                        "2" -> Text("Content of Two")
                        "3" -> Text("Content of Three")
                    }
                }
            }
        }
    }
}

@Composable
private fun TabScope<String>.Section(key: String, caption: String) {
    Tab(key) {
        Text(caption)
    }
}

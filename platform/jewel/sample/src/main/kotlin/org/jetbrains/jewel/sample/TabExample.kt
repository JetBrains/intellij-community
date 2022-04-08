@file:OptIn(ExperimentalTime::class, ExperimentalComposeUiApi::class, ExperimentalSplitPaneApi::class)

package org.jetbrains.jewel.sample

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.components.Checkbox
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Tab
import org.jetbrains.jewel.theme.intellij.components.TabRow
import org.jetbrains.jewel.theme.intellij.components.Text
import org.jetbrains.jewel.theme.intellij.components.rememberTabContainerState
import kotlin.time.ExperimentalTime

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@OptIn(ExperimentalFoundationApi::class)
fun main() = singleWindowApplication {
    var isDarkTheme by remember { mutableStateOf(true) }
    IntelliJTheme(isDarkTheme) {
        Surface {
            Column {
                Row(Modifier.focusable()) {
                    Text("Dark theme:")
                    Checkbox(checked = isDarkTheme, onCheckedChange = { isDarkTheme = it })
                }
                val tabState = rememberTabContainerState(1)
                TabRow(tabState, ) {
                    Tab(1) { Text("One") }
                    Tab(2) { Text("Two") }
                    Tab(3) { Text("Three") }
                }
            }
        }
    }
}

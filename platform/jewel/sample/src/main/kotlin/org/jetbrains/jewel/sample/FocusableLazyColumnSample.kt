@file:OptIn(ExperimentalFoundationApi::class)

package org.jetbrains.jewel.sample

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.singleWindowApplication
import kotlinx.coroutines.launch
import org.jetbrains.jewel.theme.intellij.IntelliJThemeLight
import org.jetbrains.jewel.theme.intellij.LocalPalette
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.FocusableLazyColumn
import org.jetbrains.jewel.theme.intellij.components.Text
import org.jetbrains.jewel.theme.intellij.components.rememberFocusableLazyListState

fun main() = singleWindowApplication {
    IntelliJThemeLight {
        val state = rememberFocusableLazyListState()
        val scope = rememberCoroutineScope()
        FocusableLazyColumn(
            modifier = Modifier.background(LocalPalette.current.background).fillMaxSize(),
            state = state
        ) {
            stickyHeader {
                Button({ scope.launch { state.focusItem(0) } }) {
                    Text("Go to 0")
                }
            }
            repeat(100) {
                item { Text("Hello $it", color = Color.Black) }
            }
        }

    }
}

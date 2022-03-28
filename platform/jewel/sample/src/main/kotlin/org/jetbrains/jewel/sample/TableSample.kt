package org.jetbrains.jewel.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDark
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Table
import org.jetbrains.jewel.theme.toolbox.components.Text

fun main() {
    singleWindowApplication {
        IntelliJThemeDark {
            Surface(modifier = Modifier.fillMaxSize()) {
                val model = (0..30).map { i ->
                    (0..6).map { j -> "Hello ${((i + 1) * (j + 1) - 1)}" }.toTypedArray()
                }.toTypedArray()
                Table(model, Modifier.matchParentSize(), 3.dp) {
                    Text("Hello $it", maxLines = 2)
                }
            }
        }
    }
}

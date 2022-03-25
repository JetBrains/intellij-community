package org.jetbrains.jewel.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDark
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Table
import org.jetbrains.jewel.theme.toolbox.components.Text

fun main() {
    singleWindowApplication {
        IntelliJThemeDark {
            Surface(modifier = Modifier.fillMaxSize()) {
                val model = arrayOf(
                    arrayOf(0, 1, 2),
                    arrayOf(3, 4, 5),
                    arrayOf(6, 7, 8),
                )
                Table(model, Modifier.matchParentSize()) {
                    Text("Hello $it")
                }
            }
        }
    }
}

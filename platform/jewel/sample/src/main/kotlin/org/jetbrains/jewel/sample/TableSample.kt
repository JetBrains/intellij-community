package org.jetbrains.jewel.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDark
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Table
import org.jetbrains.jewel.theme.intellij.components.TableView
import org.jetbrains.jewel.theme.intellij.components.Text

fun main() {
    singleWindowApplication {
        IntelliJThemeDark {
            Surface(modifier = Modifier.fillMaxSize()) {
                val modelContents = Table(30, 10) { i, j -> "Hello ${((i + 1) * (j + 1) - 1)}" }
                TableView(modelContents, Modifier.matchParentSize(), 3.dp) { model, i, j ->
                    Text(
                        model,
                        softWrap = false,
                        modifier = Modifier.background(
                            when {
                                j % 2 == 0 -> when {
                                    i % 2 == 0 -> Color.Red
                                    else -> Color.Blue
                                }
                                else -> when {
                                    i % 2 != 0 -> Color.Red
                                    else -> Color.Blue
                                }
                            }
                        ).fillMaxSize()
                    )
                }
            }
        }
    }
}

package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.components.Text
import org.jetbrains.jewel.themes.darcula.standalone.IntelliJTheme

fun main() = singleWindowApplication(
    title = "TODO: sample app"
) {
    IntelliJTheme(isDark = false) {
        Box(Modifier.fillMaxSize()) {
            Text("TODO", fontSize = 48.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

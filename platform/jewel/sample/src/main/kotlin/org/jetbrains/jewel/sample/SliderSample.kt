package org.jetbrains.jewel.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.theme.intellij.IntelliJMetrics
import org.jetbrains.jewel.theme.intellij.IntelliJPalette
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.components.Checkbox
import org.jetbrains.jewel.theme.intellij.darcula
import org.jetbrains.jewel.theme.intellij.default
import org.jetbrains.jewel.theme.intellij.light

fun main() = singleWindowApplication(
    title = "JSlider sample"
) {
    var isDarkMode by remember { mutableStateOf(false) }

    IntelliJTheme(isDark = isDarkMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isDarkMode, onCheckedChange = { isDarkMode = it }, text = "Dark mode")
            }

            val borderColor = if (isDarkMode) IntelliJPalette.Separator.darcula.color else IntelliJPalette.Separator.light.color
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
                    .border(IntelliJMetrics.Separator.default.strokeWidth, borderColor, RoundedCornerShape(4.dp))
            ) {

            }
        }
    }
}

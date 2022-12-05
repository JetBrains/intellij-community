package org.jetbrains.jewel.samples.standalone

import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.themes.darcula.IntelliJMetrics
import org.jetbrains.jewel.themes.darcula.IntelliJTheme
import org.jetbrains.jewel.themes.darcula.components.Checkbox
import org.jetbrains.jewel.themes.darcula.components.Slider
import org.jetbrains.jewel.themes.darcula.default
import org.jetbrains.jewel.themes.darcula.styles.SliderStyle

fun main() = singleWindowApplication(
    title = "JSlider sample"
) {
    var isDarkMode by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(50) }

    IntelliJTheme(isDark = isDarkMode) {
        val scrollState = rememberScrollState()
        Box(
            Modifier.fillMaxSize()
                .background(IntelliJTheme.palette.background)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = isDarkMode, onCheckedChange = { isDarkMode = it }, text = "Dark mode")
                }

                Box(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize()
                        .border(IntelliJMetrics.Separator.default.strokeWidth, IntelliJTheme.palette.separator.color, RoundedCornerShape(4.dp))
                ) {
                    Slider(
                        value,
                        modifier = Modifier.padding(24.dp).fillMaxWidth().height(80.dp),
                        style = SliderStyle(palette = IntelliJTheme.palette, typography = IntelliJTheme.typography, paintTicks = true),
                    ) { value = it }
                }
            }
            VerticalScrollbar(
                ScrollbarAdapter(scrollState),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(end = 2.dp)
            )
        }
    }
}

package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@Composable
fun Tooltips() {
    var toggleEnabled by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(true) }
    LaunchedEffect(toggleEnabled) {
        if (!toggleEnabled) return@LaunchedEffect

        while (true) {
            delay(1.seconds)
            enabled = !enabled
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Tooltip(tooltip = { Text("This is a tooltip") }, enabled = enabled) {
            // Any content works — this is a button just because it's focusable
            DefaultButton({}) { Text("Hover me!") }
        }

        CheckboxRow("Enabled", enabled, { enabled = it })

        CheckboxRow("Toggle enabled every 1s", toggleEnabled, { toggleEnabled = it })
    }
}

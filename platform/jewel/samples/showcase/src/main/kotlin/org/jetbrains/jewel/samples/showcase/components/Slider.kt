package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Slider

@Composable
public fun Sliders(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        var value1 by remember { mutableFloatStateOf(.45f) }
        Slider(value = value1, onValueChange = { value1 = it })

        var value2 by remember { mutableFloatStateOf(.7f) }
        Slider(value = value2, onValueChange = { value2 = it }, enabled = false)

        var value3 by remember { mutableFloatStateOf(33f) }
        Slider(value = value3, onValueChange = { value3 = it }, steps = 10, valueRange = 0f..100f)

        var value4 by remember { mutableFloatStateOf(23f) }
        Slider(value = value4, onValueChange = { value4 = it }, steps = 10, valueRange = 0f..100f, enabled = false)
    }
}

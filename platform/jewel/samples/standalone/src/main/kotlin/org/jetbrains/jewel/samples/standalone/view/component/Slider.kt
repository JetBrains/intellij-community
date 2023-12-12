package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.component.Slider

@Composable
@View(title = "Sliders", position = 12)
fun Sliders() {
    var value1 by remember { mutableStateOf(.45f) }
    Slider(
        value = value1,
        onValueChange = { value1 = it },
    )

    var value2 by remember { mutableStateOf(.7f) }
    Slider(
        value = value2,
        onValueChange = { value2 = it },
        enabled = false,
    )

    var value3 by remember { mutableStateOf(33f) }
    Slider(
        value = value3,
        onValueChange = { value3 = it },
        steps = 10,
        valueRange = 0f..100f,
    )

    var value4 by remember { mutableStateOf(23f) }
    Slider(
        value = value4,
        onValueChange = { value4 = it },
        steps = 10,
        valueRange = 0f..100f,
        enabled = false,
    )
}

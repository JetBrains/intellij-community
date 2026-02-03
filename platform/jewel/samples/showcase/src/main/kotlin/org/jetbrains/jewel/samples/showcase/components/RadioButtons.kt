package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.RadioButtonRow

@Composable
public fun RadioButtons(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        var index by remember { mutableIntStateOf(0) }
        RadioButtonRow(text = "Default", selected = index == 0, onClick = { index = 0 })

        RadioButtonRow(text = "Error", selected = index == 1, onClick = { index = 1 }, outline = Outline.Error)

        RadioButtonRow(text = "Warning", selected = index == 2, onClick = { index = 2 }, outline = Outline.Warning)

        RadioButtonRow(text = "Disabled", selected = index == 3, onClick = { index = 3 }, enabled = false)
    }
}

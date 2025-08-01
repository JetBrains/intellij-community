package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow

@Composable
public fun Checkboxes(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        var checked by remember { mutableStateOf(ToggleableState.Off) }
        var checked2 by remember { mutableStateOf(ToggleableState.Off) }
        var checked3 by remember { mutableStateOf(ToggleableState.Off) }
        TriStateCheckboxRow(
            "Checkbox",
            checked,
            onClick = {
                checked =
                    when (checked) {
                        ToggleableState.On -> ToggleableState.Off
                        ToggleableState.Off -> ToggleableState.Indeterminate
                        ToggleableState.Indeterminate -> ToggleableState.On
                    }
            },
        )
        TriStateCheckboxRow(
            "Error",
            checked2,
            onClick = {
                checked2 =
                    when (checked2) {
                        ToggleableState.On -> ToggleableState.Off
                        ToggleableState.Off -> ToggleableState.Indeterminate
                        ToggleableState.Indeterminate -> ToggleableState.On
                    }
            },
            outline = Outline.Error,
        )
        TriStateCheckboxRow(
            "Warning",
            checked3,
            onClick = {
                checked3 =
                    when (checked3) {
                        ToggleableState.On -> ToggleableState.Off
                        ToggleableState.Off -> ToggleableState.Indeterminate
                        ToggleableState.Indeterminate -> ToggleableState.On
                    }
            },
            outline = Outline.Warning,
        )
        TriStateCheckboxRow("Disabled", checked, onClick = {}, enabled = false)
    }
}

package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow

@Composable
fun Checkboxes() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        var checked by remember { mutableStateOf(ToggleableState.On) }
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
            checked,
            onClick = {
                checked =
                    when (checked) {
                        ToggleableState.On -> ToggleableState.Off
                        ToggleableState.Off -> ToggleableState.Indeterminate
                        ToggleableState.Indeterminate -> ToggleableState.On
                    }
            },
            outline = Outline.Error,
        )
        TriStateCheckboxRow(
            "Warning",
            checked,
            onClick = {
                checked =
                    when (checked) {
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

package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SegmentedControls() {
    var selectedButtonIndex by remember { mutableStateOf(0) }
    val buttonIds = listOf(0, 1, 2, 3)
    val buttons =
        remember(selectedButtonIndex) {
            buttonIds.map { index ->
                SegmentedControlButtonData(
                    selected = index == selectedButtonIndex,
                    content = { _ -> Text("Button ${index + 1}") },
                    onSelect = { selectedButtonIndex = index },
                )
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SegmentedControl(buttons = buttons, enabled = true)

        SegmentedControl(buttons = buttons, enabled = false)
    }
}

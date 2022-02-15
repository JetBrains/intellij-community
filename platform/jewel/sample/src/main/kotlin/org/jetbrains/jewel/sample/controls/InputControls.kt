package org.jetbrains.jewel.sample.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.toolbox.components.Checkbox
import org.jetbrains.jewel.theme.toolbox.components.CheckboxRow
import org.jetbrains.jewel.theme.toolbox.components.RadioButtonRow
import org.jetbrains.jewel.theme.toolbox.components.Switch
import org.jetbrains.jewel.theme.toolbox.components.Text
import org.jetbrains.jewel.theme.toolbox.components.TextField
import org.jetbrains.jewel.theme.toolbox.metrics

enum class RadioSample {
    Enabled, Disabled, Automatic, Unavailable
}

@Composable
fun InputControls() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding),
        modifier = Modifier.fillMaxSize().padding(Styles.metrics.largePadding),
    ) {
        val switchState = remember { mutableStateOf(false) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Work in background")
            Switch(checked = switchState.value, onCheckedChange = { switchState.value = it })
        }

        val checkboxState1 = remember { mutableStateOf(false) }
        Checkbox(checked = checkboxState1.value, onCheckedChange = { checkboxState1.value = it })

        Spacer(Modifier.height(Styles.metrics.smallPadding))

        val checkboxState2 = remember { mutableStateOf(false) }
        CheckboxRow(checked = checkboxState2.value, onCheckedChange = { checkboxState2.value = it }) {
            Text("Enable various magic", Modifier.alignByBaseline())
        }

        val checkboxState3 = remember { mutableStateOf(false) }
        Checkbox(
            "Enable dangerous features",
            checked = checkboxState3.value,
            onCheckedChange = { checkboxState3.value = it })

        Checkbox(
            "This is a checkbox\nwith multiple lines\nof content to see the alignment",
            remember { mutableStateOf(false) }
        )
        Checkbox("Disabled", false, {}, enabled = false)
        Checkbox("Checked and disabled", true, {}, enabled = false)

        Spacer(Modifier.height(Styles.metrics.smallPadding))
        val radioState = remember { mutableStateOf(RadioSample.Automatic) }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding)) {
            RadioButtonRow(radioState, RadioSample.Automatic) {
                Text("Automatic detection of the property", Modifier.alignByBaseline())
            }
            RadioButtonRow(radioState, RadioSample.Enabled) {
                Text("Enable the property", Modifier.alignByBaseline())
            }
            RadioButtonRow(radioState, RadioSample.Disabled) {
                Text("Disable the property", Modifier.alignByBaseline())
            }
            RadioButtonRow(radioState, RadioSample.Unavailable, enabled = false) {
                Text("Unavailable", Modifier.alignByBaseline())
            }
        }

        Spacer(Modifier.height(Styles.metrics.smallPadding))
        val textFieldState = remember { mutableStateOf("Enter something…") }
        TextField(textFieldState.value, { textFieldState.value = it })

        Spacer(Modifier.height(Styles.metrics.largePadding))
        Row(horizontalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding)) {
            Button({}) {
                Text("OK")
            }
            Button({}) {
                Text("Do jump and float")
            }
            Button({}, enabled = false) {
                Text("Cancel")
            }
        }
    }
}

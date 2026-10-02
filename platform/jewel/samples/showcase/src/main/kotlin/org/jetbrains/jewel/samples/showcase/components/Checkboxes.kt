package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.InfoText
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TriStateCheckbox
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow
import org.jetbrains.jewel.ui.theme.checkboxStyle
import org.jetbrains.jewel.ui.typography
import org.jetbrains.skiko.hostOs

/** Showcases the Checkbox component. */
@Composable
public fun Checkboxes(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SimpleCheckboxes()
        CheckboxRow()
        TriStateCheckboxRow()
        CheckboxWithSecondaryLabel()
    }
}

@Composable
private fun SimpleCheckboxes() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Main Checkboxes", style = JewelTheme.typography.h1TextStyle, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Checkbox")
                var checked by remember { mutableStateOf(false) }
                Checkbox(checked, onCheckedChange = { checked = !checked })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tri-State Checkbox")
                var triStateChecked by remember { mutableStateOf(ToggleableState.Off) }
                TriStateCheckbox(
                    triStateChecked,
                    onClick = {
                        triStateChecked =
                            when (triStateChecked) {
                                ToggleableState.On -> ToggleableState.Off
                                ToggleableState.Off -> ToggleableState.Indeterminate
                                ToggleableState.Indeterminate -> ToggleableState.On
                            }
                    },
                )
            }
        }

        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CheckboxRow() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Checkbox Row", style = JewelTheme.typography.h1TextStyle, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            var checked1 by remember { mutableStateOf(false) }
            var checked2 by remember { mutableStateOf(false) }
            var checked3 by remember { mutableStateOf(false) }

            CheckboxRow(text = "No outline", checked1, onCheckedChange = { checked1 = !checked1 })
            CheckboxRow(
                text = "Error outline",
                checked2,
                onCheckedChange = { checked2 = !checked2 },
                outline = Outline.Error,
            )
            CheckboxRow(
                text = "Warning outline",
                checked3,
                onCheckedChange = { checked3 = !checked3 },
                outline = Outline.Warning,
            )
            CheckboxRow(text = "Disabled", checked1, onCheckedChange = {}, enabled = false)
        }
        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TriStateCheckboxRow() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tri-State Checkboxes", style = JewelTheme.typography.h1TextStyle, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CheckboxWithSecondaryLabel() {
    val checkboxStyleMetrics = JewelTheme.checkboxStyle.metrics
    var checked by remember { mutableStateOf(false) }

    val ctrlTabGlyphs = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK).toShortcutText()
    val ctrlShiftTabGlyphs =
        KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
            .toShortcutText()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Checkbox With Secondary Label", style = JewelTheme.typography.h1TextStyle, fontWeight = FontWeight.Bold)
        Column {
            CheckboxRow(checked, onCheckedChange = { checked = !checked }) {
                Text("Support screen readers")
                InfoText("Requires restart", Modifier.padding(start = 12.dp))
            }
            InfoText(
                "$ctrlTabGlyphs and $ctrlShiftTabGlyphs will navigate UI controls in dialogs and will not be available" +
                    " for switching editor tabs or other IDE actions. Tooltips on mouse hover will be disabled.",
                Modifier.padding(start = checkboxStyleMetrics.checkboxSize.width + checkboxStyleMetrics.iconContentGap),
            )
        }
    }
}

/**
 * Formats this key stroke the way the IDE displays shortcuts: `⌃⇧⇥` on macOS, `Ctrl+Shift+Tab` elsewhere.
 *
 * [KeyEvent.getModifiersExText] always joins modifiers with `+`, even on macOS, where the glyphs are expected to be
 * adjacent, so the delimiter is stripped from the modifiers part only.
 */
private fun KeyStroke.toShortcutText(): String {
    val key = KeyEvent.getKeyText(keyCode)
    if (modifiers == 0) return key

    val modifiersText = KeyEvent.getModifiersExText(modifiers)
    return if (hostOs.isMacOS) modifiersText.replace("+", "") + key else "$modifiersText+$key"
}

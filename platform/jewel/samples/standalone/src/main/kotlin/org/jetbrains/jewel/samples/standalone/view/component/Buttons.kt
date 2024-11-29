package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SelectableIconActionButton
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconActionButton
import org.jetbrains.jewel.ui.component.ToggleableIconButton
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.hints.Stroke

@Composable
fun Buttons() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NormalButtons()

        var selectedIndex by remember { mutableIntStateOf(0) }
        IconButtons(selectedIndex == 1) { selectedIndex = 1 }
        IconActionButtons(selectedIndex == 2) { selectedIndex = 2 }

        ActionButtons()
    }
}

@Composable
private fun NormalButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = {}) { Text("Outlined") }

        OutlinedButton(onClick = {}, enabled = false) { Text("Outlined Disabled") }

        DefaultButton(onClick = {}) { Text("Default") }

        DefaultButton(onClick = {}, enabled = false) { Text("Default disabled") }
    }
}

@Composable
private fun IconButtons(selected: Boolean, onSelectableClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("IconButton", style = Typography.h4TextStyle())

        Text("Focusable:")

        IconButton(onClick = {}) { Icon(key = AllIconsKeys.Actions.Close, contentDescription = "IconButton") }

        Text("Not focusable:")

        IconButton(onClick = {}, focusable = false) {
            Icon(key = AllIconsKeys.Actions.Close, contentDescription = "IconButton")
        }

        Text("Selectable:")

        SelectableIconButton(onClick = onSelectableClick, selected = selected) { state ->
            val tint by LocalIconButtonStyle.current.colors.selectableForegroundFor(state)
            Icon(
                key = AllIconsKeys.Actions.MatchCase,
                contentDescription = "SelectableIconButton",
                hints = arrayOf(Selected(selected), Stroke(tint)),
            )
        }

        Text("Toggleable:")

        var checked by remember { mutableStateOf(false) }
        ToggleableIconButton(onValueChange = { checked = !checked }, value = checked) { state ->
            val tint by LocalIconButtonStyle.current.colors.toggleableForegroundFor(state)
            Icon(
                key = AllIconsKeys.Actions.MatchCase,
                contentDescription = "ToggleableIconButton",
                hints = arrayOf(Selected(checked), Stroke(tint)),
            )
        }
    }
}

@Composable
private fun IconActionButtons(selected: Boolean, onSelectableClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("IconActionButton", style = Typography.h4TextStyle())

        Text("With tooltip:")
        IconActionButton(key = AllIconsKeys.Actions.Copy, contentDescription = "IconActionButton", onClick = {}) {
            Text("I am a tooltip")
        }

        Text("Without tooltip:")
        IconActionButton(key = AllIconsKeys.Actions.Copy, contentDescription = "IconActionButton", onClick = {})

        Text("Selectable:")
        SelectableIconActionButton(
            key = AllIconsKeys.Actions.Copy,
            contentDescription = "SelectableIconActionButton",
            selected = selected,
            onClick = onSelectableClick,
        )

        Text("Toggleable:")
        var checked by remember { mutableStateOf(false) }
        ToggleableIconActionButton(
            key = AllIconsKeys.Actions.Copy,
            contentDescription = "SelectableIconActionButton",
            value = checked,
            onValueChange = { checked = it },
        )
    }
}

@Composable
private fun ActionButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ActionButton", style = Typography.h4TextStyle())

        Text("With tooltip:")

        ActionButton(onClick = {}, tooltip = { Text("I am a tooltip") }) { Text("Hover me!") }

        Text("Without tooltip:")

        ActionButton(onClick = {}) { Text("Do something") }
    }
}

package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.DefaultSplitButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.OutlinedSplitButton
import org.jetbrains.jewel.ui.component.SelectableIconActionButton
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconActionButton
import org.jetbrains.jewel.ui.component.ToggleableIconButton
import org.jetbrains.jewel.ui.component.items
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.badge.DotBadgeShape
import org.jetbrains.jewel.ui.painter.hints.Badge
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.hints.Stroke
import org.jetbrains.jewel.ui.theme.transparentIconButtonStyle
import org.jetbrains.jewel.ui.typography

@Composable
public fun Buttons(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        NormalButtons()

        var selectedIndex by remember { mutableIntStateOf(0) }
        IconButtons(selectedIndex == 1) { selectedIndex = 1 }
        IconActionButtons(selectedIndex == 2) { selectedIndex = 2 }

        ActionButtons()

        SplitButtons()
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("IconButton", style = JewelTheme.typography.h4TextStyle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Without background:")

            IconButton(style = JewelTheme.transparentIconButtonStyle, onClick = {}) {
                Icon(key = AllIconsKeys.Actions.Close, contentDescription = "IconButton")
            }
        }
    }
}

@Composable
private fun IconActionButtons(selected: Boolean, onSelectableClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("IconActionButton", style = JewelTheme.typography.h4TextStyle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
}

@Composable
private fun ActionButtons() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ActionButton", style = JewelTheme.typography.h4TextStyle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("With tooltip:")

            ActionButton(onClick = {}, tooltip = { Text("I am a tooltip") }) { Text("Hover me!") }

            Text("Without tooltip:")

            ActionButton(onClick = {}) { Text("Do something") }
        }
    }
}

@Composable
private fun SplitButtons() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SplitButton", style = JewelTheme.typography.h4TextStyle)

        val items = remember { listOf("This is", "---", "A menu", "---", "Item 3") }
        var selected by remember { mutableStateOf(items.first()) }

        Row(Modifier.height(150.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedSplitButton(
                onClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button clicked") },
                secondaryOnClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button chevron clicked") },
                content = { Text("Split button") },
                menuContent = {
                    items.forEach {
                        if (it == "---") {
                            separator()
                        } else {
                            selectableItem(
                                selected = selected == it,
                                onClick = {
                                    selected = it
                                    JewelLogger.getInstance("Jewel").warn("Item clicked: $it")
                                },
                            ) {
                                Text(it)
                            }
                        }
                    }
                },
            )
            OutlinedSplitButton(
                onClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button clicked") },
                secondaryOnClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button chevron clicked") },
                content = { Text("Split button") },
                popupContainer = {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Generic popup content")
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                key = AllIconsKeys.Nodes.ConfigFolder,
                                contentDescription = "taskGroup",
                                hint = Badge(Color.Red, DotBadgeShape.Default),
                            )
                        }
                    }
                },
            )
            OutlinedSplitButton(
                enabled = false,
                onClick = {},
                secondaryOnClick = {},
                content = { Text("Disabled button") },
                menuContent = {},
            )
            DefaultSplitButton(
                onClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button clicked") },
                secondaryOnClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button chevron clicked") },
                content = { Text("Split button") },
                menuContent = {
                    items(
                        items = listOf("Item 1", "Item 2", "Item 3"),
                        isSelected = { false },
                        onItemClick = { JewelLogger.getInstance("Jewel").warn("Item clicked: $it") },
                        content = { Text(it) },
                    )
                },
            )
            DefaultSplitButton(
                enabled = false,
                onClick = {},
                secondaryOnClick = {},
                content = { Text("Disabled button") },
                menuContent = {},
            )
        }
    }
}

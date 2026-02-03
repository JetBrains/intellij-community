@file:Suppress("UnusedImports") // Detekt false positive on the InfoText import

package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.DefaultSplitButton
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.InfoText
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.OutlinedSplitButton
import org.jetbrains.jewel.ui.component.SelectableIconActionButton
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconActionButton
import org.jetbrains.jewel.ui.component.ToggleableIconButton
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.items
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.badge.DotBadgeShape
import org.jetbrains.jewel.ui.painter.hints.Badge
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.hints.Stroke
import org.jetbrains.jewel.ui.theme.outlinedSplitButtonStyle
import org.jetbrains.jewel.ui.theme.transparentIconButtonStyle

@Composable
public fun Buttons(modifier: Modifier = Modifier) {
    VerticallyScrollableContainer(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            NormalButtons()

            SplitButtons()

            var selectedIndex by remember { mutableIntStateOf(0) }
            IconButtons(selectedIndex == 1) { selectedIndex = 1 }
            IconActionButtons(selectedIndex == 2) { selectedIndex = 2 }

            ActionButtons()
        }
    }
}

@Composable
private fun NormalButtons() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("Ordinary buttons")

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = {}) { SingleLineText("Outlined") }

            OutlinedButton(onClick = {}, enabled = false) { SingleLineText("Outlined Disabled") }

            DefaultButton(onClick = {}) { SingleLineText("Default") }

            DefaultButton(onClick = {}, enabled = false) { SingleLineText("Default disabled") }
        }
    }
}

@Composable
private fun IconButtons(selected: Boolean, onSelectableClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("IconButton")

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ComponentWithLabel("Focusable:") {
                IconButton(onClick = {}) { Icon(key = AllIconsKeys.Actions.AddFile, contentDescription = "IconButton") }
            }

            ComponentWithLabel("Not focusable:") {
                IconButton(onClick = {}, focusable = false) {
                    Icon(key = AllIconsKeys.Actions.AddFile, contentDescription = "IconButton")
                }
            }

            ComponentWithLabel("Selectable:") {
                SelectableIconButton(onClick = onSelectableClick, selected = selected) { state ->
                    val tint by LocalIconButtonStyle.current.colors.selectableForegroundFor(state)
                    Icon(
                        key = AllIconsKeys.Actions.MatchCase,
                        contentDescription = "SelectableIconButton",
                        hints = arrayOf(Selected(selected), Stroke(tint)),
                    )
                }
            }

            ComponentWithLabel("Toggleable:") {
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

            ComponentWithLabel("Without background:") {
                IconButton(style = JewelTheme.transparentIconButtonStyle, onClick = {}) {
                    Icon(key = AllIconsKeys.Actions.AddFile, contentDescription = "IconButton")
                }
            }

            ComponentWithLabel("Disabled:") {
                IconButton(onClick = {}, enabled = false) {
                    Icon(key = AllIconsKeys.Actions.AddFile, contentDescription = "IconButton")
                }
            }
        }
    }
}

@Composable
private fun IconActionButtons(selected: Boolean, onSelectableClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("IconActionButton")

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ComponentWithLabel("With tooltip:") {
                IconActionButton(
                    key = AllIconsKeys.Actions.BuildAutoReloadChanges,
                    contentDescription = "IconActionButton",
                    onClick = {},
                ) {
                    SingleLineText("I am a tooltip")
                }
            }

            ComponentWithLabel("Without tooltip:") {
                IconActionButton(
                    key = AllIconsKeys.Actions.BuildAutoReloadChanges,
                    contentDescription = "IconActionButton",
                    onClick = {},
                )
            }

            ComponentWithLabel("Selectable:") {
                SelectableIconActionButton(
                    key = AllIconsKeys.Actions.BuildAutoReloadChanges,
                    contentDescription = "SelectableIconActionButton",
                    selected = selected,
                    onClick = onSelectableClick,
                )
            }

            ComponentWithLabel("Toggleable:") {
                var checked by remember { mutableStateOf(false) }
                ToggleableIconActionButton(
                    key = AllIconsKeys.Actions.BuildAutoReloadChanges,
                    contentDescription = "SelectableIconActionButton",
                    value = checked,
                    onValueChange = { checked = it },
                )
            }

            ComponentWithLabel("Disabled:") {
                IconActionButton(
                    key = AllIconsKeys.Actions.BuildAutoReloadChanges,
                    contentDescription = "IconActionButton",
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun ActionButtons() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("ActionButton")

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ComponentWithLabel("With tooltip:") {
                ActionButton(onClick = {}, tooltip = { Text("I am a tooltip") }) { SingleLineText("Hover me!") }
            }

            ComponentWithLabel("Without tooltip:") { ActionButton(onClick = {}) { SingleLineText("Do something") } }
        }
    }
}

@Composable
private fun ComponentWithLabel(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoText(label)
        content()
    }
}

@Composable
private fun SplitButtons() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("SplitButton")

        val items = remember { listOf("This is", "---", "A menu", "---", "Item 3") }
        var selected by remember { mutableStateOf(items.first()) }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedSplitButton(
                onClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button clicked") },
                secondaryOnClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button chevron clicked") },
                content = { SingleLineText("Split button") },
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
                content = { SingleLineText("Split button") },
                popupContainer = {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Generic popup content with a bigger text that will check if the popup can handle it properly."
                        )
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
                content = { SingleLineText("Disabled outline split button") },
                menuContent = { blankNotice() },
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
                content = { SingleLineText("Disabled default split button") },
                menuContent = { blankNotice() },
            )

            DefaultSplitButton(
                onClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button clicked") },
                secondaryOnClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button chevron clicked") },
                content = { SingleLineText("Sub menus") },
                menuContent = {
                    fun MenuScope.buildSubmenus(stack: List<Int>) {
                        val stackStr = stack.joinToString(".").let { if (stack.isEmpty()) it else "$it." }

                        repeat(5) {
                            val number = it + 1
                            val itemStr = "$stackStr$number"

                            if (stack.size == 4) {
                                selectableItem(
                                    selected = selected == itemStr,
                                    onClick = {
                                        selected = itemStr
                                        JewelLogger.getInstance("Jewel").warn("Item clicked: $itemStr")
                                    },
                                ) {
                                    Text("Item $itemStr")
                                }
                            } else {
                                submenu(
                                    submenu = { buildSubmenus(stack + number) },
                                    content = { Text("Submenu $itemStr") },
                                )
                            }
                        }

                        separator()

                        repeat(10) {
                            val number = it + 1
                            val itemStr = "${stackStr}other.$number"

                            selectableItem(
                                selected = selected == itemStr,
                                onClick = {
                                    selected = itemStr
                                    JewelLogger.getInstance("Jewel").warn("Item clicked: $itemStr")
                                },
                            ) {
                                Text("Other Item With More Chars - ${it + 1}")
                            }
                        }
                    }

                    buildSubmenus(emptyList())
                },
            )

            Tooltip(
                tooltip = {
                    Text("This button is intentionally too narrow, to check that it works when space-constrained.")
                },
                modifier = Modifier.width(120.dp),
            ) {
                OutlinedSplitButton(
                    onClick = {},
                    secondaryOnClick = {},
                    content = { SingleLineText("Long text that gets ellipsized") },
                    menuContent = { blankNotice() },
                )
            }

            OutlinedSplitButton(
                onClick = {},
                secondaryOnClick = {},
                content = { SingleLineText("Taller button!") },
                menuContent = { blankNotice() },
                modifier = Modifier.height(JewelTheme.outlinedSplitButtonStyle.button.metrics.minSize.height * 1.25f),
            )
        }
    }
}

private fun MenuScope.blankNotice() {
    passiveItem { InfoText("Intentionally left blank", Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
}

@Composable
private fun SingleLineText(text: String) {
    Text(text, overflow = TextOverflow.Ellipsis, maxLines = 1)
}

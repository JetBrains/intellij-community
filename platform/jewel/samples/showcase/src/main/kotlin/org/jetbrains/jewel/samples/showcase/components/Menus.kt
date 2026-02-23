// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

@Composable
public fun Menus(modifier: Modifier = Modifier) {
    VerticallyScrollableContainer(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BasicMenus()
            MenusWithIcons()
            MenusWithSubmenus()
            MenusWithAdContent()
        }
    }
}

@Composable
private fun BasicMenus() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("Basic menus")

        var showMenu by remember { mutableStateOf(false) }
        var selectedItem by remember { mutableStateOf("Item 1") }

        Box {
            DefaultButton(onClick = { showMenu = true }) { Text("Show Menu") }

            if (showMenu) {
                PopupMenu(
                    onDismissRequest = {
                        showMenu = false
                        true
                    },
                    horizontalAlignment = Alignment.Start,
                ) {
                    selectableItem(selected = selectedItem == "Item 1", onClick = { selectedItem = "Item 1" }) {
                        Text("Item 1")
                    }
                    selectableItem(selected = selectedItem == "Item 2", onClick = { selectedItem = "Item 2" }) {
                        Text("Item 2")
                    }
                    separator()
                    selectableItem(selected = selectedItem == "Item 3", onClick = { selectedItem = "Item 3" }) {
                        Text("Item 3")
                    }
                }
            }
        }

        Text("Selected: $selectedItem", color = JewelTheme.contentColor)
    }
}

@Composable
private fun MenusWithIcons() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("Menus with icons")

        var showMenu by remember { mutableStateOf(false) }
        var selectedAction by remember { mutableStateOf("Copy") }

        Box {
            DefaultButton(onClick = { showMenu = true }) { Text("Show Menu with Icons") }

            if (showMenu) {
                PopupMenu(
                    onDismissRequest = {
                        showMenu = false
                        true
                    },
                    horizontalAlignment = Alignment.Start,
                ) {
                    selectableItem(
                        selected = selectedAction == "Copy",
                        onClick = { selectedAction = "Copy" },
                        iconKey = AllIconsKeys.Actions.Copy,
                        keybinding = setOf("Ctrl", "C"),
                    ) {
                        Text("Copy")
                    }
                    selectableItem(
                        selected = selectedAction == "Paste",
                        onClick = { selectedAction = "Paste" },
                        iconKey = AllIconsKeys.Actions.MenuPaste,
                        keybinding = setOf("Ctrl", "V"),
                    ) {
                        Text("Paste")
                    }
                    separator()
                    selectableItem(
                        selected = selectedAction == "Delete",
                        onClick = { selectedAction = "Delete" },
                        iconKey = AllIconsKeys.Actions.GC,
                        keybinding = setOf("Delete"),
                    ) {
                        Text("Delete")
                    }
                }
            }
        }

        Text("Last action: $selectedAction", color = JewelTheme.contentColor)
    }
}

@Composable
private fun MenusWithSubmenus() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("Menus with submenus")

        var showMenu by remember { mutableStateOf(false) }
        var selectedItem by remember { mutableStateOf("None") }

        Box {
            DefaultButton(onClick = { showMenu = true }) { Text("Show Menu with Submenus") }

            if (showMenu) {
                PopupMenu(
                    onDismissRequest = {
                        showMenu = false
                        true
                    },
                    horizontalAlignment = Alignment.Start,
                ) {
                    selectableItem(
                        selected = selectedItem == "Direct Item",
                        onClick = { selectedItem = "Direct Item" },
                    ) {
                        Text("Direct Item")
                    }
                    separator()
                    submenu(
                        iconKey = AllIconsKeys.General.ChevronRight,
                        submenu = {
                            selectableItem(
                                selected = selectedItem == "Submenu Item 1",
                                onClick = { selectedItem = "Submenu Item 1" },
                            ) {
                                Text("Submenu Item 1")
                            }
                            selectableItem(
                                selected = selectedItem == "Submenu Item 2",
                                onClick = { selectedItem = "Submenu Item 2" },
                            ) {
                                Text("Submenu Item 2")
                            }
                            separator()
                            submenu(
                                submenu = {
                                    selectableItem(
                                        selected = selectedItem == "Nested Item 1",
                                        onClick = { selectedItem = "Nested Item 1" },
                                    ) {
                                        Text("Nested Item 1")
                                    }
                                    selectableItem(
                                        selected = selectedItem == "Nested Item 2",
                                        onClick = { selectedItem = "Nested Item 2" },
                                    ) {
                                        Text("Nested Item 2")
                                    }
                                },
                                content = { Text("Nested Submenu") },
                            )
                        },
                        content = { Text("More Options") },
                    )
                }
            }
        }

        Text("Selected: $selectedItem", color = JewelTheme.contentColor)
    }
}

@Composable
private fun MenusWithAdContent() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("Menus with ad content")

        var showMenu by remember { mutableStateOf(false) }
        var selectedMode by remember { mutableStateOf("Standard") }

        Box {
            DefaultButton(onClick = { showMenu = true }) { Text("Show Menu with Ad") }

            if (showMenu) {
                PopupMenu(
                    onDismissRequest = {
                        showMenu = false
                        true
                    },
                    horizontalAlignment = Alignment.Start,
                    adContent = {
                        Text(
                            text = "Tip: Use Ctrl+Space to quickly change modes",
                            style = JewelTheme.typography.small,
                            color = JewelTheme.globalColors.text.info,
                        )
                    },
                ) {
                    selectableItem(selected = selectedMode == "Beginner", onClick = { selectedMode = "Beginner" }) {
                        Text("Beginner Mode")
                    }
                    selectableItem(selected = selectedMode == "Standard", onClick = { selectedMode = "Standard" }) {
                        Text("Standard Mode")
                    }
                    selectableItem(selected = selectedMode == "Advanced", onClick = { selectedMode = "Advanced" }) {
                        Text("Advanced Mode")
                    }
                    selectableItem(selected = selectedMode == "Expert", onClick = { selectedMode = "Expert" }) {
                        Text("Expert Mode")
                    }
                    selectableItem(selected = selectedMode == "Pro", onClick = { selectedMode = "Pro" }) {
                        Text("Pro Mode")
                    }
                    selectableItem(selected = selectedMode == "Admin", onClick = { selectedMode = "Admin" }) {
                        Text("Admin Mode")
                    }
                }
            }
        }

        Text("Current mode: $selectedMode", color = JewelTheme.contentColor)
    }
}

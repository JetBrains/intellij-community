package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun Dropdowns() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val items = remember { listOf("Light", "Dark", "---", "High Contrast", "Darcula", "IntelliJ Light") }
        var selected by remember { mutableStateOf(items.first()) }

        Dropdown(enabled = false, menuContent = {}) { Text("Disabled") }
        Dropdown(
            menuContent = {
                items.forEach {
                    if (it == "---") {
                        separator()
                    } else {
                        selectableItem(selected = selected == it, onClick = { selected = it }) { Text(it) }
                    }
                }
                separator()
                submenu(
                    submenu = {
                        items.forEach {
                            if (it == "---") {
                                separator()
                            } else {
                                selectableItem(selected = selected == it, onClick = { selected = it }) { Text(it) }
                            }
                        }
                        separator()
                        submenu(
                            submenu = {
                                items.forEach {
                                    if (it == "---") {
                                        separator()
                                    } else {
                                        selectableItem(selected = selected == it, onClick = { selected = it }) {
                                            Text(it)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Submenu2")
                        }
                    }
                ) {
                    Text("Submenu")
                }
            }
        ) {
            Text(selected)
        }
        Dropdown(
            outline = Outline.Error,
            menuContent = {
                items.forEach {
                    if (it == "---") {
                        separator()
                    } else {
                        selectableItem(selected = selected == it, onClick = { selected = it }) { Text(it) }
                    }
                }
            },
        ) {
            Text(selected)
        }
        Dropdown(
            menuContent = {
                items.forEach {
                    if (it == "---") {
                        separator()
                    } else {
                        selectableItem(
                            iconKey = dropdownIconsSample.random(),
                            keybinding =
                                if (Random.nextBoolean()) {
                                    null
                                } else {
                                    dropdownKeybindingsSample.shuffled().take(2).toSet()
                                },
                            selected = false,
                            onClick = {},
                        ) {
                            Text(it)
                        }
                    }
                }
                submenu(
                    submenu = {
                        items.forEach {
                            if (it == "---") {
                                separator()
                            } else {
                                selectableItem(
                                    iconKey = dropdownIconsSample.random(),
                                    keybinding =
                                        if (Random.nextBoolean()) {
                                            null
                                        } else {
                                            dropdownKeybindingsSample.shuffled().take(2).toSet()
                                        },
                                    selected = false,
                                    onClick = {},
                                ) {
                                    Text(it)
                                }
                            }
                        }
                        separator()
                        submenu(
                            submenu = {
                                items.forEach {
                                    if (it == "---") {
                                        separator()
                                    } else {
                                        selectableItem(
                                            iconKey = dropdownIconsSample.random(),
                                            selected = false,
                                            onClick = {},
                                        ) {
                                            Text(it)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Submenu2")
                        }
                    }
                ) {
                    Text("Submenu")
                }
            }
        ) {
            Text("With icons")
        }
    }
}

private val dropdownIconsSample = listOf(AllIconsKeys.Actions.Find, AllIconsKeys.Actions.Close, null)
private val dropdownKeybindingsSample = setOf("A", "B", "↑", "→", "␡")

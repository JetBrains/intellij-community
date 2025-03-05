// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.EditableListComboBox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
public fun Dropdowns() {
    Column {
        OldDropdowns()

        Spacer(modifier = Modifier.height(24.dp))

        ComboBoxes()
    }
}

@Composable
private fun OldDropdowns() {
    GroupHeader("Dropdowns (deprecated)")

    Spacer(modifier = Modifier.height(8.dp))

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
private val dropdownKeybindingsSample = setOf("A", "B", "?", "?", "?")

@Composable
private fun ComboBoxes() {
    GroupHeader("List combobox")
    ListComboBoxes()

    Spacer(modifier = Modifier.height(16.dp))

    GroupHeader("Simple combobox")
    SimpleComboBoxes()
}

@Composable
private fun ListComboBoxes() {
    val comboBoxItems = remember {
        listOf(
            "Cat",
            "Elephant",
            "Sun",
            "Book",
            "Laughter",
            "Whisper",
            "Ocean",
            "Serendipity lorem ipsum",
            "Umbrella",
            "Joy",
        )
    }

    var selectedComboBox1: String? by remember { mutableStateOf(comboBoxItems.first()) }
    var selectedComboBox2: String? by remember { mutableStateOf(comboBoxItems.first()) }
    var selectedComboBox3: String? by remember { mutableStateOf(comboBoxItems.first()) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enabled and Editable")

            Text(text = "Selected item: $selectedComboBox1", maxLines = 1, overflow = TextOverflow.Ellipsis)

            EditableListComboBox(
                items = comboBoxItems,
                modifier = Modifier.width(200.dp),
                maxPopupHeight = 150.dp,
                onSelectedItemChange = { _, text -> selectedComboBox1 = text },
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        text = item,
                        isSelected = isSelected,
                        isActive = isActive,
                        iconContentDescription = item,
                    )
                },
            )
        }

        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enabled")

            Text(text = "Selected item: $selectedComboBox2", maxLines = 1, overflow = TextOverflow.Ellipsis)

            ListComboBox(
                items = comboBoxItems,
                modifier = Modifier.width(200.dp),
                maxPopupHeight = 150.dp,
                onSelectedItemChange = { _, text -> selectedComboBox2 = text },
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        text = item,
                        isSelected = isSelected,
                        isActive = isActive,
                        iconContentDescription = item,
                    )
                },
            )
        }

        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Disabled")

            Text(text = "Selected item: $selectedComboBox3", maxLines = 1, overflow = TextOverflow.Ellipsis)

            ListComboBox(
                items = comboBoxItems,
                modifier = Modifier.width(200.dp),
                isEnabled = false,
                onSelectedItemChange = { _, text -> selectedComboBox3 = text },
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        text = item,
                        isSelected = isSelected,
                        isActive = isActive,
                        iconContentDescription = item,
                    )
                },
            )
        }
    }
}

@Composable
private fun SimpleComboBoxes() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enabled and Editable")

            val popupManager = remember { PopupManager() }
            val state = rememberTextFieldState()
            EditableComboBox(
                textFieldState = state,
                modifier = Modifier.width(200.dp),
                popupManager = popupManager,
                popupContent = {
                    PopupContainer(
                        onDismissRequest = { popupManager.setPopupVisible(false) },
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text("Your custom popup here!", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                },
            )
        }
    }
}

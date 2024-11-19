package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.ListItemState
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.simpleListItemStyle

@Composable
fun Dropdowns() {
    Column {
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "ComboBoxes", style = Typography.h1TextStyle())
        Text(text = "Selected item: $selectedComboBox1")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text("Enabled and Editable")
                Text(text = "Selected item: $selectedComboBox1")
                ListComboBox(
                    items = comboBoxItems,
                    modifier = Modifier.width(200.dp),
                    maxPopupHeight = 150.dp,
                    onSelectedItemChange = { selectedComboBox1 = it },
                    listItemContent = { item, isSelected, _, isItemHovered, isPreviewSelection ->
                        SimpleListItem(
                            text = item,
                            state = ListItemState(isSelected, isItemHovered, isPreviewSelection),
                            modifier = Modifier,
                            style = JewelTheme.simpleListItemStyle,
                            contentDescription = item,
                        )
                    },
                )
            }

            Column {
                Text("Enabled")
                Text(text = "Selected item: $selectedComboBox2")

                ListComboBox(
                    items = comboBoxItems,
                    modifier = Modifier.width(200.dp),
                    isEditable = false,
                    maxPopupHeight = 150.dp,
                    onSelectedItemChange = { selectedComboBox2 = it },
                    listItemContent = { item, isSelected, isFocused, isItemHovered, isPreviewSelection ->
                        SimpleListItem(
                            text = item,
                            state = ListItemState(isSelected, isItemHovered, isPreviewSelection),
                            style = JewelTheme.simpleListItemStyle,
                            contentDescription = item,
                        )
                    },
                )
            }
            Column {
                Text("Disabled")
                Text(text = "Selected item: $selectedComboBox3")
                ListComboBox(
                    items = comboBoxItems,
                    modifier = Modifier.width(200.dp),
                    isEditable = false,
                    isEnabled = false,
                    onSelectedItemChange = { selectedComboBox3 = it },
                    listItemContent = { item, isSelected, _, isItemHovered, isPreviewSelection ->
                        SimpleListItem(
                            text = item,
                            state = ListItemState(isSelected, isItemHovered, isPreviewSelection),
                            style = JewelTheme.simpleListItemStyle,
                            contentDescription = item,
                        )
                    },
                )
            }
        }
    }
}

private val dropdownIconsSample = listOf(AllIconsKeys.Actions.Find, AllIconsKeys.Actions.Close, null)
private val dropdownKeybindingsSample = setOf("A", "B", "?", "?", "?")

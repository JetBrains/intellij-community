// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.showcase.ShowcaseIcons
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.EditableListComboBox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.search.SpeedSearchableComboBox
import org.jetbrains.jewel.ui.disabledAppearance
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val stringItems =
    listOf(
        "Cat",
        "Elephant",
        "Sun",
        "Book",
        "Laughter",
        "Whisper",
        "Ocean",
        "Serendipity lorem ipsum dolor sit amet",
        "Umbrella",
        "Joy",
        "Mountain",
        "Harmony",
        "Starlight",
        "Meadow",
        "Thunder",
        "River",
        "Galaxy",
        "Breeze",
        "Twilight",
        "Aurora",
    )

private val languageOptions =
    listOf(
        ProgrammingLanguage("Kotlin", ShowcaseIcons.ProgrammingLanguages.Kotlin),
        ProgrammingLanguage("Java", AllIconsKeys.FileTypes.Java),
        ProgrammingLanguage("Python", AllIconsKeys.Language.Python),
        ProgrammingLanguage("JavaScript", AllIconsKeys.FileTypes.JavaScript),
        ProgrammingLanguage("Java", AllIconsKeys.FileTypes.Java),
        ProgrammingLanguage("Rust", AllIconsKeys.Language.Rust),
        ProgrammingLanguage("Go", AllIconsKeys.Language.GO),
        ProgrammingLanguage("Ruby", AllIconsKeys.Language.Ruby),
    )

@Composable
public fun ComboBoxes(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GroupHeader("List combo box (aka dropdown)")
        ListComboBoxes()

        GroupHeader("Editable list combo box")
        EditableListComboBoxes()

        GroupHeader("Custom combo box content")
        CustomComboBoxes()

        GroupHeader("Dynamic content")
        DynamicListComboBox()

        Spacer(Modifier.height(16.dp).fillMaxWidth())
    }
}

@Composable
private fun ListComboBoxes() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("String-based API")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            ListComboBox(
                items = stringItems,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { index -> selectedIndex = index },
                modifier = Modifier.widthIn(max = 200.dp),
                itemKeys = { _, item -> item },
            )
        }

        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Generics-based API")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) languageOptions[selectedIndex].name else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            ListComboBox(
                items = languageOptions,
                selectedIndex = selectedIndex,
                modifier = Modifier.widthIn(max = 200.dp),
                onSelectedItemChange = { index -> selectedIndex = index },
                itemKeys = { index, _ -> index },
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        text = item.name,
                        selected = isSelected,
                        active = isActive,
                        iconContentDescription = item.name,
                        icon = item.icon,
                        colorFilter = null,
                    )
                },
            )
        }

        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Speed Search API")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            SpeedSearchArea(Modifier.widthIn(max = 200.dp)) {
                SpeedSearchableComboBox(
                    items = stringItems,
                    selectedIndex = selectedIndex,
                    onSelectedItemChange = { index -> selectedIndex = index },
                )
            }
        }

        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Disabled")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) languageOptions[selectedIndex].name else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            ListComboBox(
                items = languageOptions,
                selectedIndex = selectedIndex,
                modifier = Modifier.widthIn(max = 200.dp),
                enabled = false,
                onSelectedItemChange = { index -> selectedIndex = index },
                itemKeys = { index, _ -> index },
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        modifier = Modifier.disabledAppearance(),
                        text = item.name,
                        selected = isSelected,
                        active = isActive,
                        iconContentDescription = item.name,
                        icon = item.icon,
                    )
                },
            )
        }
    }
}

@Composable
private fun EditableListComboBoxes() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("String-based API, enabled")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            EditableListComboBox(
                items = stringItems,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { index -> selectedIndex = index },
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Large Popup, enabled")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            EditableListComboBox(
                items = stringItems,
                selectedIndex = selectedIndex,
                maxPopupWidth = 275.dp,
                onSelectedItemChange = { index -> selectedIndex = index },
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }

        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("String-based API, disabled")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            EditableListComboBox(
                items = stringItems,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { index -> selectedIndex = index },
                modifier = Modifier.widthIn(max = 200.dp),
                enabled = false,
            )
        }
    }
}

@Composable
private fun CustomComboBoxes() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text-label ComboBox")

            val popupManager = remember { PopupManager() }
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"

            ComboBox(
                labelText = selectedItemText,
                modifier = Modifier.widthIn(max = 200.dp),
                popupManager = popupManager,
                popupContent = {
                    CustomPopupContent {
                        selectedIndex = stringItems.indices.random()
                        popupManager.setPopupVisible(false)
                    }
                },
            )
        }

        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom-label ComboBox")

            val popupManager = remember { PopupManager() }
            var selectedIndex by remember { mutableIntStateOf(2) }

            ComboBox(
                modifier = Modifier.widthIn(max = 200.dp),
                popupManager = popupManager,
                popupContent = {
                    CustomPopupContent {
                        selectedIndex = languageOptions.indices.random()
                        popupManager.setPopupVisible(false)
                    }
                },
                labelContent = {
                    SimpleListItem(
                        text = languageOptions[selectedIndex].name,
                        icon = languageOptions[selectedIndex].icon,
                        selected = false,
                        active = true,
                    )
                },
            )
        }

        Column(Modifier.weight(1f).widthIn(min = 125.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("EditableComboBox")

            val popupManager = remember { PopupManager() }
            val state = rememberTextFieldState(stringItems[2])

            EditableComboBox(
                textFieldState = state,
                modifier = Modifier.widthIn(max = 200.dp),
                popupManager = popupManager,
                popupContent = {
                    CustomPopupContent {
                        val newItemIndex = stringItems.indices.random()
                        state.edit { replace(0, originalText.length, stringItems[newItemIndex]) }
                        popupManager.setPopupVisible(false)
                    }
                },
            )
        }
    }
}

@Composable
private fun CustomPopupContent(onButtonClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Your custom content here! Generic popup content with a bigger text that will check if the popup can handle it properly.",
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        DefaultButton(onClick = onButtonClick) { Text("Pick a random item") }
    }
}

@Composable
private fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = JewelTheme.globalColors.text.info,
        modifier = modifier,
    )
}

private data class ProgrammingLanguage(val name: String, val icon: IconKey)

@Composable
private fun DynamicListComboBox() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val itemsState = remember { androidx.compose.runtime.mutableStateOf(listOf("A", "B", "C")) }
        var selectedIndex by remember { mutableIntStateOf(0) }
        var reportCount by remember { mutableIntStateOf(0) }
        var lastReportedIndex by remember { mutableIntStateOf(-1) }
        val listState: SelectableLazyListState = rememberSelectableLazyListState()

        val itemsJoined = itemsState.value.joinToString(prefix = "[", postfix = "]")
        val statusPrefix = "Items: $itemsJoined selectedIndex: $selectedIndex; "
        val statusSuffix = "onSelectedItemChange count=$reportCount, last=$lastReportedIndex"

        InfoText(text = statusPrefix + statusSuffix)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ListComboBox(
                items = itemsState.value,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { idx ->
                    lastReportedIndex = idx
                    reportCount += 1
                    selectedIndex = idx
                },
                modifier = Modifier.widthIn(min = 100.dp, max = 200.dp),
                itemKeys = { _, item -> item }, // stable keys by item value
                listState = listState,
            )
            DefaultButton(
                onClick = {
                    itemsState.value = itemsState.value.filterNot { it == "B" }
                    selectedIndex = -1
                    listState.selectedKeys = emptySet()
                }
            ) {
                Text("Delete B, Clear Selection")
            }
            DefaultButton(onClick = { itemsState.value = listOf("A", "B", "C") }) { Text("Add B") }
            DefaultButton(
                onClick = {
                    itemsState.value = emptyList()
                    selectedIndex = -1
                    listState.selectedKeys = emptySet()
                }
            ) {
                Text("Delete All")
            }
            DefaultButton(
                onClick = {
                    itemsState.value = listOf("A", "B", "C")
                    selectedIndex = -1
                    listState.selectedKeys = emptySet()
                }
            ) {
                Text("Add All")
            }
        }
    }
}

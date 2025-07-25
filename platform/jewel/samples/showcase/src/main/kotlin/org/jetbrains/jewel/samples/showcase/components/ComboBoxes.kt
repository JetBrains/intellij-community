// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.showcase.ShowcaseIcons
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.EditableListComboBox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
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
        "Serendipity lorem ipsum",
        "Umbrella",
        "Joy",
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GroupHeader("List combo box (aka dropdown)")
        ListComboBoxes()

        GroupHeader("Editable list combo box")
        EditableListComboBoxes()

        GroupHeader("Custom combo boxes")
        CustomComboBoxes()
    }
}

@Composable
private fun ListComboBoxes() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("String-based API, enabled")
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

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Generics-based API, enabled")
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

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("String-based API, disabled")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"
            InfoText(text = "Selected item: $selectedItemText")

            ListComboBox(
                items = stringItems,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { index -> selectedIndex = index },
                modifier = Modifier.widthIn(max = 200.dp),
                enabled = false,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Generics-based API, disabled")
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("String-based API, non-editable")

            val popupManager = remember { PopupManager() }
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) stringItems[selectedIndex] else "[none]"

            ComboBox(
                labelText = selectedItemText,
                modifier = Modifier.widthIn(max = 200.dp),
                popupManager = popupManager,
                popupContent = {
                    CustomPopupContent(popupManager) {
                        selectedIndex = stringItems.indices.random()
                        popupManager.setPopupVisible(false)
                    }
                },
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Generics-based API, non-editable")

            val popupManager = remember { PopupManager() }
            var selectedIndex by remember { mutableIntStateOf(2) }

            ComboBox(
                modifier = Modifier.widthIn(max = 200.dp),
                popupManager = popupManager,
                popupContent = {
                    CustomPopupContent(popupManager) {
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

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("String-based API, editable")

            val popupManager = remember { PopupManager() }
            val state = rememberTextFieldState(stringItems[2])

            EditableComboBox(
                textFieldState = state,
                modifier = Modifier.widthIn(max = 200.dp),
                popupManager = popupManager,
                popupContent = {
                    CustomPopupContent(popupManager) {
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
private fun CustomPopupContent(popupManager: PopupManager, onButtonClick: () -> Unit) {
    PopupContainer(onDismissRequest = { popupManager.setPopupVisible(false) }, horizontalAlignment = Alignment.Start) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoText("Your custom content here!")
            DefaultButton(onClick = onButtonClick) { Text("Pick a random item") }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = JewelTheme.globalColors.text.info)
}

private data class ProgrammingLanguage(val name: String, val icon: IconKey)

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.EditableListComboBox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text

@Composable
public fun Dropdowns() {
    ComboBoxes()
}

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

    // Create a list with duplicates to demonstrate the itemKeys parameter
    val duplicateItems = remember {
        listOf(
            "Red", // index 0
            "Blue", // index 1
            "Green", // index 2
            "Red", // index 3 - duplicate
            "Yellow", // index 4
            "Blue", // index 5 - duplicate
            "Purple", // index 6
            "Orange", // index 7
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enabled and Editable")

            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) comboBoxItems[selectedIndex] else ""
            Text(text = "Selected item: $selectedItemText", maxLines = 1, overflow = TextOverflow.Ellipsis)

            EditableListComboBox(
                items = comboBoxItems,
                selectedIndex = selectedIndex,
                onItemSelected = { index, text -> selectedIndex = index },
                modifier = Modifier.width(200.dp),
                maxPopupHeight = 150.dp,
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
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) comboBoxItems[selectedIndex] else ""
            Text(text = "Selected item: $selectedItemText", maxLines = 1, overflow = TextOverflow.Ellipsis)

            ListComboBox(
                items = comboBoxItems,
                selectedIndex = selectedIndex,
                modifier = Modifier.width(200.dp),
                maxPopupHeight = 150.dp,
                onItemSelected = { index -> selectedIndex = index },
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        text = item,
                        isSelected = isSelected,
                        isActive = isActive,
                        iconContentDescription = item,
                    )
                },
                itemToLabel = { item -> item },
                itemKeys = { _, item -> item },
            )
        }

        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Disabled")
            var selectedIndex by remember { mutableIntStateOf(2) }
            val selectedItemText = if (selectedIndex >= 0) comboBoxItems[selectedIndex] else ""
            Text(text = "Selected item: $selectedItemText", maxLines = 1, overflow = TextOverflow.Ellipsis)

            ListComboBox(
                isEnabled = false,
                items = comboBoxItems,
                selectedIndex = selectedIndex,
                modifier = Modifier.width(200.dp),
                maxPopupHeight = 150.dp,
                onItemSelected = { index -> selectedIndex = index },
                itemToLabel = { item -> item },
                itemKeys = { _, item -> item },
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

    Spacer(modifier = Modifier.height(16.dp))

    Text("With duplicate values (using itemKeys)")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Using index as key")
            var selectedIndex by remember { mutableIntStateOf(1) }
            val selectedItemText = if (selectedIndex >= 0) duplicateItems[selectedIndex] else ""
            Text(
                text = "Selected item: $selectedItemText (index: $selectedIndex)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            ListComboBox(
                items = duplicateItems,
                selectedIndex = selectedIndex,
                modifier = Modifier.width(200.dp),
                maxPopupHeight = 150.dp,
                onItemSelected = { index -> selectedIndex = index },
                itemKeys = { index, _ -> index },
                itemToLabel = { item -> item },
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
            Text("Using fancy key")
            var selectedIndex by remember { mutableIntStateOf(3) }
            val selectedItemText = if (selectedIndex >= 0) duplicateItems[selectedIndex] else ""
            Text(
                text = "Selected item: $selectedItemText (index: $selectedIndex)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            EditableListComboBox(
                items = duplicateItems,
                selectedIndex = selectedIndex,
                modifier = Modifier.width(200.dp),
                maxPopupHeight = 150.dp,
                onItemSelected = { index, text -> selectedIndex = index },
                // Create a fancy key with both index and item value
                itemKeys = { index, item -> "$item-$index" },
                itemContent = { item, isSelected, isActive ->
                    SimpleListItem(
                        text = "$item (index: ${duplicateItems.indexOf(item)})",
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

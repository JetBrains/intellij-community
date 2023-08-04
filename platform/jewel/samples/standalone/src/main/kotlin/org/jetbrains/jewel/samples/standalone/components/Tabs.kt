@file:Suppress("MagicNumber")

package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.TabData
import org.jetbrains.jewel.TabStrip
import org.jetbrains.jewel.Text

@Composable
fun Tabs() {
    GroupHeader("Tabs")
    Text("Default tabs", Modifier.fillMaxWidth())
    DefaultTabShowcase()

    Spacer(Modifier.height(16.dp))
    Text("Editor tabs", Modifier.fillMaxWidth())
    EditorTabShowcase()
}

@Composable
private fun DefaultTabShowcase() {
    var selectedTabIndex by remember { mutableStateOf(0) }

    var tabIds by remember { mutableStateOf((1..12).toList()) }
    val maxId by derivedStateOf { tabIds.maxOrNull() ?: 0 }

    val tabs by derivedStateOf {
        tabIds.mapIndexed { index, id ->
            TabData.Default(
                selected = index == selectedTabIndex,
                label = "Default tab $id",
                onClose = {
                    tabIds = tabIds.toMutableList().apply { removeAt(index) }
                    if (selectedTabIndex >= index) {
                        selectedTabIndex = (selectedTabIndex - 1).coerceIn(tabIds.indices)
                    }
                },
                onClick = { selectedTabIndex = index }
            )
        }
    }

    TabStripWithAddButton(tabs = tabs) {
        val insertionIndex = (selectedTabIndex + 1).coerceIn(0..tabIds.size)
        val nextTabId = maxId + 1

        tabIds = tabIds.toMutableList()
            .apply { add(insertionIndex, nextTabId) }
        selectedTabIndex = insertionIndex
    }
}

@Composable
private fun EditorTabShowcase() {
    var selectedTabIndex by remember { mutableStateOf(0) }

    var tabIds by remember { mutableStateOf((1..12).toList()) }
    val maxId by derivedStateOf { tabIds.maxOrNull() ?: 0 }

    val tabs by derivedStateOf {
        tabIds.mapIndexed { index, id ->
            TabData.Editor(
                selected = index == selectedTabIndex,
                label = "Editor tab $id",
                onClose = {
                    tabIds = tabIds.toMutableList().apply { removeAt(index) }
                    if (selectedTabIndex >= index) {
                        selectedTabIndex = (selectedTabIndex - 1).coerceIn(tabIds.indices)
                    }
                },
                onClick = { selectedTabIndex = index }
            )
        }
    }

    TabStripWithAddButton(tabs = tabs) {
        val insertionIndex = (selectedTabIndex + 1).coerceIn(0..tabIds.size)
        val nextTabId = maxId + 1

        tabIds = tabIds.toMutableList()
            .apply { add(insertionIndex, nextTabId) }
        selectedTabIndex = insertionIndex
    }
}

@Composable
private fun TabStripWithAddButton(
    tabs: List<TabData>,
    onAddClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabStrip(tabs, modifier = Modifier.weight(1f))

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(IntelliJTheme.defaultTabStyle.metrics.tabHeight)
        ) {
            Icon("icons/intui/add.svg", contentDescription = "Add a tab")
        }
    }
}

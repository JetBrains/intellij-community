// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
@file:Suppress("MagicNumber")

package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.theme.editorTabStyle

@Composable
public fun Tabs(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Default tabs", Modifier.fillMaxWidth())
        DefaultTabShowcase()

        Spacer(Modifier.height(16.dp))
        Text("Editor tabs", Modifier.fillMaxWidth())
        EditorTabShowcase()
    }
}

@Composable
private fun DefaultTabShowcase() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var tabIds by remember { mutableStateOf((1..12).toList()) }
    val maxId = remember(tabIds) { tabIds.maxOrNull() ?: 0 }

    val tabs =
        remember(tabIds, selectedTabIndex) {
            tabIds.mapIndexed { index, id ->
                TabData.Default(
                    selected = index == selectedTabIndex,
                    content = { tabState ->
                        val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                        val icon by iconProvider.getPainter(Stateful(tabState))
                        SimpleTabContent(label = "Default Tab $id", state = tabState, icon = icon)
                    },
                    onClose = {
                        tabIds = tabIds.toMutableList().apply { removeAt(index) }
                        if (selectedTabIndex >= index) {
                            val maxPossibleIndex = max(0, tabIds.lastIndex)
                            selectedTabIndex = (selectedTabIndex - 1).coerceIn(0..maxPossibleIndex)
                        }
                    },
                    onClick = { selectedTabIndex = index },
                )
            }
        }

    TabStripWithAddButton(tabs = tabs, style = JewelTheme.defaultTabStyle) {
        val insertionIndex = (selectedTabIndex + 1).coerceIn(0..tabIds.size)
        val nextTabId = maxId + 1

        tabIds = tabIds.toMutableList().apply { add(insertionIndex, nextTabId) }
        selectedTabIndex = insertionIndex
    }
}

@Composable
private fun EditorTabShowcase() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var tabIds by remember { mutableStateOf((1..12).toList()) }
    val maxId = remember(tabIds) { tabIds.maxOrNull() ?: 0 }

    val tabs =
        remember(tabIds, selectedTabIndex) {
            tabIds.mapIndexed { index, id ->
                TabData.Editor(
                    selected = index == selectedTabIndex,
                    content = { tabState ->
                        SimpleTabContent(
                            state = tabState,
                            modifier = Modifier,
                            icon = {
                                Icon(
                                    key = AllIconsKeys.Actions.Find,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).tabContentAlpha(state = tabState),
                                    tint = Color.Magenta,
                                )
                            },
                            label = { Text("Editor tab $id") },
                        )
                        Box(
                            modifier =
                                Modifier.size(12.dp).thenIf(tabState.isHovered) {
                                    drawWithCache {
                                        onDrawBehind {
                                            drawCircle(color = Color.Magenta.copy(alpha = .4f), radius = 6.dp.toPx())
                                        }
                                    }
                                }
                        )
                    },
                    onClose = {
                        tabIds = tabIds.toMutableList().apply { removeAt(index) }
                        if (selectedTabIndex >= index) {
                            val maxPossibleIndex = max(0, tabIds.lastIndex)
                            selectedTabIndex = (selectedTabIndex - 1).coerceIn(0..maxPossibleIndex)
                        }
                    },
                    onClick = { selectedTabIndex = index },
                )
            }
        }

    TabStripWithAddButton(tabs = tabs, style = JewelTheme.editorTabStyle) {
        val insertionIndex = (selectedTabIndex + 1).coerceIn(0..tabIds.size)
        val nextTabId = maxId + 1

        tabIds = tabIds.toMutableList().apply { add(insertionIndex, nextTabId) }
        selectedTabIndex = insertionIndex
    }
}

@Composable
private fun TabStripWithAddButton(tabs: List<TabData>, style: TabStyle, onAddClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabStrip(tabs = tabs, style = style, modifier = Modifier.weight(1f))

        IconButton(onClick = onAddClick, modifier = Modifier.size(JewelTheme.defaultTabStyle.metrics.tabHeight)) {
            Icon(key = AllIconsKeys.General.Add, contentDescription = "Add a tab")
        }
    }
}

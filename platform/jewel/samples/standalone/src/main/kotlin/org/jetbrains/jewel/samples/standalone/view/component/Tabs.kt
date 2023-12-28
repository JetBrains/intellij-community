@file:Suppress("MagicNumber")

package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.util.thenIf
import kotlin.math.max

@Composable
@View(title = "Tabs", position = 7)
fun Tabs() {
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
    val maxId = remember(tabIds) { tabIds.maxOrNull() ?: 0 }

    val tabs = remember(tabIds, selectedTabIndex) {
        tabIds.mapIndexed { index, id ->
            TabData.Default(
                selected = index == selectedTabIndex,
                content = {
                    val iconProvider =
                        rememberResourcePainterProvider("icons/search.svg", StandaloneSampleIcons::class.java)
                    val icon by iconProvider.getPainter()
                    SimpleTabContent(
                        state = it,
                        title = "Default Tab $id",
                        icon = icon,
                    )
                },
                onClose = {
                    tabIds = tabIds.toMutableList().apply { removeAt(index) }
                    if (selectedTabIndex >= index) {
                        val maxPossibleIndex = max(0, tabIds.lastIndex)
                        selectedTabIndex = (selectedTabIndex - 1)
                            .coerceIn(0..maxPossibleIndex)
                    }
                },
                onClick = { selectedTabIndex = index },
            )
        }
    }

    TabStripWithAddButton(tabs) {
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
    val maxId = remember(tabIds) { tabIds.maxOrNull() ?: 0 }

    val tabs = remember(tabIds, selectedTabIndex) {
        tabIds.mapIndexed { index, id ->
            TabData.Editor(
                selected = index == selectedTabIndex,
                content = { tabState ->
                    Row {
                        SimpleTabContent(
                            modifier = Modifier,
                            state = tabState,
                            label = { Text("Editor tab $id") },
                            icon = {
                                Icon(
                                    resource = "icons/search.svg",
                                    contentDescription = "SearchIcon",
                                    iconClass = StandaloneSampleIcons::class.java,
                                    modifier = Modifier.size(16.dp).tabContentAlpha(state = tabState),
                                    tint = Color.Magenta,
                                )
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .thenIf(tabState.isHovered) {
                                drawWithCache {
                                    onDrawBehind {
                                        drawCircle(color = Color.Magenta.copy(alpha = .4f), radius = 6.dp.toPx())
                                    }
                                }
                            },
                    )
                },
                onClose = {
                    tabIds = tabIds.toMutableList().apply { removeAt(index) }
                    if (selectedTabIndex >= index) {
                        val maxPossibleIndex = max(0, tabIds.lastIndex)
                        selectedTabIndex = (selectedTabIndex - 1)
                            .coerceIn(0..maxPossibleIndex)
                    }
                },
                onClick = { selectedTabIndex = index },
            )
        }
    }

    TabStripWithAddButton(tabs) {
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

        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(JewelTheme.defaultTabStyle.metrics.tabHeight),
        ) {
            Icon(
                resource = "expui/general/add.svg",
                contentDescription = "Add a tab",
                StandaloneSampleIcons::class.java,
            )
        }
    }
}

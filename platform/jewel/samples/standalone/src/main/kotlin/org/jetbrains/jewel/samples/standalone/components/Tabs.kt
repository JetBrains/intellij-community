@file:Suppress("MagicNumber")

package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.NoIndication
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.TabData
import org.jetbrains.jewel.TabStrip
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.styling.rememberStatelessPainterProvider
import kotlin.math.max

@Composable
fun Tabs(
    svgLoader: SvgLoader,
    resourceLoader: ResourceLoader,
) {
    GroupHeader("Tabs")
    Text("Default tabs", Modifier.fillMaxWidth())
    DefaultTabShowcase(svgLoader, resourceLoader)

    Spacer(Modifier.height(16.dp))
    Text("Editor tabs", Modifier.fillMaxWidth())
    EditorTabShowcase(svgLoader, resourceLoader)
}

@Composable
private fun DefaultTabShowcase(
    svgLoader: SvgLoader,
    resourceLoader: ResourceLoader,
) {
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
                        val maxPossibleIndex = max(0, tabIds.lastIndex)
                        selectedTabIndex = (selectedTabIndex - 1)
                            .coerceIn(0..maxPossibleIndex)
                    }
                },
                onClick = { selectedTabIndex = index },
            )
        }
    }

    TabStripWithAddButton(tabs, svgLoader, resourceLoader) {
        val insertionIndex = (selectedTabIndex + 1).coerceIn(0..tabIds.size)
        val nextTabId = maxId + 1

        tabIds = tabIds.toMutableList()
            .apply { add(insertionIndex, nextTabId) }
        selectedTabIndex = insertionIndex
    }
}

@Composable
private fun EditorTabShowcase(
    svgLoader: SvgLoader,
    resourceLoader: ResourceLoader,
) {
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
                        val maxPossibleIndex = max(0, tabIds.lastIndex)
                        selectedTabIndex = (selectedTabIndex - 1)
                            .coerceIn(0..maxPossibleIndex)
                    }
                },
                onClick = { selectedTabIndex = index },
            )
        }
    }

    TabStripWithAddButton(tabs, svgLoader, resourceLoader) {
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
    svgLoader: SvgLoader,
    resourceLoader: ResourceLoader,
    onAddClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabStrip(tabs, modifier = Modifier.weight(1f))

        Spacer(Modifier.width(8.dp))

        var isHovered by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> isHovered = true
                    is HoverInteraction.Exit -> isHovered = false
                }
            }
        }

        // TODO create an IconButton instead of this hack
        val backgroundColor = if (isHovered) {
            IntUiTheme.defaultTabStyle.colors.backgroundHovered
        } else {
            IntUiTheme.defaultTabStyle.colors.background
        }

        Box(
            modifier = Modifier.size(IntelliJTheme.defaultTabStyle.metrics.tabHeight)
                .clickable(
                    onClick = onAddClick,
                    onClickLabel = "Add a tab",
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = NoIndication,
                )
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            val addIconProvider = rememberStatelessPainterProvider("expui/general/add.svg", svgLoader)
            val addIcon by addIconProvider.getPainter(resourceLoader)

            Icon(addIcon, contentDescription = "Add a tab")
        }
    }
}

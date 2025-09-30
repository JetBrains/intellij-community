// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.Chip
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableChip
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
public fun ChipsAndTrees(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.weight(1f).semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupHeader(text = "Chips", modifier = Modifier.fillMaxWidth())
            ChipsSample(Modifier.padding(8.dp))
        }

        Column(
            Modifier.weight(1f).semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupHeader("Tree", modifier = Modifier.fillMaxWidth())
            TreeSample()
        }

        Column(
            Modifier.weight(1f).semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupHeader("SelectableLazyColumn", modifier = Modifier.width(300.dp))
            SelectableLazyColumnSample()
        }
    }
}

@Composable
public fun ChipsSample(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var selectedIndex by remember { mutableStateOf(-1) }
            RadioButtonChip(selected = selectedIndex == 0, onClick = { selectedIndex = 0 }, enabled = true) {
                Text("First")
            }

            RadioButtonChip(selected = selectedIndex == 1, onClick = { selectedIndex = 1 }, enabled = true) {
                Text("Second")
            }

            RadioButtonChip(selected = selectedIndex == 2, onClick = { selectedIndex = 2 }, enabled = true) {
                Text("Third")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var isChecked by remember { mutableStateOf(false) }
            ToggleableChip(checked = isChecked, onClick = { isChecked = it }, enabled = true) { Text("Toggleable") }

            var count by remember { mutableIntStateOf(1) }
            Chip(enabled = true, onClick = { count++ }) { Text("Clicks: $count") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(enabled = false, onClick = {}) { Text("Disabled") }
        }
    }
}

@Composable
public fun TreeSample(modifier: Modifier = Modifier) {
    var tree by remember {
        mutableStateOf(
            buildTree {
                addNode("root 1") {
                    addLeaf("leaf 1")
                    addLeaf("leaf 2")
                }
                addNode("root 2") {
                    addLeaf("leaf 2.1")
                    addNode("node 1") {
                        addLeaf("subleaf 1")
                        addLeaf("subleaf 2")
                    }
                }
                addNode("root 3") {
                    addLeaf("leaf 3.1")
                    addLeaf("leaf 3.2")
                }
            }
        )
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({
            tree = buildTree {
                addNode("root ${Random.nextInt(0, 1024)}") {
                    addLeaf("leaf 1")
                    addLeaf("leaf 2")
                }
            }
        }) {
            Text("Rebuild tree")
        }

        val borderColor =
            if (JewelTheme.isDark) {
                JewelTheme.colorPalette.grayOrNull(3) ?: Color(0xFF393B40)
            } else {
                JewelTheme.colorPalette.grayOrNull(12) ?: Color(0xFFEBECF0)
            }

        Box(Modifier.border(1.dp, borderColor, RoundedCornerShape(2.dp))) {
            LazyTree(
                tree = tree,
                modifier = Modifier.size(200.dp, 200.dp).focusable(),
                onElementClick = {},
                onElementDoubleClick = {},
            ) { element ->
                Box(Modifier.fillMaxWidth()) { Text(element.data, Modifier.padding(2.dp)) }
            }
        }
    }
}

@Composable
public fun SelectableLazyColumnSample(modifier: Modifier = Modifier) {
    var listOfItems by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        @Suppress("InjectDispatcher") // Ok for demo code
        launch { listOfItems = withContext(Dispatchers.Default) { List(1_000_000) { "Item $it" } } }
    }

    val state = rememberSelectableLazyListState()
    Box(modifier = modifier.size(200.dp, 200.dp)) {
        if (listOfItems.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            SelectableLazyColumn(modifier = Modifier.focusable(), state = state) {
                items(listOfItems, key = { item -> item }) { item ->
                    SimpleListItem(
                        text = item,
                        selected = isSelected,
                        active = isActive,
                        modifier =
                            Modifier.fillMaxWidth().selectable(isSelected) {
                                JewelLogger.getInstance("ChipsAndTree").info("Click on $item")
                            },
                    )
                }
            }
            VerticalScrollbar(
                rememberScrollbarAdapter(state.lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

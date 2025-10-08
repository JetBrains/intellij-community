// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.Chip
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableChip
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.component.search.SpeedSearchableLazyColumn
import org.jetbrains.jewel.ui.component.search.SpeedSearchableTree
import org.jetbrains.jewel.ui.component.search.highlightSpeedSearchMatches
import org.jetbrains.jewel.ui.component.search.highlightTextSearch
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
    var isRandom by remember { mutableStateOf(false) }
    var tree by remember { mutableStateOf(buildTree(random = false)) }

    // Clean up open nodes when the tree changes
    val treeState = rememberTreeState()
    LaunchedEffect(tree) { treeState.openNodes.forEach(treeState::toggleNode) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.width(200.dp)) {
            OutlinedButton(
                onClick = {
                    isRandom = false
                    tree = buildTree(random = false)
                },
                enabled = isRandom,
            ) {
                Text("Reset")
            }

            DefaultButton({
                isRandom = true
                tree = buildTree(random = true)
            }) {
                Text("Randomize")
            }
        }

        val borderColor =
            if (JewelTheme.isDark) {
                JewelTheme.colorPalette.grayOrNull(3) ?: Color(0xFF393B40)
            } else {
                JewelTheme.colorPalette.grayOrNull(12) ?: Color(0xFFEBECF0)
            }

        SpeedSearchArea(Modifier.border(1.dp, borderColor, RoundedCornerShape(2.dp))) {
            SpeedSearchableTree(
                tree = tree,
                treeState = treeState,
                modifier = Modifier.size(200.dp, 200.dp).focusable(),
                onElementClick = {},
                onElementDoubleClick = {},
                nodeText = { it.data },
            ) { element ->
                Box(Modifier.fillMaxWidth()) {
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    Text(
                        element.data.highlightTextSearch(),
                        Modifier.padding(2.dp).highlightSpeedSearchMatches(textLayoutResult),
                        onTextLayout = { textLayoutResult = it },
                    )
                }
            }
        }
    }
}

@Composable
public fun SelectableLazyColumnSample(modifier: Modifier = Modifier) {
    var randomIndex by remember { mutableStateOf(-1) }
    var listOfItems by remember { mutableStateOf(emptyList<String>()) }
    val state = rememberSelectableLazyListState()

    LaunchedEffect(randomIndex) {
        @Suppress("InjectDispatcher") // Ok for demo code
        listOfItems =
            withContext(Dispatchers.Default) {
                if (randomIndex >= 0) {
                    (1..Random.nextInt(100, 1_000_000) step Random.nextInt(1, 9)).map { "Item $it" }
                } else {
                    List(1_000_000) { "Item $it" }
                }
            }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.width(200.dp)) {
            OutlinedButton(onClick = { randomIndex = -1 }, enabled = randomIndex >= 0) { Text("Reset") }

            DefaultButton({ randomIndex += 1 }) { Text("Randomize") }
        }

        Box(modifier = Modifier.size(200.dp)) {
            if (listOfItems.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                SpeedSearchArea {
                    SpeedSearchableLazyColumn(modifier = Modifier.focusable(), state = state) {
                        items(listOfItems, textContent = { item -> item }, key = { item -> item }) { item ->
                            LaunchedEffect(isSelected) {
                                if (isSelected) {
                                    JewelLogger.getInstance("ChipsAndTree").info("Item $item got selected")
                                }
                            }

                            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                            SimpleListItem(
                                text = item.highlightTextSearch(),
                                selected = isSelected,
                                active = isActive,
                                onTextLayout = { textLayoutResult = it },
                                modifier = Modifier.fillMaxWidth(),
                                textModifier = Modifier.highlightSpeedSearchMatches(textLayoutResult),
                            )
                        }
                    }

                    VerticalScrollbar(state.lazyListState, modifier = Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

private fun buildTree(random: Boolean) = buildTree {
    if (random) {
        val startingNode = Random.nextInt(0, 1024)
        val randomTree =
            (1 until Random.nextInt(2, 128) step Random.nextInt(1, 5)).associate { root ->
                (root + startingNode) to (1 until Random.nextInt(2, 128) step Random.nextInt(1, 10))
            }

        randomTree.forEach { (root, children) ->
            addNode("Random root $root") { children.forEach { leaf -> addLeaf("Random leaf $leaf") } }
        }
    } else {
        repeat(100) { root ->
            addNode("root ${root + 1}") {
                repeat(100) { node ->
                    addNode("node ${root + 1}.${node + 1}") {
                        repeat(100) { addLeaf("subleaf ${root + 1}.${node + 1}.${it + 1}") }
                    }
                }
                repeat(100) { addLeaf("leaf ${root + 1}.${it + 1}") }
            }
        }
    }
}

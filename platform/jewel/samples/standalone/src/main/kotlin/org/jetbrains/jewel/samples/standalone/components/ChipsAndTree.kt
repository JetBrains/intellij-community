package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.Chip
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.LazyTree
import org.jetbrains.jewel.RadioButtonChip
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.ToggleableChip
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.tree.buildTree
import org.jetbrains.jewel.intui.standalone.IntUiTheme

@Composable
fun ChipsAndTree() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(text = "Chips", modifier = Modifier.fillMaxWidth())
            ChipsSample(Modifier.padding(8.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader("Tree", modifier = Modifier.fillMaxWidth())
            TreeSample()
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader("SelectableLazyColumn", modifier = Modifier.width(300.dp))
            SelectableLazyColumnSample()
        }
    }
}

@Composable
fun SelectableLazyColumnSample() {
    val listOfItems = remember {
        List((5000..10000).random()) { "Item $it" }
    }
    val interactionSource = remember { MutableInteractionSource() }
    SelectableLazyColumn(
        modifier = Modifier
            .size(200.dp, 200.dp)
            .focusable(interactionSource = interactionSource),
        content = {
            items(
                count = listOfItems.size,
                key = { index -> listOfItems[index] },
            ) { index ->
                Text(
                    text = listOfItems[index],
                    modifier =
                    Modifier.then(
                        when {
                            isSelected && isActive -> Modifier.background(Color.Blue)
                            isSelected && !isActive -> Modifier.background(Color.Gray)
                            else -> Modifier
                        },
                    ).clickable {
                        println("click on $index")
                    },
                )
            }
        },
        interactionSource = remember { MutableInteractionSource() },
    )
}

@Composable
fun ChipsSample(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var selectedIndex by remember { mutableStateOf(-1) }
            RadioButtonChip(
                selected = selectedIndex == 0,
                onClick = { selectedIndex = 0 },
                enabled = true,
            ) {
                Text("First")
            }

            RadioButtonChip(
                selected = selectedIndex == 1,
                onClick = { selectedIndex = 1 },
                enabled = true,
            ) {
                Text("Second")
            }

            RadioButtonChip(
                selected = selectedIndex == 2,
                onClick = { selectedIndex = 2 },
                enabled = true,
            ) {
                Text("Third")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var isChecked by remember { mutableStateOf(false) }
            ToggleableChip(
                checked = isChecked,
                onClick = {
                    isChecked = it
                },
                enabled = true,
            ) {
                Text("Toggleable")
            }

            var count by remember { mutableStateOf(1) }
            Chip(
                enabled = true,
                onClick = { count++ },
            ) {
                Text("Clicks: $count")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(
                enabled = false,
                onClick = {},
            ) {
                Text("Disabled")
            }
        }
    }
}

@Composable
fun TreeSample(modifier: Modifier = Modifier) {
    val tree = remember {
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
    }

    val borderColor =
        if (IntUiTheme.isDark) {
            IntUiTheme.colorPalette.grey(3)
        } else {
            IntUiTheme.colorPalette.grey(12)
        }

    Box(modifier.border(1.dp, borderColor, RoundedCornerShape(2.dp))) {
        LazyTree(
            tree = tree,
            modifier = Modifier.size(200.dp, 200.dp),
            onElementClick = {},
            onElementDoubleClick = {},
        ) { element ->
            Box(Modifier.fillMaxWidth()) {
                Text(element.data, Modifier.padding(2.dp))
            }
        }
    }
}

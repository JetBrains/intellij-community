package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.BaseChip
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.IntelliJTree
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.foundation.tree.buildTree

@Composable
fun ChipsAndTree() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            GroupHeader(text = "Chips", modifier = Modifier.width(300.dp))
            ChipsRow(Modifier.padding(8.dp))
        }
        Column {
            GroupHeader("Tree", modifier = Modifier.width(300.dp))
            TreeSample()
        }
    }
}

@Composable
fun ChipsRow(modifier: Modifier = Modifier) {
    Row(modifier) {
        BaseChip(
            enabled = true,
            onChipClick = {}
        ) {
            Text("Enabled")
        }
        BaseChip(
            enabled = false,
            onChipClick = {}
        ) {
            Text("Disabled")
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
                addLeaf("leaf 1")
                addNode("node 1") {
                    addLeaf("leaf 1")
                    addLeaf("leaf 2")
                }
            }
            addNode("root 3") {
                addLeaf("leaf 1")
                addLeaf("leaf 2")
            }
        }
    }
    IntelliJTree(
        Modifier.size(200.dp, 200.dp).then(modifier),
        onElementClick = {},
        onElementDoubleClick = {},
        tree = tree
    ) { element ->
        Text(element.data, modifier.padding(2.dp))
    }
}

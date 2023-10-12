package org.jetbrains.jewel.samples.ideplugin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import org.jetbrains.jewel.CheckboxRow
import org.jetbrains.jewel.CircularProgressIndicator
import org.jetbrains.jewel.CircularProgressIndicatorBig
import org.jetbrains.jewel.DefaultButton
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IconButton
import org.jetbrains.jewel.LazyTree
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.OutlinedButton
import org.jetbrains.jewel.RadioButtonRow
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.TextField
import org.jetbrains.jewel.Tooltip
import org.jetbrains.jewel.bridge.SwingBridgeService
import org.jetbrains.jewel.bridge.SwingBridgeTheme
import org.jetbrains.jewel.bridge.retrieveStatelessIcon
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.tree.buildTree
import org.jetbrains.jewel.intui.standalone.IntUiTheme

@Composable
internal fun ComponentShowcaseTab() {
    SwingBridgeTheme {
        val resourceLoader = LocalResourceLoader.current
        val bgColor by remember(IntUiTheme.isDark) { mutableStateOf(JBColor.PanelBackground.toComposeColor()) }

        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ColumnOne(resourceLoader)
            ColumnTwo(resourceLoader)
        }
    }
}

@Composable
private fun RowScope.ColumnOne(resourceLoader: ResourceLoader) {
    Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Here is a selection of our finest components:")

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var clicks1 by remember { mutableStateOf(0) }
            OutlinedButton({ clicks1++ }) {
                Text("Outlined: $clicks1")
            }
            OutlinedButton({ }, enabled = false) {
                Text("Outlined")
            }

            var clicks2 by remember { mutableStateOf(0) }
            DefaultButton({ clicks2++ }) {
                Text("Default: $clicks2")
            }
            DefaultButton({ }, enabled = false) {
                Text("Default")
            }
        }

        var textFieldValue by remember { mutableStateOf("") }
        TextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            placeholder = { Text("Write something...") },
            modifier = Modifier.width(200.dp),
        )

        var checked by remember { mutableStateOf(false) }
        CheckboxRow(
            checked = checked,
            onCheckedChange = { checked = it },
            resourceLoader = resourceLoader,
        ) {
            Text("Hello, I am a themed checkbox")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            var index by remember { mutableStateOf(0) }
            RadioButtonRow(selected = index == 0, resourceLoader, onClick = { index = 0 }) {
                Text("I am number one")
            }
            RadioButtonRow(selected = index == 1, resourceLoader, onClick = { index = 1 }) {
                Text("Sad second")
            }
        }

        val svgLoader = service<SwingBridgeService>().svgLoader
        Row {
            val painterProvider = retrieveStatelessIcon("actions/close.svg", svgLoader, IntUiTheme.iconData)
            val painter by painterProvider.getPainter(resourceLoader)
            Icon(painter = painter, modifier = Modifier.border(1.dp, Color.Magenta), contentDescription = "An icon")
        }

        Row {
            IconButton(onClick = { }) {
                val painterProvider = retrieveStatelessIcon("actions/close.svg", svgLoader, IntUiTheme.iconData)
                val painter by painterProvider.getPainter(resourceLoader)
                Icon(painter = painter, contentDescription = "An icon")
            }
        }

        Row {
            Text("Circular progress small: ")
            CircularProgressIndicator(svgLoader)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Circular progress big: ")
            CircularProgressIndicatorBig(svgLoader)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Tooltip(tooltip = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val painterProvider =
                        retrieveStatelessIcon("general/showInfos.svg", svgLoader, IntUiTheme.iconData)
                    val painter by painterProvider.getPainter(resourceLoader)
                    Icon(painter = painter, contentDescription = null)

                    Text("This is a tooltip")
                }
            }) {
                Text(
                    modifier = Modifier.border(1.dp, IntUiTheme.globalColors.borders.normal)
                        .padding(12.dp, 8.dp),
                    text = "Hover Me!",
                )
            }
        }
    }
}

@Composable
private fun RowScope.ColumnTwo(resourceLoader: ResourceLoader) {
    Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
        LazyTree(
            tree = tree,
            resourceLoader = resourceLoader,
            modifier = Modifier.height(200.dp).fillMaxWidth(),
            onElementClick = {},
            onElementDoubleClick = {},
        ) { element ->
            Box(Modifier.fillMaxWidth()) {
                Text(element.data, Modifier.padding(2.dp))
            }
        }
    }
}

@file:OptIn(ExperimentalSplitPaneApi::class)

package org.jetbrains.jewel.sample

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.components.Checkbox
import org.jetbrains.jewel.theme.intellij.components.Separator
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Text
import org.jetbrains.jewel.theme.intellij.components.Tree
import org.jetbrains.jewel.theme.intellij.components.TreeLayout
import org.jetbrains.jewel.theme.intellij.components.asTree
import org.jetbrains.skiko.Cursor
import java.nio.file.Paths

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
fun main() = singleWindowApplication {
    var isDarkTheme by remember { mutableStateOf(true) }
    IntelliJTheme(isDarkTheme) {
        Surface {
            Column {
                Row(Modifier.focusable()) {
                    Text("Dark theme:")
                    Checkbox(checked = isDarkTheme, onCheckedChange = { isDarkTheme = it })
                }
                Row {
                    var tree by remember { mutableStateOf(Paths.get(System.getProperty("user.dir")).asTree(true)) }
                    val splitPanelState = rememberSplitPaneState(initialPositionPercentage = .33f)
                    HorizontalSplitPane(splitPaneState = splitPanelState) {
                        first {
                            Box {
                                val listState = rememberLazyListState()
                                TreeLayout(
                                    tree = tree,
                                    state = listState,
                                    onTreeChanged = { tree = it },
                                    onTreeElementDoubleClick = {
                                        when (it) {
                                            is Tree.Element.Leaf -> println("CIAO ${it.data.absolutePath}")
                                            is Tree.Element.Node -> tree = tree.replaceElement(it, it.copy(isOpen = !it.isOpen))
                                        }
                                    },
                                    rowContent = {
                                        val text: String = when (it) {
                                            is Tree.Element.Leaf -> it.data.name
                                            is Tree.Element.Node -> "[${it.data.name}]"
                                        }
                                        Text(modifier = Modifier.fillMaxWidth(), text = text, softWrap = false)
                                    },
                                )
                                if (listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size) {
                                    VerticalScrollbar(
                                        modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 2.dp),
                                        adapter = rememberScrollbarAdapter(listState)
                                    )
                                }
                            }
                        }
                        second {
                            Text("Hello world2")
                        }
                        splitter {
                            handle {
                                Separator(
                                    modifier = Modifier.markAsHandle().cursorForVerticalResize(),
                                    orientation = Orientation.Vertical,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.cursorForVerticalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))

package org.jetbrains.jewel.samples.ideplugin

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.components.Surface
import org.jetbrains.jewel.components.Text
import org.jetbrains.jewel.components.Tree
import org.jetbrains.jewel.components.TreeLayout
import org.jetbrains.jewel.components.asTree
import org.jetbrains.jewel.themes.darcula.idebridge.IntelliJTheme
import org.jetbrains.jewel.themes.darcula.idebridge.addComposePanel
import java.nio.file.Paths

@ExperimentalCoroutinesApi
internal class PKGSDemo : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposePanel("Packages") {
            IntelliJTheme {
                Surface {
                    var tree by remember { mutableStateOf(Paths.get(project.basePath ?: System.getProperty("user.dir")).asTree(true)) }

                    Box {
                        val listState = rememberLazyListState()
                        TreeLayout(
                            modifier = Modifier.fillMaxWidth(),
                            tree = tree,
                            state = listState,
                            onTreeChanged = { tree = it },
                            onTreeElementDoubleClick = {
                                when (it) {
                                    is Tree.Element.Leaf -> println("CIAO ${it.data.absolutePath}")
                                    is Tree.Element.Node ->
                                        tree =
                                            tree.replaceElement(it, it.copy(isOpen = !it.isOpen))
                                }
                            },
                            rowContent = {
                                val text: String = when (it) {
                                    is Tree.Element.Leaf -> it.data.name
                                    is Tree.Element.Node -> "[${it.data.name}]"
                                }
                                Text(modifier = Modifier.fillMaxWidth(), text = text, softWrap = false)
                            }
                        )
                        if (listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size) {
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 2.dp),
                                adapter = rememberScrollbarAdapter(listState)
                            )
                        }
                    }
                }
            }
        }
    }
}

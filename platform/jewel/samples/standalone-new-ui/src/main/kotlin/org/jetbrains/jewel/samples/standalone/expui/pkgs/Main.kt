package org.jetbrains.jewel.samples.standalone.expui.pkgs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneScope
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.tree.Tree
import org.jetbrains.jewel.foundation.tree.asTree
import org.jetbrains.jewel.themes.expui.desktop.window.JBWindow
import org.jetbrains.jewel.themes.expui.standalone.control.ActionButton
import org.jetbrains.jewel.themes.expui.standalone.control.Icon
import org.jetbrains.jewel.themes.expui.standalone.control.IntelliJTree
import org.jetbrains.jewel.themes.expui.standalone.control.Label
import org.jetbrains.jewel.themes.expui.standalone.control.Tooltip
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.border
import org.jetbrains.jewel.themes.expui.standalone.theme.DarkTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import org.jetbrains.skiko.Cursor
import java.awt.Desktop
import java.net.URI
import java.nio.file.Paths
import kotlin.system.exitProcess

@OptIn(ExperimentalSplitPaneApi::class)
fun main() = application {
    var isDark by remember { mutableStateOf(false) }
    val theme = if (isDark) {
        DarkTheme
    } else {
        LightTheme
    }
    val tree = remember { Paths.get(System.getProperty("user.dir")).asTree() }
    val leafIconSet = remember { setOf("gradleIcon.svg", "nodejsIcon.svg") }
    val nodeIcon = remember { "folderIcon.svg" }

    JBWindow(
        title = "Jewel New UI Sample for PackageSearch",
        theme = theme,
        state = rememberWindowState(size = DpSize(1600.dp, 600.dp)),
        onCloseRequest = {
            exitApplication()
            exitProcess(0)
        },
        mainToolBar = {
            Row(Modifier.mainToolBarItem(Alignment.End)) {
                Tooltip("Open GitHub link in browser") {
                    ActionButton(
                        {
                            Desktop.getDesktop()
                                .browse(URI.create("https://github.com/ButterCam/compose-jetbrains-theme"))
                        },
                        Modifier.size(40.dp),
                        shape = RectangleShape
                    ) {
                        Icon("icons/github.svg")
                    }
                }
                Tooltip("Switch between dark and light mode,\ncurrently is ${if (isDark) "dark" else "light"} mode") {
                    ActionButton(
                        { isDark = !isDark },
                        Modifier.size(40.dp),
                        shape = RectangleShape
                    ) {
                        if (isDark) {
                            Icon("icons/darkTheme.svg")
                        } else {
                            Icon("icons/lightTheme.svg")
                        }
                    }
                }
            }
        }
    ) {
        val pkgsViewModel = remember { PackageSearchViewModel() }
        val splitPanelState = rememberSplitPaneState(initialPositionPercentage = .12f)
        val areaColors = LocalAreaColors.current
        Column {
            HorizontalSplitPane(splitPaneState = splitPanelState) {
                first {
                    Column(Modifier.padding(end = 8.dp)) {
                        IntelliJTree(
                            tree = tree
                        ) {
                            Row {
                                val icon: String = remember { if (it is Tree.Element.Node) nodeIcon else leafIconSet.random() }
                                Icon(resource = "icons/" + icon)
                                Label(modifier = Modifier.fillMaxWidth(), text = it.data.name, softWrap = false, fontSize = 18.sp)
                            }
                        }
                    }
                }
                defaultPKGSSplitter(areaColors.startBorderColor, PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                second {
                    val innerSplitPaneState = rememberSplitPaneState(initialPositionPercentage = .80f)
                    HorizontalSplitPane(Modifier.padding(start = 8.dp), splitPaneState = innerSplitPaneState) {
                        first {
                            PackageSearchBox(
                                textSearchState = pkgsViewModel.inputText,
                                onTextValueChange = { pkgsViewModel.inputText = it },
                                availableFilters = pkgsViewModel.searchFilters,
                                searchResultsStateList = pkgsViewModel.searchResults,
                                onSearchResultClick = { pkgsViewModel.selectedResult.value = it },
                                selectedModule = pkgsViewModel.selectedModule,
                                addedModules = pkgsViewModel.addedModules.value
                            )
                        }
                        defaultPKGSSplitter(areaColors.startBorderColor, PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        second {
                            Box(Modifier.padding(8.dp).border(areaColors).padding(8.dp)) {
                                Label("there is the last box to fit with others information about selected package")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
fun SplitPaneScope.defaultPKGSSplitter(
    splitterColor: Color,
    cursor: PointerIcon
) {
    splitter {
        visiblePart {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(splitterColor)
            )
        }
        handle {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .markAsHandle()
                    .pointerHoverIcon(cursor)
            )
        }
    }
}

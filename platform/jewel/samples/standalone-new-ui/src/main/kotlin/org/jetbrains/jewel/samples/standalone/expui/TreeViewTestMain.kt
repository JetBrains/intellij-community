package org.jetbrains.jewel.samples.standalone.expui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.foundation.tree.DefaultTreeViewKeyActions
import org.jetbrains.jewel.foundation.tree.TreeView
import org.jetbrains.jewel.foundation.tree.asTree
import org.jetbrains.jewel.foundation.tree.rememberTreeState
import org.jetbrains.jewel.themes.expui.desktop.window.JBWindow
import org.jetbrains.jewel.themes.expui.standalone.control.Label
import org.jetbrains.jewel.themes.expui.standalone.theme.DarkTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main() = application {
    val isDark by remember { mutableStateOf(false) }
    val theme = if (isDark) {
        DarkTheme
    } else {
        LightTheme
    }
    val tree = Paths.get(System.getProperty("user.dir")).asTree()
    JBWindow(
        title = "Jewel New UI Sample",
        theme = theme,
        state = rememberWindowState(size = DpSize(1200.dp, 700.dp)),
        onCloseRequest = {
            exitApplication()
            exitProcess(0)
        },
        mainToolBar = {
        }
    ) {
        val treeState = rememberTreeState()
        Column {
            println(treeState.selectedItemIndexes)
            Label(text = treeState.selectedItemIndexes.joinToString("-"))
            Label(text = treeState.selectedElements.joinToString { (it.data as? File)?.name.toString() + "\n" })
            TreeView(
                tree = tree,
                treeState = treeState,
                arrowContent = {
                    Label(text = if (it) "x" else "--")
                },
                keyActions = DefaultTreeViewKeyActions(treeState),
                selectionBackgroundColor = Color.Cyan,
                focusedBackgroundColor = Color.LightGray,
                selectionFocusedBackgroundColor = Color.Red
            ) {
                Label(it.data.name)
            }
        }
    }
}

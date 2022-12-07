package org.jetbrains.jewel.themes.expui.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import org.jetbrains.jewel.themes.expui.desktop.util.JbrCustomWindowDecorationAccessing
import org.jetbrains.jewel.themes.expui.standalone.control.BasicMainToolBar
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarColors
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarScope
import org.jetbrains.jewel.themes.expui.standalone.style.LocalMainToolBarColors

@Composable
internal fun FrameWindowScope.MainToolBarOnLinux(
    colors: MainToolBarColors = LocalMainToolBarColors.current,
    content: (@Composable MainToolBarScope.() -> Unit)?,
) {
    BasicMainToolBar(colors, JbrCustomWindowDecorationAccessing, content)
}
package org.jetbrains.jewel.themes.expui.desktop.window

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import org.jetbrains.jewel.themes.expui.desktop.util.JbrCustomWindowDecorationAccessing
import org.jetbrains.jewel.themes.expui.standalone.control.BasicMainToolBar
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarColors
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarScope
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarTitle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalMainToolBarColors

@Composable
internal fun FrameWindowScope.MainToolBarOnMacOS(
    title: String,
    showTitle: Boolean,
    isFullScreen: Boolean,
    colors: MainToolBarColors = LocalMainToolBarColors.current,
    content: (@Composable MainToolBarScope.() -> Unit)?,
) {
    BasicMainToolBar(colors, JbrCustomWindowDecorationAccessing) {
        if (isFullScreen) {
            Spacer(Modifier.width(80.dp).mainToolBarItem(Alignment.Start, true))
        }
        if (showTitle) {
            MainToolBarTitle(title)
        }
        content?.invoke(this)
    }
}

package org.jetbrains.jewel.themes.expui.desktop.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import org.jetbrains.jewel.themes.expui.desktop.util.JbrCustomWindowDecorationAccessing
import org.jetbrains.jewel.themes.expui.standalone.control.ActionButton
import org.jetbrains.jewel.themes.expui.standalone.control.BasicMainToolBar
import org.jetbrains.jewel.themes.expui.standalone.control.Icon
import org.jetbrains.jewel.themes.expui.standalone.control.LocalActionButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalContentActivated
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarColors
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarScope
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarTitle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalMainToolBarColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalWindowsCloseWindowButtonColors

@Composable
internal fun FrameWindowScope.MainToolBarOnWindows(
    icon: Painter?,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    title: String,
    showTitle: Boolean,
    resizeable: Boolean,
    colors: MainToolBarColors = LocalMainToolBarColors.current,
    content: (@Composable MainToolBarScope.() -> Unit)?,
) {
    BasicMainToolBar(colors, JbrCustomWindowDecorationAccessing) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(40.dp).mainToolBarItem(Alignment.Start, true),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon)
            }
        }
        WindowsSystemButtons(windowState, resizeable, onCloseRequest)
        if (showTitle) {
            MainToolBarTitle(title)
        }
        content?.invoke(this)
    }
}

@Composable
private fun MainToolBarScope.WindowsSystemButtons(
    windowState: WindowState,
    resizeable: Boolean,
    onCloseRequest: () -> Unit,
) {
    val active = LocalContentActivated.current
    CompositionLocalProvider(
        LocalActionButtonColors provides LocalWindowsCloseWindowButtonColors.current
    ) {
        ActionButton(
            { onCloseRequest() },
            Modifier.focusProperties { canFocus = false }.size(40.dp).mainToolBarItem(Alignment.End),
            shape = RectangleShape
        ) {
            if (active) {
                Icon("icons/windows/closeActive.svg")
            } else {
                Icon("icons/windows/closeInactive.svg")
            }
        }
    }
    ActionButton(
        {
            windowState.placement = when (windowState.placement) {
                WindowPlacement.Floating -> WindowPlacement.Maximized
                WindowPlacement.Maximized -> WindowPlacement.Floating
                WindowPlacement.Fullscreen -> WindowPlacement.Fullscreen
            }
        },
        Modifier.focusProperties { canFocus = false }.size(40.dp).mainToolBarItem(Alignment.End),
        enabled = resizeable,
        shape = RectangleShape
    ) {
        if (windowState.placement == WindowPlacement.Floating) {
            if (active) {
                Icon("icons/windows/maximize.svg")
            } else {
                Icon("icons/windows/maximizeInactive.svg")
            }
        } else {
            if (active) {
                Icon("icons/windows/restore.svg")
            } else {
                Icon("icons/windows/restoreInactive.svg")
            }
        }
    }
    ActionButton(
        { windowState.isMinimized = true },
        Modifier.focusProperties { canFocus = false }.size(40.dp).mainToolBarItem(Alignment.End),
        shape = RectangleShape
    ) {
        if (active) {
            Icon("icons/windows/minimize.svg")
        } else {
            Icon("icons/windows/minimizeInactive.svg")
        }
    }
}

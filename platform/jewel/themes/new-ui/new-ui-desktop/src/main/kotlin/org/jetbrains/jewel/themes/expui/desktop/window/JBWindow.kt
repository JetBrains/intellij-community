package org.jetbrains.jewel.themes.expui.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarScope
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.Theme
import org.jetbrains.jewel.util.isLinux
import org.jetbrains.jewel.util.isMacOs
import org.jetbrains.jewel.util.isWindows
import javax.swing.JFrame

val LocalWindow = compositionLocalOf<JFrame> {
    error("CompositionLocal LocalWindow not provided")
}

@Composable
fun JBWindow(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "",
    showTitle: Boolean = true,
    theme: Theme = LightTheme,
    icon: Painter? = painterResource("icons/compose.svg"),
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    mainToolBar: (@Composable MainToolBarScope.() -> Unit)? = null,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    when {
        isLinux() -> JBWindowOnLinux(
            onCloseRequest,
            state,
            visible,
            title,
            theme,
            resizable,
            enabled,
            focusable,
            alwaysOnTop,
            onPreviewKeyEvent,
            onKeyEvent,
            mainToolBar,
            content
        )

        isWindows() -> JBWindowOnWindows(
            onCloseRequest,
            state,
            visible,
            title,
            showTitle,
            theme,
            icon,
            resizable,
            enabled,
            focusable,
            alwaysOnTop,
            onPreviewKeyEvent,
            onKeyEvent,
            mainToolBar,
            content
        )

        isMacOs() -> JBWindowOnMacOS(
            onCloseRequest,
            state,
            visible,
            title,
            showTitle,
            theme,
            resizable,
            enabled,
            focusable,
            alwaysOnTop,
            onPreviewKeyEvent,
            onKeyEvent,
            mainToolBar,
            content
        )

        else -> throw UnsupportedOperationException("Unsupported platform")
    }
}

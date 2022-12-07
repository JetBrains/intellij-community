package org.jetbrains.jewel.themes.expui.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.themes.expui.standalone.control.LocalContentActivated
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarScope
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.Theme

@Composable
internal fun JBWindowOnWindows(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "",
    showTitle: Boolean = true,
    theme: Theme = LightTheme,
    icon: Painter? = null,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    mainToolBar: (@Composable MainToolBarScope.() -> Unit)?,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    Window(
        onCloseRequest,
        state,
        visible,
        title,
        icon,
        false,
        false,
        resizable,
        enabled,
        focusable,
        alwaysOnTop,
        onPreviewKeyEvent,
        onKeyEvent
    ) {
        CompositionLocalProvider(
            LocalWindow provides window,
            LocalContentActivated provides LocalWindowInfo.current.isWindowFocused,
            *theme.provideValues()
        ) {
            Column(Modifier.fillMaxSize()) {
                MainToolBarOnWindows(icon, state, onCloseRequest, title, showTitle, resizable, content = mainToolBar)
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(LocalAreaColors.current.startBorderColor))
                Box(Modifier.fillMaxSize().areaBackground()) {
                    content()
                }
            }
        }
    }
}
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.themes.expui.standalone.control.LocalContentActivated
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarScope
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.Theme
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener

@Composable
internal fun JBWindowOnMacOS(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "",
    showTitle: Boolean = true,
    theme: Theme = LightTheme,
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
        null,
        false,
        false,
        resizable,
        enabled,
        focusable,
        alwaysOnTop,
        onPreviewKeyEvent,
        onKeyEvent
    ) {
        LaunchedEffect(Unit, theme) {
            val rootPane = window.rootPane
            rootPane.putClientProperty(
                "apple.awt.windowAppearance",
                if (theme.isDark) "NSAppearanceNameVibrantDark" else "NSAppearanceNameVibrantLight"
            )
        }
        CompositionLocalProvider(
            LocalWindow provides window,
            LocalContentActivated provides LocalWindowInfo.current.isWindowFocused,
            *theme.provideValues()
        ) {
            Column(Modifier.fillMaxSize()) {
                val isFullscreen by rememberWindowIsFullscreen()
                MainToolBarOnMacOS(title, showTitle, isFullscreen, content = mainToolBar)
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(LocalAreaColors.current.startBorderColor))
                Box(Modifier.fillMaxSize().areaBackground()) {
                    content()
                }
            }
        }
    }
}

@Composable
fun FrameWindowScope.rememberWindowIsFullscreen(): State<Boolean> {
    val isFullscreen = remember {
        mutableStateOf(window.placement == WindowPlacement.Fullscreen)
    }
    DisposableEffect(window) {
        val listener = object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) {
                isFullscreen.value = window.placement == WindowPlacement.Fullscreen
            }

            override fun componentMoved(e: ComponentEvent?) {}

            override fun componentShown(e: ComponentEvent?) {}

            override fun componentHidden(e: ComponentEvent?) {}
        }
        window.addComponentListener(listener)
        onDispose {
            window.removeComponentListener(listener)
        }
    }
    return isFullscreen
}

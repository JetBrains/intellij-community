package org.jetbrains.jewel.window.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint
import org.jetbrains.jewel.window.DecoratedWindowState
import org.jetbrains.jewel.window.TitleBarScope
import org.jetbrains.jewel.window.defaultTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.awt.Frame
import java.awt.event.WindowEvent

@Composable
private fun TitleBarScope.CloseButton(
    onClick: () -> Unit,
    state: DecoratedWindowState,
    style: TitleBarStyle = JewelTheme.defaultTitleBarStyle,
) {
    ControlButton(onClick, state, style.icons.closeButton, "Close", style, style.paneCloseButtonStyle)
}

@Composable
private fun TitleBarScope.ControlButton(
    onClick: () -> Unit,
    state: DecoratedWindowState,
    iconKey: IconKey,
    description: String,
    style: TitleBarStyle = JewelTheme.defaultTitleBarStyle,
    iconButtonStyle: IconButtonStyle = style.paneButtonStyle,
) {
    IconButton(
        onClick,
        Modifier.align(Alignment.End).focusable(false).size(style.metrics.titlePaneButtonSize),
        style = iconButtonStyle,
    ) {
        Icon(iconKey, description, hint = if (state.isActive) PainterHint else Inactive)
    }
}

private data object Inactive : PainterSuffixHint() {
    override fun PainterProviderScope.suffix(): String = "Inactive"
}

@Composable
internal fun TitleBarScope.WindowControlArea(
    window: ComposeWindow,
    state: DecoratedWindowState,
    style: TitleBarStyle = JewelTheme.defaultTitleBarStyle,
) {
    CloseButton({ window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) }, state, style)

    if (state.isMaximized) {
        ControlButton({ window.extendedState = Frame.NORMAL }, state, style.icons.restoreButton, "Restore")
    } else {
        ControlButton(
            { window.extendedState = Frame.MAXIMIZED_BOTH },
            state,
            style.icons.maximizeButton,
            "Maximize",
        )
    }
    ControlButton({ window.extendedState = Frame.ICONIFIED }, state, style.icons.minimizeButton, "Minimize")
}

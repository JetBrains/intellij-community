package org.jetbrains.jewel.window

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import java.awt.Frame
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
internal fun DecoratedWindowScope.TitleBarOnLinux(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = JewelTheme.defaultTitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit,
) {
    var lastPress = 0L
    val viewConfig = LocalViewConfiguration.current
    TitleBarImpl(
        modifier.onPointerEvent(PointerEventType.Press, PointerEventPass.Main) {
            if (
                this.currentEvent.button == PointerButton.Primary &&
                    this.currentEvent.changes.any { changed -> !changed.isConsumed }
            ) {
                JBR.getWindowMove()?.startMovingTogetherWithMouse(window, MouseEvent.BUTTON1)
                if (
                    System.currentTimeMillis() - lastPress in
                        viewConfig.doubleTapMinTimeMillis..viewConfig.doubleTapTimeoutMillis
                ) {
                    if (state.isMaximized) {
                        window.extendedState = Frame.NORMAL
                    } else {
                        window.extendedState = Frame.MAXIMIZED_BOTH
                    }
                }
                lastPress = System.currentTimeMillis()
            }
        },
        gradientStartColor,
        style,
        { _, _ -> PaddingValues(0.dp) },
    ) { state ->
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
        content(state)
    }
}

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

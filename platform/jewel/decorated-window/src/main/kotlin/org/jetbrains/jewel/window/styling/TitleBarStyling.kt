package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.IconButtonStyle
import org.jetbrains.jewel.window.DecoratedWindowState

@Stable
interface TitleBarStyle {

    val colors: TitleBarColors
    val metrics: TitleBarMetrics
    val icons: TitleBarIcons

    val dropdownStyle: DropdownStyle
    val iconButtonStyle: IconButtonStyle
    val paneButtonStyle: IconButtonStyle
    val paneCloseButtonStyle: IconButtonStyle
}

@Stable
interface TitleBarColors {

    val background: Color
    val inactiveBackground: Color
    val content: Color
    val border: Color

    // The background color for newControlButtons(three circles in left top corner) in MacOS fullscreen mode
    val fullscreenControlButtonsBackground: Color

    // The hover and press background color for window control buttons(minimize, maximize) in Linux
    val titlePaneButtonHoverBackground: Color
    val titlePaneButtonPressBackground: Color

    // The hover and press background color for window close button in Linux
    val titlePaneCloseButtonHoverBackground: Color
    val titlePaneCloseButtonPressBackground: Color

    // The hover and press background color for IconButtons in title bar content
    val iconButtonHoverBackground: Color
    val iconButtonPressBackground: Color

    // The hover and press background color for Dropdown in title bar content
    val dropdownPressBackground: Color
    val dropdownHoverBackground: Color

    @Composable
    fun backgroundFor(state: DecoratedWindowState) = rememberUpdatedState(
        when {
            !state.isActive -> inactiveBackground
            else -> background
        },
    )
}

@Stable
interface TitleBarMetrics {

    val height: Dp

    val gradientStartX: Dp

    val gradientEndX: Dp

    val titlePaneButtonSize: DpSize
}

@Immutable
interface TitleBarIcons {

    val minimizeButton: PainterProvider

    val maximizeButton: PainterProvider

    val restoreButton: PainterProvider

    val closeButton: PainterProvider
}

val LocalTitleBarStyle = staticCompositionLocalOf<TitleBarStyle> {
    error("No TitleBarStyle provided")
}

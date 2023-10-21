package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.GenerateDataFunctions
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.IconButtonStyle
import org.jetbrains.jewel.window.DecoratedWindowState

@Stable
@GenerateDataFunctions
class TitleBarStyle(
    val colors: TitleBarColors,
    val metrics: TitleBarMetrics,
    val icons: TitleBarIcons,
    val dropdownStyle: DropdownStyle,
    val iconButtonStyle: IconButtonStyle,
    val paneButtonStyle: IconButtonStyle,
    val paneCloseButtonStyle: IconButtonStyle,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class TitleBarColors(
    val background: Color,
    val inactiveBackground: Color,
    val content: Color,
    val border: Color,

    // The background color for newControlButtons(three circles in left top corner) in MacOS fullscreen mode
    val fullscreenControlButtonsBackground: Color,

    // The hover and press background color for window control buttons(minimize, maximize) in Linux
    val titlePaneButtonHoveredBackground: Color,
    val titlePaneButtonPressedBackground: Color,

    // The hover and press background color for window close button in Linux
    val titlePaneCloseButtonHoveredBackground: Color,
    val titlePaneCloseButtonPressedBackground: Color,

    // The hover and press background color for IconButtons in title bar content
    val iconButtonHoveredBackground: Color,
    val iconButtonPressedBackground: Color,

    // The hover and press background color for Dropdown in title bar content
    val dropdownPressedBackground: Color,
    val dropdownHoveredBackground: Color,
) {

    @Composable
    fun backgroundFor(state: DecoratedWindowState) = rememberUpdatedState(
        when {
            !state.isActive -> inactiveBackground
            else -> background
        },
    )

    companion object
}

@Immutable
@GenerateDataFunctions
class TitleBarMetrics(
    val height: Dp,
    val gradientStartX: Dp,
    val gradientEndX: Dp,
    val titlePaneButtonSize: DpSize,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class TitleBarIcons(
    val minimizeButton: PainterProvider,
    val maximizeButton: PainterProvider,
    val restoreButton: PainterProvider,
    val closeButton: PainterProvider,
) {

    companion object
}

val LocalTitleBarStyle = staticCompositionLocalOf<TitleBarStyle> {
    error("No TitleBarStyle provided")
}

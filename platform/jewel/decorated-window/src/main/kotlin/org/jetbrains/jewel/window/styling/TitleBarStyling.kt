package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.window.DecoratedWindowState

@Stable
@GenerateDataFunctions
public class TitleBarStyle(
    public val colors: TitleBarColors,
    public val metrics: TitleBarMetrics,
    public val icons: TitleBarIcons,
    public val dropdownStyle: DropdownStyle,
    public val iconButtonStyle: IconButtonStyle,
    public val paneButtonStyle: IconButtonStyle,
    public val paneCloseButtonStyle: IconButtonStyle,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class TitleBarColors(
    public val background: Color,
    public val inactiveBackground: Color,
    public val content: Color,
    public val border: Color,
    // The background color for newControlButtons(three circles in left top corner) in MacOS
    // fullscreen mode
    public val fullscreenControlButtonsBackground: Color,
    // The hover and press background color for window control buttons(minimize, maximize) in Linux
    public val titlePaneButtonHoveredBackground: Color,
    public val titlePaneButtonPressedBackground: Color,
    // The hover and press background color for window close button in Linux
    public val titlePaneCloseButtonHoveredBackground: Color,
    public val titlePaneCloseButtonPressedBackground: Color,
    // The hover and press background color for IconButtons in title bar content
    public val iconButtonHoveredBackground: Color,
    public val iconButtonPressedBackground: Color,
    // The hover and press background color for Dropdown in title bar content
    public val dropdownPressedBackground: Color,
    public val dropdownHoveredBackground: Color,
) {
    @Composable
    public fun backgroundFor(state: DecoratedWindowState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isActive -> inactiveBackground
                else -> background
            }
        )

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TitleBarMetrics(
    public val height: Dp,
    public val gradientStartX: Dp,
    public val gradientEndX: Dp,
    public val titlePaneButtonSize: DpSize,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class TitleBarIcons(
    public val minimizeButton: IconKey,
    public val maximizeButton: IconKey,
    public val restoreButton: IconKey,
    public val closeButton: IconKey,
) {
    public companion object
}

public val LocalTitleBarStyle: ProvidableCompositionLocal<TitleBarStyle> = staticCompositionLocalOf {
    error("No TitleBarStyle provided. Have you forgotten the theme?")
}

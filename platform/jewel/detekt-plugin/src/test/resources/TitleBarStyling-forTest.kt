// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TitleBarStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false
        if (dropdownStyle != other.dropdownStyle) return false
        if (iconButtonStyle != other.iconButtonStyle) return false
        if (paneButtonStyle != other.paneButtonStyle) return false
        if (paneCloseButtonStyle != other.paneCloseButtonStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        result = 31 * result + dropdownStyle.hashCode()
        result = 31 * result + iconButtonStyle.hashCode()
        result = 31 * result + paneButtonStyle.hashCode()
        result = 31 * result + paneCloseButtonStyle.hashCode()
        return result
    }

    override fun toString(): String {
        return "TitleBarStyle(" +
               "colors=$colors, " +
               "metrics=$metrics, " +
               "icons=$icons, " +
               "dropdownStyle=$dropdownStyle, " +
               "iconButtonStyle=$iconButtonStyle, " +
               "paneButtonStyle=$paneButtonStyle, " +
               "paneCloseButtonStyle=$paneCloseButtonStyle" +
               ")"
    }

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TitleBarColors

        if (background != other.background) return false
        if (inactiveBackground != other.inactiveBackground) return false
        if (content != other.content) return false
        if (border != other.border) return false
        if (fullscreenControlButtonsBackground != other.fullscreenControlButtonsBackground) return false
        if (titlePaneButtonHoveredBackground != other.titlePaneButtonHoveredBackground) return false
        if (titlePaneButtonPressedBackground != other.titlePaneButtonPressedBackground) return false
        if (titlePaneCloseButtonHoveredBackground != other.titlePaneCloseButtonHoveredBackground) return false
        if (titlePaneCloseButtonPressedBackground != other.titlePaneCloseButtonPressedBackground) return false
        if (iconButtonHoveredBackground != other.iconButtonHoveredBackground) return false
        if (iconButtonPressedBackground != other.iconButtonPressedBackground) return false
        if (dropdownPressedBackground != other.dropdownPressedBackground) return false
        if (dropdownHoveredBackground != other.dropdownHoveredBackground) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + inactiveBackground.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + fullscreenControlButtonsBackground.hashCode()
        result = 31 * result + titlePaneButtonHoveredBackground.hashCode()
        result = 31 * result + titlePaneButtonPressedBackground.hashCode()
        result = 31 * result + titlePaneCloseButtonHoveredBackground.hashCode()
        result = 31 * result + titlePaneCloseButtonPressedBackground.hashCode()
        result = 31 * result + iconButtonHoveredBackground.hashCode()
        result = 31 * result + iconButtonPressedBackground.hashCode()
        result = 31 * result + dropdownPressedBackground.hashCode()
        result = 31 * result + dropdownHoveredBackground.hashCode()
        return result
    }

    override fun toString(): String {
        return "TitleBarColors(" +
               "background=$background, " +
               "inactiveBackground=$inactiveBackground, " +
               "content=$content, " +
               "border=$border, " +
               "fullscreenControlButtonsBackground=$fullscreenControlButtonsBackground, " +
               "titlePaneButtonHoveredBackground=$titlePaneButtonHoveredBackground, " +
               "titlePaneButtonPressedBackground=$titlePaneButtonPressedBackground, " +
               "titlePaneCloseButtonHoveredBackground=$titlePaneCloseButtonHoveredBackground, " +
               "titlePaneCloseButtonPressedBackground=$titlePaneCloseButtonPressedBackground, " +
               "iconButtonHoveredBackground=$iconButtonHoveredBackground, " +
               "iconButtonPressedBackground=$iconButtonPressedBackground, " +
               "dropdownPressedBackground=$dropdownPressedBackground, " +
               "dropdownHoveredBackground=$dropdownHoveredBackground" +
               ")"
    }

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TitleBarMetrics

        if (height != other.height) return false
        if (gradientStartX != other.gradientStartX) return false
        if (gradientEndX != other.gradientEndX) return false
        if (titlePaneButtonSize != other.titlePaneButtonSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = height.hashCode()
        result = 31 * result + gradientStartX.hashCode()
        result = 31 * result + gradientEndX.hashCode()
        result = 31 * result + titlePaneButtonSize.hashCode()
        return result
    }

    override fun toString(): String {
        return "TitleBarMetrics(" +
               "height=$height, " +
               "gradientStartX=$gradientStartX, " +
               "gradientEndX=$gradientEndX, " +
               "titlePaneButtonSize=$titlePaneButtonSize" +
               ")"
    }

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TitleBarIcons

        if (minimizeButton != other.minimizeButton) return false
        if (maximizeButton != other.maximizeButton) return false
        if (restoreButton != other.restoreButton) return false
        if (closeButton != other.closeButton) return false

        return true
    }

    override fun hashCode(): Int {
        var result = minimizeButton.hashCode()
        result = 31 * result + maximizeButton.hashCode()
        result = 31 * result + restoreButton.hashCode()
        result = 31 * result + closeButton.hashCode()
        return result
    }

    override fun toString(): String {
        return "TitleBarIcons(" +
               "minimizeButton=$minimizeButton, " +
               "maximizeButton=$maximizeButton, " +
               "restoreButton=$restoreButton, " +
               "closeButton=$closeButton" +
               ")"
    }

    public companion object
}

public val LocalTitleBarStyle: ProvidableCompositionLocal<TitleBarStyle> = staticCompositionLocalOf {
    error("No TitleBarStyle provided. Have you forgotten the theme?")
}

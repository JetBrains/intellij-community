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

/** Defines the overall visual style of the title bar, combining its colors, metrics, icons, and button styles. */
@Stable
@GenerateDataFunctions
public class TitleBarStyle(
    /** The color tokens used by the title bar. */
    public val colors: TitleBarColors,
    /** The layout metrics for the title bar. */
    public val metrics: TitleBarMetrics,
    /** The icon keys for the title bar's window control buttons. */
    public val icons: TitleBarIcons,
    /** The style applied to dropdowns rendered inside the title bar. */
    public val dropdownStyle: DropdownStyle,
    /** The style applied to icon buttons rendered inside the title bar. */
    public val iconButtonStyle: IconButtonStyle,
    /** The style applied to window control pane buttons (minimize, maximize, restore). */
    public val paneButtonStyle: IconButtonStyle,
    /** The style applied to the window close pane button. */
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

    /** Companion object for [TitleBarStyle]. */
    public companion object
}

/** Holds all color tokens used by the title bar in its various interactive and focus states. */
@Immutable
@GenerateDataFunctions
public class TitleBarColors(
    /** The background color when the window is active. */
    public val background: Color,
    /** The background color when the window is inactive. */
    public val inactiveBackground: Color,
    /** The foreground/content color of the title bar. */
    public val content: Color,
    /** The border color of the title bar. */
    public val border: Color,
    /** The background color for the macOS fullscreen control buttons (the three circles in the top-left corner). */
    public val fullscreenControlButtonsBackground: Color,
    /** The background color for window control buttons (minimize, maximize) on Linux when hovered. */
    public val titlePaneButtonHoveredBackground: Color,
    /** The background color for window control buttons (minimize, maximize) on Linux when pressed. */
    public val titlePaneButtonPressedBackground: Color,
    /** The background color for the window close button on Linux when hovered. */
    public val titlePaneCloseButtonHoveredBackground: Color,
    /** The background color for the window close button on Linux when pressed. */
    public val titlePaneCloseButtonPressedBackground: Color,
    /** The background color for icon buttons in the title bar content area when hovered. */
    public val iconButtonHoveredBackground: Color,
    /** The background color for icon buttons in the title bar content area when pressed. */
    public val iconButtonPressedBackground: Color,
    /** The background color for dropdowns in the title bar content area when pressed. */
    public val dropdownPressedBackground: Color,
    /** The background color for dropdowns in the title bar content area when hovered. */
    public val dropdownHoveredBackground: Color,
) {
    /**
     * Returns a [State] holding the background color appropriate for the given [state]: the inactive background when
     * the window is not active, or the active background otherwise.
     *
     * @param state The current [DecoratedWindowState] of the window.
     */
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

    /** Companion object for [TitleBarColors]. */
    public companion object
}

/** Holds the layout metrics for the title bar: height, gradient positions, and control button size. */
@Immutable
@GenerateDataFunctions
public class TitleBarMetrics(
    /** The height of the title bar. */
    public val height: Dp,
    /** The horizontal start position of the title bar gradient. */
    public val gradientStartX: Dp,
    /** The horizontal end position of the title bar gradient. */
    public val gradientEndX: Dp,
    /** The size of the window control pane buttons (minimize, maximize, restore, close). */
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

    /** Companion object for [TitleBarMetrics]. */
    public companion object
}

/** Holds the icon keys for the title bar's window control buttons (minimize, maximize, restore, close). */
@Immutable
@GenerateDataFunctions
public class TitleBarIcons(
    /** The icon key for the minimize window control button. */
    public val minimizeButton: IconKey,
    /** The icon key for the maximize window control button. */
    public val maximizeButton: IconKey,
    /** The icon key for the restore window control button. */
    public val restoreButton: IconKey,
    /** The icon key for the close window control button. */
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

    /** Companion object for [TitleBarIcons]. */
    public companion object
}

/** CompositionLocal that provides the current [TitleBarStyle]. Must be provided by the active theme. */
public val LocalTitleBarStyle: ProvidableCompositionLocal<TitleBarStyle> = staticCompositionLocalOf {
    error("No TitleBarStyle provided. Have you forgotten the theme?")
}

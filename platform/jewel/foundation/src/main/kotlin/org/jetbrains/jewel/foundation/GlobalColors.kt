package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Defines the global colors used in the Jewel UI. These colors are not component-specific and can be used to style any
 * part of the application.
 *
 * @param borders The colors used for component borders.
 * @param outlines The colors used for component outlines, which are drawn outside the borders.
 * @param text The colors used for text elements.
 * @param panelBackground The background color for panels and windows.
 * @param toolwindowBackground The background color for tool windows. It is [`Unspecified`][Color.Unspecified] outside
 *   of the IDE.
 */
@Immutable
@GenerateDataFunctions
public class GlobalColors(
    public val borders: BorderColors,
    public val outlines: OutlineColors,
    public val text: TextColors,
    public val panelBackground: Color,
    public val toolwindowBackground: Color,
) {
    @Deprecated(
        "Use the constructor with toolwindowBackground instead",
        ReplaceWith("GlobalColors(borders, outlines, text, panelBackground, toolwindowBackground)"),
    )
    public constructor(
        borders: BorderColors,
        outlines: OutlineColors,
        text: TextColors,
        panelBackground: Color,
    ) : this(borders, outlines, text, panelBackground, Color.Unspecified)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GlobalColors

        if (borders != other.borders) return false
        if (outlines != other.outlines) return false
        if (text != other.text) return false
        if (panelBackground != other.panelBackground) return false
        if (toolwindowBackground != other.toolwindowBackground) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borders.hashCode()
        result = 31 * result + outlines.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + panelBackground.hashCode()
        result = 31 * result + toolwindowBackground.hashCode()
        return result
    }

    override fun toString(): String =
        "GlobalColors(" +
            "borders=$borders, " +
            "outlines=$outlines, " +
            "text=$text, " +
            "panelBackground=$panelBackground, " +
            "toolwindowBackground=$toolwindowBackground" +
            ")"

    public companion object
}

/**
 * Defines the different colors for text elements in various states.
 *
 * @param normal The default text color.
 * @param selected The text color for selected elements.
 * @param disabled The text color for disabled elements.
 * @param disabledSelected The text color for disabled and selected elements.
 * @param info The text color for informational messages.
 * @param error The text color for error messages.
 * @param warning The text color for warning messages.
 */
@Immutable
@GenerateDataFunctions
public class TextColors(
    public val normal: Color,
    public val selected: Color,
    public val disabled: Color,
    public val disabledSelected: Color,
    public val info: Color,
    public val error: Color,
    public val warning: Color,
) {
    @Deprecated(
        "Use the primary constructors with disabledSelected and warning, instead.",
        ReplaceWith("TextColors(normal, selected, disabled, disabled, info, error, normal)"),
    )
    public constructor(
        normal: Color,
        selected: Color,
        disabled: Color,
        info: Color,
        error: Color,
    ) : this(normal, selected, disabled, disabled, info, error, normal)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextColors

        if (normal != other.normal) return false
        if (selected != other.selected) return false
        if (disabled != other.disabled) return false
        if (disabledSelected != other.disabledSelected) return false
        if (info != other.info) return false
        if (error != other.error) return false
        if (warning != other.warning) return false

        return true
    }

    override fun hashCode(): Int {
        var result = normal.hashCode()
        result = 31 * result + selected.hashCode()
        result = 31 * result + disabled.hashCode()
        result = 31 * result + disabledSelected.hashCode()
        result = 31 * result + info.hashCode()
        result = 31 * result + error.hashCode()
        result = 31 * result + warning.hashCode()
        return result
    }

    override fun toString(): String =
        "TextColors(normal=$normal, selected=$selected, disabled=$disabled, disabledSelected=$disabledSelected, " +
            "info=$info, error=$error, warning=$warning)"

    public companion object
}

/**
 * Defines the colors for component borders in different states.
 *
 * @param normal The default border color.
 * @param focused The border color when the component is focused.
 * @param disabled The border color when the component is disabled.
 */
@Immutable
@GenerateDataFunctions
public class BorderColors(public val normal: Color, public val focused: Color, public val disabled: Color) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BorderColors

        if (normal != other.normal) return false
        if (focused != other.focused) return false
        if (disabled != other.disabled) return false

        return true
    }

    override fun hashCode(): Int {
        var result = normal.hashCode()
        result = 31 * result + focused.hashCode()
        result = 31 * result + disabled.hashCode()
        return result
    }

    override fun toString(): String = "BorderColors(normal=$normal, focused=$focused, disabled=$disabled)"

    public companion object
}

/**
 * Defines the colors for component outlines in different states. Outlines are drawn outside the component's borders.
 *
 * @param focused The outline color when the component is focused.
 * @param focusedWarning The outline color when the component is focused and has a warning.
 * @param focusedError The outline color when the component is focused and has an error.
 * @param warning The outline color for components with a warning.
 * @param error The outline color for components with an error.
 */
@Immutable
@GenerateDataFunctions
public class OutlineColors(
    public val focused: Color,
    public val focusedWarning: Color,
    public val focusedError: Color,
    public val warning: Color,
    public val error: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OutlineColors

        if (focused != other.focused) return false
        if (focusedWarning != other.focusedWarning) return false
        if (focusedError != other.focusedError) return false
        if (warning != other.warning) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = focused.hashCode()
        result = 31 * result + focusedWarning.hashCode()
        result = 31 * result + focusedError.hashCode()
        result = 31 * result + warning.hashCode()
        result = 31 * result + error.hashCode()
        return result
    }

    override fun toString(): String =
        "OutlineColors(" +
            "focused=$focused, " +
            "focusedWarning=$focusedWarning, " +
            "focusedError=$focusedError, " +
            "warning=$warning, " +
            "error=$error" +
            ")"

    public companion object
}

/**
 * Provides the [GlobalColors] for the current composition.
 *
 * @see org.jetbrains.jewel.foundation.theme.JewelTheme.Companion.globalColors
 */
public val LocalGlobalColors: ProvidableCompositionLocal<GlobalColors> = staticCompositionLocalOf {
    error("No GlobalColors provided. Have you forgotten the theme?")
}

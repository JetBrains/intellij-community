package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
@GenerateDataFunctions
public class GlobalColors(
    public val borders: BorderColors,
    public val outlines: OutlineColors,
    public val text: TextColors,
    public val panelBackground: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GlobalColors

        if (borders != other.borders) return false
        if (outlines != other.outlines) return false
        if (text != other.text) return false
        if (panelBackground != other.panelBackground) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borders.hashCode()
        result = 31 * result + outlines.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + panelBackground.hashCode()
        return result
    }

    override fun toString(): String {
        return "GlobalColors(" +
            "borders=$borders, " +
            "outlines=$outlines, " +
            "text=$text, " +
            "panelBackground=$panelBackground" +
            ")"
    }

    public companion object
}

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

    override fun toString(): String {
        return "OutlineColors(" +
            "focused=$focused, " +
            "focusedWarning=$focusedWarning, " +
            "focusedError=$focusedError, " +
            "warning=$warning, " +
            "error=$error" +
            ")"
    }

    public companion object
}

public val LocalGlobalColors: ProvidableCompositionLocal<GlobalColors> = staticCompositionLocalOf {
    error("No GlobalColors provided. Have you forgotten the theme?")
}

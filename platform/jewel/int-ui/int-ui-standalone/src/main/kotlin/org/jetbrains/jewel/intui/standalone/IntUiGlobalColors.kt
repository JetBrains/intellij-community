package org.jetbrains.jewel.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.BorderColors
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.OutlineColors
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme

@Immutable
class IntUiGlobalColors(
    override val borders: BorderColors,
    override val outlines: OutlineColors,
    override val infoContent: Color,
    override val paneBackground: Color,
) : GlobalColors {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntUiGlobalColors

        if (borders != other.borders) return false
        if (outlines != other.outlines) return false
        if (infoContent != other.infoContent) return false
        if (paneBackground != other.paneBackground) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borders.hashCode()
        result = 31 * result + outlines.hashCode()
        result = 31 * result + infoContent.hashCode()
        result = 31 * result + paneBackground.hashCode()
        return result
    }

    override fun toString(): String =
        "IntUiGlobalColors(borders=$borders, outlines=$outlines, infoContent=$infoContent, paneBackground=$paneBackground)"

    companion object {

        @Composable
        fun light(
            borders: BorderColors = IntUiBorderColors.light(),
            outlines: OutlineColors = IntUiOutlineColors.light(),
            infoContent: Color = IntUiLightTheme.colors.grey(7),
            paneBackground: Color = IntUiLightTheme.colors.grey(13),
        ) = IntUiGlobalColors(
            borders = borders,
            outlines = outlines,
            infoContent = infoContent,
            paneBackground = paneBackground,
        )

        @Composable
        fun dark(
            borders: BorderColors = IntUiBorderColors.dark(),
            outlines: OutlineColors = IntUiOutlineColors.dark(),
            infoContent: Color = IntUiDarkTheme.colors.grey(7),
            paneBackground: Color = IntUiDarkTheme.colors.grey(2),
        ) = IntUiGlobalColors(
            borders = borders,
            outlines = outlines,
            infoContent = infoContent,
            paneBackground = paneBackground,
        )
    }
}

@Immutable
class IntUiBorderColors(
    override val normal: Color,
    override val focused: Color,
    override val disabled: Color,
) : BorderColors {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntUiBorderColors

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

    override fun toString(): String =
        "IntUiBorderColors(normal=$normal, focused=$focused, disabled=$disabled)"

    companion object {

        @Composable
        fun light(
            normal: Color = IntUiLightTheme.colors.grey(9),
            focused: Color = IntUiLightTheme.colors.grey(14),
            disabled: Color = IntUiLightTheme.colors.grey(11),
        ) = IntUiBorderColors(normal, focused, disabled)

        @Composable
        fun dark(
            normal: Color = IntUiDarkTheme.colors.grey(5),
            focused: Color = IntUiDarkTheme.colors.grey(2),
            disabled: Color = IntUiDarkTheme.colors.grey(4),
        ) = IntUiBorderColors(normal, focused, disabled)
    }
}

@Immutable
class IntUiOutlineColors(
    override val focused: Color,
    override val focusedWarning: Color,
    override val focusedError: Color,
    override val warning: Color,
    override val error: Color,
) : OutlineColors {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntUiOutlineColors

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
        "IntUiOutlineColors(focused=$focused, focusedWarning=$focusedWarning, focusedError=$focusedError, " +
            "warning=$warning, error=$error)"

    companion object {

        @Composable
        fun light(
            focused: Color = IntUiLightTheme.colors.blue(4),
            focusedWarning: Color = IntUiLightTheme.colors.yellow(4),
            focusedError: Color = IntUiLightTheme.colors.red(4),
            warning: Color = IntUiLightTheme.colors.yellow(7),
            error: Color = IntUiLightTheme.colors.red(9),
        ) = IntUiOutlineColors(focused, focusedWarning, focusedError, warning, error)

        @Composable
        fun dark(
            focused: Color = IntUiDarkTheme.colors.blue(6),
            focusedWarning: Color = IntUiDarkTheme.colors.yellow(4),
            focusedError: Color = IntUiDarkTheme.colors.red(4),
            warning: Color = IntUiDarkTheme.colors.yellow(2),
            error: Color = IntUiDarkTheme.colors.red(2),
        ) = IntUiOutlineColors(focused, focusedWarning, focusedError, warning, error)
    }
}

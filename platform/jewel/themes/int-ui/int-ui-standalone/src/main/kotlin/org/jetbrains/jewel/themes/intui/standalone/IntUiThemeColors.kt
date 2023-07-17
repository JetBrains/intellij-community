package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import org.jetbrains.jewel.BorderColors
import org.jetbrains.jewel.OutlineColors
import org.jetbrains.jewel.SwingLafKey
import org.jetbrains.jewel.ThemeColors
import org.jetbrains.jewel.themes.intui.core.LocalIntUiPalette
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme

data class IntUiThemeColors(
    override val borders: BorderColors,
    override val outlines: OutlineColors,
    @SwingLafKey("*.infoForeground") override val infoContent: Color
) : ThemeColors {

    companion object {

        @Composable
        fun light(
            borders: BorderColors = IntUiBorderColors.light(),
            outlines: OutlineColors = IntUiOutlineColors.light(),
            @SwingLafKey("*.infoForeground") infoContent: Color = Color.Unspecified
        ) = IntUiThemeColors(
            borders = borders,
            outlines = outlines,
            infoContent = infoContent.takeOrElse { LocalIntUiPalette.current.grey(7) }
        )

        @Composable
        fun dark(
            borders: BorderColors = IntUiBorderColors.dark(),
            outlines: OutlineColors = IntUiOutlineColors.dark(),
            @SwingLafKey("*.infoForeground") infoContent: Color = Color.Unspecified
        ) = IntUiThemeColors(
            borders = borders,
            outlines = outlines,
            infoContent = infoContent.takeOrElse { LocalIntUiPalette.current.grey(7) }
        )
    }
}

@Immutable
data class IntUiBorderColors(
    @SwingLafKey("Component.borderColor") override val normal: Color,
    @SwingLafKey("Component.focusedBorderColor") override val focused: Color,
    @SwingLafKey("*.disabledBorderColor") override val disabled: Color
) : BorderColors {

    companion object {

        @Composable
        fun light(
            normal: Color = IntUiLightTheme.colors.grey(9),
            focused: Color = IntUiLightTheme.colors.grey(14),
            disabled: Color = IntUiLightTheme.colors.grey(11)
        ) = IntUiBorderColors(normal, focused, disabled)

        @Composable
        fun dark(
            normal: Color = IntUiDarkTheme.colors.grey(5),
            focused: Color = IntUiDarkTheme.colors.grey(2),
            disabled: Color = IntUiDarkTheme.colors.grey(4)
        ) = IntUiBorderColors(normal, focused, disabled)
    }
}

@Immutable
data class IntUiOutlineColors(
    @SwingLafKey("*.focusColor") override val focused: Color,
    @SwingLafKey("Component.warningFocusColor") override val focusedWarning: Color,
    @SwingLafKey("Component.errorFocusColor") override val focusedError: Color,
    @SwingLafKey("Component.inactiveWarningFocusColor") override val warning: Color,
    @SwingLafKey("Component.inactiveErrorFocusColor") override val error: Color
) : OutlineColors {

    companion object {

        @Composable
        fun light(
            focused: Color = IntUiLightTheme.colors.blue(4),
            focusedWarning: Color = IntUiLightTheme.colors.yellow(4),
            focusedError: Color = IntUiLightTheme.colors.red(4),
            warning: Color = IntUiLightTheme.colors.yellow(7),
            error: Color = IntUiLightTheme.colors.red(9)
        ) = IntUiOutlineColors(focused, focusedWarning, focusedError, warning, error)

        @Composable
        fun dark(
            focused: Color = IntUiDarkTheme.colors.blue(6),
            focusedWarning: Color = IntUiDarkTheme.colors.yellow(4),
            focusedError: Color = IntUiDarkTheme.colors.red(4),
            warning: Color = IntUiDarkTheme.colors.yellow(2),
            error: Color = IntUiDarkTheme.colors.red(2)
        ) = IntUiOutlineColors(focused, focusedWarning, focusedError, warning, error)
    }
}

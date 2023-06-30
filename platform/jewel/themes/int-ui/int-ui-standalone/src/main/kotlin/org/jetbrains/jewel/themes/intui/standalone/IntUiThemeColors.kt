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
import org.jetbrains.jewel.themes.intui.core.palette.IntUiDarkPalette
import org.jetbrains.jewel.themes.intui.core.palette.IntUiLightPalette

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
            normal: Color = IntUiLightPalette.grey(9),
            focused: Color = IntUiLightPalette.grey(14),
            disabled: Color = IntUiLightPalette.grey(11)
        ) = IntUiBorderColors(normal, focused, disabled)

        @Composable
        fun dark(
            normal: Color = IntUiDarkPalette.grey(5),
            focused: Color = IntUiDarkPalette.grey(2),
            disabled: Color = IntUiDarkPalette.grey(4)
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
            focused: Color = IntUiLightPalette.blue(4),
            focusedWarning: Color = IntUiLightPalette.yellow(4),
            focusedError: Color = IntUiLightPalette.red(4),
            warning: Color = IntUiLightPalette.yellow(7),
            error: Color = IntUiLightPalette.red(9)
        ) = IntUiOutlineColors(focused, focusedWarning, focusedError, warning, error)

        @Composable
        fun dark(
            focused: Color = IntUiDarkPalette.blue(6),
            focusedWarning: Color = IntUiDarkPalette.yellow(4),
            focusedError: Color = IntUiDarkPalette.red(4),
            warning: Color = IntUiDarkPalette.yellow(2),
            error: Color = IntUiDarkPalette.red(2)
        ) = IntUiOutlineColors(focused, focusedWarning, focusedError, warning, error)
    }
}

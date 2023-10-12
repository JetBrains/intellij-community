package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.styling.IconButtonColors
import org.jetbrains.jewel.styling.IconButtonMetrics
import org.jetbrains.jewel.styling.IconButtonStyle

@Stable
data class IntUiIconButtonStyle(
    override val colors: IntUiIconButtonColors,
    override val metrics: IntUiIconButtonMetrics,
) : IconButtonStyle {

    companion object {

        @Composable
        fun light() = IntUiIconButtonStyle(IntUiIconButtonColors.light(), IntUiIconButtonMetrics())

        @Composable
        fun dark() = IntUiIconButtonStyle(IntUiIconButtonColors.dark(), IntUiIconButtonMetrics())
    }
}

@Immutable
data class IntUiIconButtonColors(
    override val background: Color,
    override val backgroundDisabled: Color,
    override val backgroundFocused: Color,
    override val backgroundPressed: Color,
    override val backgroundHovered: Color,
    override val border: Color,
    override val borderDisabled: Color,
    override val borderFocused: Color,
    override val borderPressed: Color,
    override val borderHovered: Color,
) : IconButtonColors {

    companion object {

        @Composable
        fun light(
            background: Color = Color.Unspecified,
            backgroundDisabled: Color = background,
            backgroundFocused: Color = background,
            backgroundPressed: Color = IntUiLightTheme.colors.grey(11),
            backgroundHovered: Color = IntUiLightTheme.colors.grey(12),
            border: Color = background,
            borderDisabled: Color = border,
            borderFocused: Color = IntUiLightTheme.colors.blue(5),
            borderPressed: Color = backgroundPressed,
            borderHovered: Color = backgroundHovered,
        ) =
            IntUiIconButtonColors(
                background,
                backgroundDisabled,
                backgroundFocused,
                backgroundPressed,
                backgroundHovered,
                border,
                borderDisabled,
                borderFocused,
                borderPressed,
                borderHovered,
            )

        @Composable
        fun dark(
            background: Color = Color.Unspecified,
            backgroundDisabled: Color = background,
            backgroundFocused: Color = background,
            backgroundPressed: Color = IntUiDarkTheme.colors.grey(5),
            backgroundHovered: Color = IntUiDarkTheme.colors.grey(3),
            border: Color = background,
            borderDisabled: Color = border,
            borderFocused: Color = IntUiDarkTheme.colors.blue(6),
            borderPressed: Color = backgroundPressed,
            borderHovered: Color = backgroundHovered,
        ) =
            IntUiIconButtonColors(
                background,
                backgroundDisabled,
                backgroundFocused,
                backgroundPressed,
                backgroundHovered,
                border,
                borderDisabled,
                borderFocused,
                borderPressed,
                borderHovered,
            )
    }
}

@Stable
data class IntUiIconButtonMetrics(
    override val cornerSize: CornerSize = CornerSize(4.dp),
    override val borderWidth: Dp = 1.dp,
    override val padding: PaddingValues = PaddingValues(0.dp),
    override val minSize: DpSize = DpSize(16.dp, 16.dp),
) : IconButtonMetrics

package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.styling.ButtonColors
import org.jetbrains.jewel.styling.ButtonMetrics
import org.jetbrains.jewel.styling.ButtonStyle
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@Stable
data class IntUiButtonStyle(
    override val colors: IntUiButtonColors,
    override val metrics: IntUiButtonMetrics,
) : ButtonStyle {

    object Default {

        @Composable
        fun light(
            colors: IntUiButtonColors = IntUiButtonColors.Default.light(),
            metrics: IntUiButtonMetrics = IntUiButtonMetrics.Default.create(),
        ): ButtonStyle = IntUiButtonStyle(colors, metrics)

        @Composable
        fun dark(
            colors: IntUiButtonColors = IntUiButtonColors.Default.dark(),
            metrics: IntUiButtonMetrics = IntUiButtonMetrics.Default.create(),
        ): ButtonStyle = IntUiButtonStyle(colors, metrics)
    }

    object Outlined {

        @Composable
        fun light(
            colors: IntUiButtonColors = IntUiButtonColors.Outlined.light(),
            metrics: IntUiButtonMetrics = IntUiButtonMetrics.Outlined.create(),
        ): ButtonStyle = IntUiButtonStyle(colors, metrics)

        @Composable
        fun dark(
            colors: IntUiButtonColors = IntUiButtonColors.Outlined.dark(),
            metrics: IntUiButtonMetrics = IntUiButtonMetrics.Outlined.create(),
        ): ButtonStyle = IntUiButtonStyle(colors, metrics)
    }
}

@Immutable
data class IntUiButtonColors(
    override val background: Brush,
    override val backgroundDisabled: Brush,
    override val backgroundFocused: Brush,
    override val backgroundPressed: Brush,
    override val backgroundHovered: Brush,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val border: Color,
    override val borderDisabled: Color,
    override val borderFocused: Color,
    override val borderPressed: Color,
    override val borderHovered: Color,
) : ButtonColors {

    object Default {

        @Composable
        fun light(
            background: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
            backgroundDisabled: Brush = SolidColor(IntUiLightTheme.colors.grey(12)),
            backgroundFocused: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
            backgroundPressed: Brush = SolidColor(IntUiLightTheme.colors.blue(2)),
            backgroundHovered: Brush = SolidColor(IntUiLightTheme.colors.blue(3)),
            content: Color = IntUiLightTheme.colors.grey(14),
            contentDisabled: Color = IntUiLightTheme.colors.grey(8),
            contentFocused: Color = IntUiLightTheme.colors.grey(14),
            contentPressed: Color = IntUiLightTheme.colors.grey(14),
            contentHovered: Color = IntUiLightTheme.colors.grey(14),
            border: Color = Color.Unspecified,
            borderDisabled: Color = Color.Unspecified,
            borderFocused: Color = Color.Unspecified,
            borderPressed: Color = Color.Unspecified,
            borderHovered: Color = Color.Unspecified,
        ) = IntUiButtonColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            border,
            borderDisabled,
            borderFocused,
            borderPressed,
            borderHovered
        )

        @Composable
        fun dark(
            background: Brush = SolidColor(IntUiDarkTheme.colors.blue(6)),
            backgroundDisabled: Brush = SolidColor(IntUiDarkTheme.colors.grey(5)),
            backgroundFocused: Brush = SolidColor(IntUiDarkTheme.colors.blue(6)),
            backgroundPressed: Brush = SolidColor(IntUiDarkTheme.colors.blue(4)),
            backgroundHovered: Brush = SolidColor(IntUiDarkTheme.colors.blue(5)),
            content: Color = IntUiDarkTheme.colors.grey(14),
            contentDisabled: Color = IntUiDarkTheme.colors.grey(8),
            contentFocused: Color = IntUiDarkTheme.colors.grey(14),
            contentPressed: Color = IntUiDarkTheme.colors.grey(14),
            contentHovered: Color = IntUiDarkTheme.colors.grey(14),
            border: Color = Color.Unspecified,
            borderDisabled: Color = Color.Unspecified,
            borderFocused: Color = Color.Unspecified,
            borderPressed: Color = Color.Unspecified,
            borderHovered: Color = Color.Unspecified,
        ) = IntUiButtonColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            border,
            borderDisabled,
            borderFocused,
            borderPressed,
            borderHovered
        )
    }

    object Outlined {

        @Composable
        fun light(
            background: Brush = SolidColor(Color.Unspecified),
            backgroundDisabled: Brush = SolidColor(IntUiTheme.palette.grey(12)),
            backgroundFocused: Brush = SolidColor(Color.Unspecified),
            backgroundPressed: Brush = SolidColor(IntUiTheme.palette.grey(13)),
            backgroundHovered: Brush = SolidColor(Color.Unspecified),
            content: Color = IntUiTheme.palette.grey(1),
            contentDisabled: Color = IntUiTheme.palette.grey(8),
            contentFocused: Color = IntUiTheme.palette.grey(1),
            contentPressed: Color = IntUiTheme.palette.grey(1),
            contentHovered: Color = IntUiTheme.palette.grey(1),
            border: Color = IntUiTheme.palette.grey(9),
            borderDisabled: Color = Color.Unspecified,
            borderFocused: Color = IntUiTheme.palette.blue(4),
            borderPressed: Color = IntUiTheme.palette.grey(7),
            borderHovered: Color = IntUiTheme.palette.grey(8),
        ) = IntUiButtonColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            border,
            borderDisabled,
            borderFocused,
            borderPressed,
            borderHovered
        )

        @Composable
        fun dark(
            background: Brush = SolidColor(Color.Unspecified),
            backgroundDisabled: Brush = SolidColor(IntUiTheme.palette.grey(5)),
            backgroundFocused: Brush = SolidColor(IntUiTheme.palette.grey(6)),
            backgroundPressed: Brush = SolidColor(IntUiTheme.palette.grey(2)),
            backgroundHovered: Brush = SolidColor(Color.Unspecified),
            content: Color = IntUiTheme.palette.grey(12),
            contentDisabled: Color = IntUiTheme.palette.grey(8),
            contentFocused: Color = IntUiTheme.palette.grey(12),
            contentPressed: Color = IntUiTheme.palette.grey(12),
            contentHovered: Color = IntUiTheme.palette.grey(12),
            border: Color = IntUiTheme.palette.grey(5),
            borderDisabled: Color = Color.Unspecified,
            borderFocused: Color = IntUiTheme.palette.blue(6),
            borderPressed: Color = IntUiTheme.palette.grey(7),
            borderHovered: Color = IntUiTheme.palette.grey(7),
        ) = IntUiButtonColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            border,
            borderDisabled,
            borderFocused,
            borderPressed,
            borderHovered
        )
    }
}

@Stable
data class IntUiButtonMetrics(
    override val cornerSize: CornerSize,
    override val padding: PaddingValues,
    override val minSize: DpSize,
    override val borderWidth: Dp,
) : ButtonMetrics {

    object Default {

        fun create(
            cornerSize: CornerSize = CornerSize(4.dp),
            padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            minSize: DpSize = DpSize(72.dp, 28.dp),
            borderWidth: Dp = 0.dp,
        ) = IntUiButtonMetrics(cornerSize, padding, minSize, borderWidth)
    }

    object Outlined {

        fun create(
            cornerSize: CornerSize = CornerSize(4.dp),
            padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            minSize: DpSize = DpSize(72.dp, 28.dp),
            borderWidth: Dp = 1.dp,
        ) = IntUiButtonMetrics(cornerSize, padding, minSize, borderWidth)
    }
}

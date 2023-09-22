package org.jetbrains.jewel.intui.standalone.styling

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
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.styling.ButtonColors
import org.jetbrains.jewel.styling.ButtonMetrics
import org.jetbrains.jewel.styling.ButtonStyle

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
    override val border: Brush,
    override val borderDisabled: Brush,
    override val borderFocused: Brush,
    override val borderPressed: Brush,
    override val borderHovered: Brush,
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
            border: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
            borderDisabled: Brush = SolidColor(IntUiLightTheme.colors.grey(11)),
            borderFocused: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
            borderPressed: Brush = border,
            borderHovered: Brush = border,
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
            borderHovered,
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
            border: Brush = SolidColor(IntUiDarkTheme.colors.blue(6)),
            borderDisabled: Brush = SolidColor(IntUiDarkTheme.colors.grey(4)),
            borderFocused: Brush = SolidColor(IntUiDarkTheme.colors.grey(1)),
            borderPressed: Brush = border,
            borderHovered: Brush = border,
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
            borderHovered,
        )
    }

    object Outlined {

        @Composable
        fun light(
            background: Brush = SolidColor(Color.Transparent),
            backgroundDisabled: Brush = SolidColor(IntUiTheme.colorPalette.grey(12)),
            backgroundFocused: Brush = SolidColor(Color.Transparent),
            backgroundPressed: Brush = SolidColor(IntUiTheme.colorPalette.grey(13)),
            backgroundHovered: Brush = SolidColor(Color.Transparent),
            content: Color = IntUiTheme.colorPalette.grey(1),
            contentDisabled: Color = IntUiTheme.colorPalette.grey(8),
            contentFocused: Color = IntUiTheme.colorPalette.grey(1),
            contentPressed: Color = IntUiTheme.colorPalette.grey(1),
            contentHovered: Color = IntUiTheme.colorPalette.grey(1),
            border: Brush = SolidColor(IntUiTheme.colorPalette.grey(9)),
            borderDisabled: Brush = SolidColor(IntUiTheme.colorPalette.blue(11)),
            borderFocused: Brush = SolidColor(IntUiTheme.colorPalette.blue(4)),
            borderPressed: Brush = SolidColor(IntUiTheme.colorPalette.grey(7)),
            borderHovered: Brush = SolidColor(IntUiTheme.colorPalette.grey(8)),
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
            borderHovered,
        )

        @Composable
        fun dark(
            background: Brush = SolidColor(Color.Transparent),
            backgroundDisabled: Brush = SolidColor(IntUiTheme.colorPalette.grey(5)),
            backgroundFocused: Brush = SolidColor(IntUiTheme.colorPalette.grey(6)),
            backgroundPressed: Brush = SolidColor(IntUiTheme.colorPalette.grey(2)),
            backgroundHovered: Brush = SolidColor(Color.Unspecified),
            content: Color = IntUiTheme.colorPalette.grey(12),
            contentDisabled: Color = IntUiTheme.colorPalette.grey(8),
            contentFocused: Color = IntUiTheme.colorPalette.grey(12),
            contentPressed: Color = IntUiTheme.colorPalette.grey(12),
            contentHovered: Color = IntUiTheme.colorPalette.grey(12),
            border: Brush = SolidColor(IntUiTheme.colorPalette.grey(5)),
            borderDisabled: Brush = SolidColor(IntUiTheme.colorPalette.blue(4)),
            borderFocused: Brush = SolidColor(IntUiTheme.colorPalette.grey(2)),
            borderPressed: Brush = SolidColor(IntUiTheme.colorPalette.grey(7)),
            borderHovered: Brush = SolidColor(IntUiTheme.colorPalette.grey(7)),
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
            borderHovered,
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

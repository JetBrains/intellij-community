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
import org.jetbrains.jewel.styling.ChipColors
import org.jetbrains.jewel.styling.ChipMetrics
import org.jetbrains.jewel.styling.ChipStyle
import org.jetbrains.jewel.themes.intui.core.palette.IntUiDarkPalette
import org.jetbrains.jewel.themes.intui.core.palette.IntUiLightPalette

@Stable
data class IntUiChipStyle(
    override val colors: IntUiChipColors,
    override val metrics: IntUiChipMetrics
) : ChipStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiChipColors = IntUiChipColors.light(),
            metrics: IntUiChipMetrics = IntUiChipMetrics()
        ) = IntUiChipStyle(colors, metrics)

        @Composable
        fun dark(
            colors: IntUiChipColors = IntUiChipColors.dark(),
            metrics: IntUiChipMetrics = IntUiChipMetrics()
        ) = IntUiChipStyle(colors, metrics)
    }
}

@Immutable
data class IntUiChipColors(
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
    override val borderHovered: Color
) : ChipColors {

    companion object {

        @Composable
        fun light(
            background: Brush = SolidColor(IntUiLightPalette.grey(14)),
            backgroundDisabled: Brush = SolidColor(IntUiLightPalette.grey(12)),
            backgroundFocused: Brush = background,
            backgroundPressed: Brush = SolidColor(IntUiLightPalette.grey(13)),
            backgroundHovered: Brush = background,
            content: Color = IntUiLightPalette.grey(1),
            contentDisabled: Color = IntUiLightPalette.grey(8),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            border: Color = IntUiLightPalette.grey(9),
            borderDisabled: Color = IntUiLightPalette.grey(6),
            borderFocused: Color = Color.Unspecified,
            borderPressed: Color = IntUiLightPalette.grey(7),
            borderHovered: Color = IntUiLightPalette.grey(8)
        ) = IntUiChipColors(
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
            background: Brush = SolidColor(IntUiDarkPalette.grey(2)),
            backgroundDisabled: Brush = SolidColor(IntUiDarkPalette.grey(5)),
            backgroundFocused: Brush = background,
            backgroundPressed: Brush = background,
            backgroundHovered: Brush = background,
            content: Color = IntUiDarkPalette.grey(12),
            contentDisabled: Color = IntUiDarkPalette.grey(8),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            border: Color = IntUiDarkPalette.grey(5),
            borderDisabled: Color = IntUiDarkPalette.grey(6),
            borderFocused: Color = Color.Unspecified,
            borderPressed: Color = IntUiDarkPalette.grey(7),
            borderHovered: Color = borderPressed
        ) = IntUiChipColors(
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
data class IntUiChipMetrics(
    override val minSize: DpSize = DpSize(80.dp, 40.dp),
    override val cornerSize: CornerSize = CornerSize(100),
    override val padding: PaddingValues = PaddingValues(4.dp),
    override val borderWidth: Dp = 1.dp
) : ChipMetrics

package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.styling.TooltipColors
import org.jetbrains.jewel.styling.TooltipMetrics
import org.jetbrains.jewel.styling.TooltipStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Stable
data class IntUiTooltipStyle(
    override val colors: IntUiTooltipColors,
    override val metrics: IntUiTooltipMetrics,
) : TooltipStyle {

    companion object {

        @Composable
        fun light(
            intUiTooltipColors: IntUiTooltipColors = IntUiTooltipColors.light(),
            intUiTooltipMetrics: IntUiTooltipMetrics = IntUiTooltipMetrics(),
        ): IntUiTooltipStyle = IntUiTooltipStyle(
            colors = intUiTooltipColors,
            metrics = intUiTooltipMetrics,
        )

        @Composable
        fun dark(
            intUiTooltipColors: IntUiTooltipColors = IntUiTooltipColors.dark(),
            intUiTooltipMetrics: IntUiTooltipMetrics = IntUiTooltipMetrics(),
        ): IntUiTooltipStyle = IntUiTooltipStyle(
            colors = intUiTooltipColors,
            metrics = intUiTooltipMetrics,
        )
    }
}

@Stable
data class IntUiTooltipColors(
    override val content: Color,
    override val background: Color,
    override val border: Color,
    override val shadow: Color,
) : TooltipColors {

    companion object {

        @Composable
        fun light(
            contentColor: Color = IntUiLightTheme.colors.grey(12),
            backgroundColor: Color = IntUiLightTheme.colors.grey(2),
            borderColor: Color = backgroundColor,
            shadow: Color = Color(0x78919191), // Not a palette color
        ) = IntUiTooltipColors(contentColor, backgroundColor, borderColor, shadow)

        @Composable
        fun dark(
            contentColor: Color = IntUiDarkTheme.colors.grey(12),
            backgroundColor: Color = IntUiDarkTheme.colors.grey(2),
            shadow: Color = Color(0x66000000), // Not a palette color
            borderColor: Color = IntUiDarkTheme.colors.grey(3),
        ) = IntUiTooltipColors(contentColor, backgroundColor, borderColor, shadow)
    }
}

@Stable
data class IntUiTooltipMetrics(
    override val contentPadding: PaddingValues = PaddingValues(vertical = 9.dp, horizontal = 12.dp),
    override val showDelay: Duration = 0.milliseconds,
    override val cornerSize: CornerSize = CornerSize(5.dp),
    override val borderWidth: Dp = 1.dp,
    override val shadowSize: Dp = 12.dp,
) : TooltipMetrics

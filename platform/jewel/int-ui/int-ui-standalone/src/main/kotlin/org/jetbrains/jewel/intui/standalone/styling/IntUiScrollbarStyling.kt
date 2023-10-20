package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GenerateDataFunctions
import org.jetbrains.jewel.styling.ScrollbarColors
import org.jetbrains.jewel.styling.ScrollbarMetrics
import org.jetbrains.jewel.styling.ScrollbarStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Stable
@GenerateDataFunctions
class IntUiScrollbarStyle(
    override val colors: IntUiScrollbarColors,
    override val metrics: IntUiScrollbarMetrics,
    override val hoverDuration: Duration,
) : ScrollbarStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiScrollbarColors = IntUiScrollbarColors.light(),
            metrics: IntUiScrollbarMetrics = IntUiScrollbarMetrics(),
            hoverDuration: Duration = 300.milliseconds,
        ) = IntUiScrollbarStyle(colors, metrics, hoverDuration)

        @Composable
        fun dark(
            colors: IntUiScrollbarColors = IntUiScrollbarColors.dark(),
            metrics: IntUiScrollbarMetrics = IntUiScrollbarMetrics(),
            hoverDuration: Duration = 300.milliseconds,
        ) = IntUiScrollbarStyle(colors, metrics, hoverDuration)
    }
}

@Immutable
@GenerateDataFunctions
class IntUiScrollbarColors(
    override val thumbBackground: Color,
    override val thumbBackgroundHovered: Color,
) : ScrollbarColors {

    companion object {

        @Composable
        fun light(
            thumbBackground: Color = Color(0x33000000),
            thumbBackgroundHovered: Color = Color(0x80000000),
        ) = IntUiScrollbarColors(thumbBackground, thumbBackgroundHovered)

        @Composable
        fun dark(
            thumbBackground: Color = Color(0x59808080),
            thumbBackgroundHovered: Color = Color(0x8C808080),
        ) = IntUiScrollbarColors(thumbBackground, thumbBackgroundHovered)
    }
}

@Stable
@GenerateDataFunctions
class IntUiScrollbarMetrics(
    override val thumbCornerSize: CornerSize = CornerSize(100),
    override val thumbThickness: Dp = 8.dp,
    override val minThumbLength: Dp = 16.dp,
    override val trackPadding: PaddingValues = PaddingValues(start = 7.dp, end = 3.dp),
) : ScrollbarMetrics

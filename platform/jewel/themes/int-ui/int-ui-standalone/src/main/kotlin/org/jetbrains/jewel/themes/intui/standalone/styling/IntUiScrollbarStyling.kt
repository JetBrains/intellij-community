package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.styling.ScrollbarColors
import org.jetbrains.jewel.styling.ScrollbarMetrics
import org.jetbrains.jewel.styling.ScrollbarStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Stable
data class IntUiScrollbarStyle(
    override val colors: IntUiScrollbarColors,
    override val metrics: IntUiScrollbarMetrics,
    override val hoverDuration: Duration
) : ScrollbarStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiScrollbarColors = IntUiScrollbarColors.light(),
            metrics: IntUiScrollbarMetrics = IntUiScrollbarMetrics(),
            hoverDuration: Duration = 300.milliseconds
        ) = IntUiScrollbarStyle(colors, metrics, hoverDuration)

        @Composable
        fun dark(
            colors: IntUiScrollbarColors = IntUiScrollbarColors.dark(),
            metrics: IntUiScrollbarMetrics = IntUiScrollbarMetrics(),
            hoverDuration: Duration = 300.milliseconds
        ) = IntUiScrollbarStyle(colors, metrics, hoverDuration)
    }
}

@Immutable
data class IntUiScrollbarColors(
    override val thumbBackground: Color,
    override val thumbBackgroundHovered: Color
) : ScrollbarColors {

    companion object {

        @Composable
        fun light(
            thumbBackground: Color = Color(0xFFD9D9D9),
            thumbBackgroundHovered: Color = Color(0xFF7B7C7D)
        ) = IntUiScrollbarColors(thumbBackground, thumbBackgroundHovered)

        @Composable
        fun dark(
            thumbBackground: Color = Color(0xFF48494B),
            thumbBackgroundHovered: Color = Color(0xFF595A5C)
        ) = IntUiScrollbarColors(thumbBackground, thumbBackgroundHovered)
    }
}

@Stable
data class IntUiScrollbarMetrics(
    override val thumbCornerSize: CornerSize = CornerSize(100),
    override val thumbThickness: Dp = 8.dp,
    override val minThumbLength: Dp = 16.dp,
    override val trackPadding: PaddingValues = PaddingValues(4.dp)
) : ScrollbarMetrics

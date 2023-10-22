package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Stable
@GenerateDataFunctions
class TooltipStyle(
    val colors: TooltipColors,
    val metrics: TooltipMetrics,
) {

    companion object
}

@Stable
@GenerateDataFunctions
class TooltipColors(
    val background: Color,
    val content: Color,
    val border: Color,
    val shadow: Color,
) {

    companion object
}

@Stable
@GenerateDataFunctions
class TooltipMetrics(
    val contentPadding: PaddingValues,
    val showDelay: Duration,
    val cornerSize: CornerSize,
    val borderWidth: Dp,
    val shadowSize: Dp,
    val tooltipOffset: DpOffset,
    val tooltipAlignment: Alignment.Horizontal,
) {

    companion object {

        fun defaults(
            contentPadding: PaddingValues = PaddingValues(vertical = 9.dp, horizontal = 12.dp),
            showDelay: Duration = 0.milliseconds,
            cornerSize: CornerSize = CornerSize(5.dp),
            borderWidth: Dp = 1.dp,
            shadowSize: Dp = 12.dp,
            tooltipOffset: DpOffset = DpOffset(0.dp, 20.dp),
            tooltipAlignment: Alignment.Horizontal = Alignment.Start,
        ) = TooltipMetrics(
            contentPadding,
            showDelay,
            cornerSize,
            borderWidth,
            shadowSize,
            tooltipOffset,
            tooltipAlignment,
        )
    }
}

val LocalTooltipStyle = staticCompositionLocalOf<TooltipStyle> {
    error("No TooltipStyle provided")
}

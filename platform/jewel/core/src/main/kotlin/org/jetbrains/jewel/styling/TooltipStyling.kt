package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.jewel.GenerateDataFunctions
import kotlin.time.Duration

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

    companion object
}

val LocalTooltipStyle = staticCompositionLocalOf<TooltipStyle> {
    error("No TooltipStyle provided")
}

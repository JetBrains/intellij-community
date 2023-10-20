package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.GenerateDataFunctions
import kotlin.time.Duration

@Stable
@GenerateDataFunctions
class ScrollbarStyle(
    val colors: ScrollbarColors,
    val metrics: ScrollbarMetrics,
    val hoverDuration: Duration,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class ScrollbarColors(
    val thumbBackground: Color,
    val thumbBackgroundHovered: Color,
) {

    companion object
}

@Stable
@GenerateDataFunctions
class ScrollbarMetrics(
    val thumbCornerSize: CornerSize,
    val thumbThickness: Dp,
    val minThumbLength: Dp,
    val trackPadding: PaddingValues,
) {

    companion object
}

val LocalScrollbarStyle = staticCompositionLocalOf<ScrollbarStyle> {
    error("No ScrollbarStyle provided")
}

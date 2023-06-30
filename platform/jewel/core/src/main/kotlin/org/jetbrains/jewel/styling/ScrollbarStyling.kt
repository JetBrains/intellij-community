package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.time.Duration

@Stable
interface ScrollbarStyle {

    val colors: ScrollbarColors
    val metrics: ScrollbarMetrics
    val hoverDuration: Duration
}

@Immutable
interface ScrollbarColors {

    val thumbBackground: Color
    val thumbBackgroundHovered: Color
}

@Stable
interface ScrollbarMetrics {

    val thumbCornerSize: CornerSize
    val thumbThickness: Dp
    val minThumbLength: Dp
    val trackPadding: PaddingValues
}

val LocalScrollbarStyle = staticCompositionLocalOf<ScrollbarStyle> {
    error("No ScrollbarStyle provided")
}

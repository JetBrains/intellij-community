package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.time.Duration

@Stable
interface TooltipStyle {

    val colors: TooltipColors
    val metrics: TooltipMetrics
}

@Stable
interface TooltipColors {

    val background: Color
    val content: Color
    val border: Color
    val shadow: Color
}

@Stable
interface TooltipMetrics {

    val contentPadding: PaddingValues
    val showDelay: Duration
    val cornerSize: CornerSize
    val borderWidth: Dp
    val shadowSize: Dp
}

val LocalTooltipStyle = staticCompositionLocalOf<TooltipStyle> {
    error("No TooltipStyle provided")
}

package org.jetbrains.jewel.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.time.Duration

@Immutable
interface HorizontalProgressBarStyle {

    val colors: HorizontalProgressBarColors
    val metrics: HorizontalProgressBarMetrics
    val indeterminateCycleDuration: Duration
}

@Immutable
interface HorizontalProgressBarColors {

    val track: Color
    val progress: Color
    val indeterminateBase: Color
    val indeterminateHighlight: Color
}

@Immutable
interface HorizontalProgressBarMetrics {

    val cornerSize: CornerSize
    val minHeight: Dp
    val indeterminateHighlightWidth: Dp
}

val LocalHorizontalProgressBarStyle = staticCompositionLocalOf<HorizontalProgressBarStyle> {
    error("No HorizontalProgressBarStyle provided")
}

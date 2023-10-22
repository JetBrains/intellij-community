package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import kotlin.time.Duration

@Immutable
@GenerateDataFunctions
class HorizontalProgressBarStyle(
    val colors: HorizontalProgressBarColors,
    val metrics: HorizontalProgressBarMetrics,
    val indeterminateCycleDuration: Duration,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class HorizontalProgressBarColors(
    val track: Color,
    val progress: Color,
    val indeterminateBase: Color,
    val indeterminateHighlight: Color,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class HorizontalProgressBarMetrics(
    val cornerSize: CornerSize,
    val minHeight: Dp,
    val indeterminateHighlightWidth: Dp,
) {

    companion object
}

val LocalHorizontalProgressBarStyle = staticCompositionLocalOf<HorizontalProgressBarStyle> {
    error("No HorizontalProgressBarStyle provided")
}

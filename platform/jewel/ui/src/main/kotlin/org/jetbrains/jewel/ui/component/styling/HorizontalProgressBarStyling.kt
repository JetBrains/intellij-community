package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.time.Duration
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarStyle(
    public val colors: HorizontalProgressBarColors,
    public val metrics: HorizontalProgressBarMetrics,
    public val indeterminateCycleDuration: Duration,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarColors(
    public val track: Color,
    public val progress: Color,
    public val indeterminateBase: Color,
    public val indeterminateHighlight: Color,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarMetrics(
    public val cornerSize: CornerSize,
    public val minHeight: Dp,
    public val indeterminateHighlightWidth: Dp,
) {
    public companion object
}

public val LocalHorizontalProgressBarStyle: ProvidableCompositionLocal<HorizontalProgressBarStyle> =
    staticCompositionLocalOf<HorizontalProgressBarStyle> {
        error("No HorizontalProgressBarStyle provided. Have you forgotten the theme?")
    }

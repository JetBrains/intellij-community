package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration

/** Holds styling properties for the circular progress indicator, including frame duration and color. */
@Immutable
public class CircularProgressStyle(
    /** The duration of each animation frame. */
    public val frameTime: Duration,
    /** The color of the progress indicator. */
    public val color: Color,
) {
    /** Companion object for [CircularProgressStyle]. */
    public companion object
}

/** CompositionLocal providing the current [CircularProgressStyle]. */
public val LocalCircularProgressStyle: ProvidableCompositionLocal<CircularProgressStyle> = staticCompositionLocalOf {
    error("No CircularProgressStyle provided. Have you forgotten the theme?")
}

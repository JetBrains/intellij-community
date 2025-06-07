package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration

@Immutable
public class CircularProgressStyle(public val frameTime: Duration, public val color: Color) {
    public companion object
}

public val LocalCircularProgressStyle: ProvidableCompositionLocal<CircularProgressStyle> = staticCompositionLocalOf {
    error("No CircularProgressStyle provided. Have you forgotten the theme?")
}

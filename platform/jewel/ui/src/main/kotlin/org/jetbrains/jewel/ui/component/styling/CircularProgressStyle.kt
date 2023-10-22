package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration

@Immutable
class CircularProgressStyle(
    val frameTime: Duration,
    val color: Color,
) {

    companion object
}

val LocalCircularProgressStyle = staticCompositionLocalOf<CircularProgressStyle> {
    error("No CircularProgressStyle provided")
}

package org.jetbrains.jewel.styling

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration

interface CircularProgressStyle {

    val frameTime: Duration
    val color: Color
}

val LocalCircularProgressStyle = staticCompositionLocalOf<CircularProgressStyle> {
    error("No CircularProgressIndicatorStyle provided")
}

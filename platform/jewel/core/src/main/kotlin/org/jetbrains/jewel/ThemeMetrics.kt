package org.jetbrains.jewel

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

@Immutable
interface ThemeMetrics {

    val outlineWidth: Dp
    val outlineCornerSize: CornerSize
}

val LocalThemeMetrics = staticCompositionLocalOf<ThemeMetrics> {
    error("No ThemeMetrics provided")
}

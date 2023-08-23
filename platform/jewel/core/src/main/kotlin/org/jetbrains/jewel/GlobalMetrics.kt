package org.jetbrains.jewel

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

@Immutable
interface GlobalMetrics {

    val outlineWidth: Dp
    val outlineCornerSize: CornerSize
}

val LocalGlobalMetrics = staticCompositionLocalOf<GlobalMetrics> {
    error("No GlobalMetrics provided")
}

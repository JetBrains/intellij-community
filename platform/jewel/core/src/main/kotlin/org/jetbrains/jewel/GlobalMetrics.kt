package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

@Immutable
interface GlobalMetrics {

    val outlineWidth: Dp
    val rowHeight: Dp
}

val LocalGlobalMetrics = staticCompositionLocalOf<GlobalMetrics> {
    error("No GlobalMetrics provided")
}

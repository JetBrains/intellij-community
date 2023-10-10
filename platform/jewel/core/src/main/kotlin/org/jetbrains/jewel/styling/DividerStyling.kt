package org.jetbrains.jewel.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Immutable
interface DividerStyle {

    val color: Color
    val metrics: DividerMetrics
}

@Immutable
interface DividerMetrics {

    val thickness: Dp
    val startIndent: Dp
}

val LocalDividerStyle = staticCompositionLocalOf<DividerStyle> {
    error("No DividerStyle provided")
}

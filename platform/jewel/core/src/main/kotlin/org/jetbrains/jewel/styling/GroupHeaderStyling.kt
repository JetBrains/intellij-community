package org.jetbrains.jewel.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Immutable
interface GroupHeaderStyle {

    val colors: GroupHeaderColors
    val metrics: GroupHeaderMetrics
}

@Immutable
interface GroupHeaderColors {

    val content: Color
    val divider: Color
}

@Immutable
interface GroupHeaderMetrics {

    val dividerThickness: Dp
    val indent: Dp
}

val LocalGroupHeaderStyle = staticCompositionLocalOf<GroupHeaderStyle> {
    error("No GroupHeaderStyle provided")
}

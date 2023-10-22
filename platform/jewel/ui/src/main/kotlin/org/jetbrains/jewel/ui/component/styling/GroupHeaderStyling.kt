package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Immutable
@GenerateDataFunctions
class GroupHeaderStyle(
    val colors: GroupHeaderColors,
    val metrics: GroupHeaderMetrics,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class GroupHeaderColors(val divider: Color) {

    companion object
}

@Immutable
@GenerateDataFunctions
class GroupHeaderMetrics(
    val dividerThickness: Dp,
    val indent: Dp,
) {

    companion object
}

val LocalGroupHeaderStyle = staticCompositionLocalOf<GroupHeaderStyle> {
    error("No GroupHeaderStyle provided")
}

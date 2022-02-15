package org.jetbrains.jewel.theme.toolbox

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided

class ToolboxMetrics(val base: Dp = 8.dp) {

    val smallPadding = base
    val mediumPadding = base * 2
    val largePadding = base * 3
    val cornerSize = CornerSize(base * 3)
    val adornmentsThickness = base / 4
}

val LocalMetrics = compositionLocalOf<ToolboxMetrics> { localNotProvided() }
val Styles.metrics: ToolboxMetrics
    @Composable
    @ReadOnlyComposable
    get() = LocalMetrics.current

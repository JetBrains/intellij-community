package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Immutable
@GenerateDataFunctions
class DividerStyle(
    val color: Color,
    val metrics: DividerMetrics,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class DividerMetrics(
    val thickness: Dp,
    val startIndent: Dp,
) {

    companion object {

        fun defaults(
            thickness: Dp = 1.dp,
            startIndent: Dp = 0.dp,
        ) = DividerMetrics(thickness, startIndent)
    }
}

val LocalDividerStyle = staticCompositionLocalOf<DividerStyle> {
    error("No DividerStyle provided")
}

package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.window.DecoratedWindowState

@Immutable
@GenerateDataFunctions
class DecoratedWindowStyle(
    val colors: DecoratedWindowColors,
    val metrics: DecoratedWindowMetrics,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class DecoratedWindowColors(
    val border: Color,
    val borderInactive: Color,
) {

    @Composable
    fun borderFor(state: DecoratedWindowState) = rememberUpdatedState(
        when {
            !state.isActive -> borderInactive
            else -> border
        },
    )

    companion object
}

@Immutable
@GenerateDataFunctions
class DecoratedWindowMetrics(val borderWidth: Dp) {

    companion object
}

val LocalDecoratedWindowStyle = staticCompositionLocalOf<DecoratedWindowStyle> {
    error("No DecoratedWindowStyle provided")
}

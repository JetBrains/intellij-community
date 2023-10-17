package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.window.DecoratedWindowState

interface DecoratedWindowStyle {

    val colors: DecoratedWindowColors
    val metrics: DecoratedWindowMetrics
}

@Stable
interface DecoratedWindowColors {

    val border: Color
    val borderInactive: Color

    @Composable
    fun borderFor(state: DecoratedWindowState) = rememberUpdatedState(
        when {
            !state.isActive -> borderInactive
            else -> border
        },
    )
}

@Stable
interface DecoratedWindowMetrics {

    val borderWidth: Dp
}

val LocalDecoratedWindowStyle = staticCompositionLocalOf<DecoratedWindowStyle> {
    error("No DecoratedWindowStyle provided")
}

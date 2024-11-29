package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.window.DecoratedWindowState

@Immutable
@GenerateDataFunctions
public class DecoratedWindowStyle(
    public val colors: DecoratedWindowColors,
    public val metrics: DecoratedWindowMetrics,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class DecoratedWindowColors(public val border: Color, public val borderInactive: Color) {
    @Composable
    public fun borderFor(state: DecoratedWindowState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isActive -> borderInactive
                else -> border
            }
        )

    public companion object
}

@Immutable
@GenerateDataFunctions
public class DecoratedWindowMetrics(public val borderWidth: Dp) {
    public companion object
}

public val LocalDecoratedWindowStyle: ProvidableCompositionLocal<DecoratedWindowStyle> = staticCompositionLocalOf {
    error("No DecoratedWindowStyle provided. Have you forgotten the theme?")
}

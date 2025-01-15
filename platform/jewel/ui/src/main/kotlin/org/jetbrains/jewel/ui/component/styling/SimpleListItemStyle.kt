package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ListItemState

@GenerateDataFunctions
public class SimpleListItemStyle(public val colors: SimpleListItemColors, public val metrics: SimpleListItemMetrics) {
    public companion object
}

@Stable
@GenerateDataFunctions
public class SimpleListItemColors(
    public val background: Color,
    public val backgroundActive: Color,
    public val backgroundSelected: Color,
    public val backgroundSelectedActive: Color,
    public val content: Color,
    public val contentActive: Color,
    public val contentSelected: Color,
    public val contentSelectedActive: Color,
) {
    @Composable
    public fun contentFor(state: ListItemState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected && state.isActive -> contentSelectedActive
                state.isSelected && !state.isActive -> contentSelected
                state.isActive -> contentActive
                else -> content
            }
        )

    @Composable
    public fun backgroundFor(state: ListItemState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected && state.isActive -> backgroundSelectedActive
                state.isSelected && !state.isActive -> backgroundSelected
                state.isActive -> backgroundActive
                else -> background
            }
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class SimpleListItemMetrics(
    public val innerPadding: PaddingValues,
    public val outerPadding: PaddingValues,
    public val selectionBackgroundCornerSize: CornerSize,
    public val iconTextGap: Dp,
) {
    public companion object
}

public val LocalSimpleListItemStyleStyle: ProvidableCompositionLocal<SimpleListItemStyle> = staticCompositionLocalOf {
    error("No LocalSimpleListItemStyleStyle provided. Have you forgotten the theme?")
}

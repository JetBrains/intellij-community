package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.IconButtonState

@Stable
@GenerateDataFunctions
public class IconButtonStyle(
    public val colors: IconButtonColors,
    public val metrics: IconButtonMetrics,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class IconButtonColors(
    public val foregroundSelectedActivated: Color,
    public val background: Color,
    public val backgroundDisabled: Color,
    public val backgroundSelected: Color,
    public val backgroundSelectedActivated: Color,
    public val backgroundFocused: Color,
    public val backgroundPressed: Color,
    public val backgroundHovered: Color,
    public val border: Color,
    public val borderDisabled: Color,
    public val borderSelected: Color,
    public val borderSelectedActivated: Color,
    public val borderFocused: Color,
    public val borderPressed: Color,
    public val borderHovered: Color,
) {

    @Composable
    public fun foregroundFor(state: IconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                state.isActive && state.isSelected -> foregroundSelectedActivated
                else -> Color.Unspecified
            },
        )

    @Composable
    public fun backgroundFor(state: IconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isActive && state.isSelected -> backgroundSelectedActivated
                state.isSelected -> backgroundSelected
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                state.isFocused -> backgroundFocused
                else -> background
            },
        )

    @Composable
    public fun borderFor(state: IconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> borderDisabled
                state.isActive && state.isSelected -> borderSelectedActivated
                state.isSelected -> borderSelected
                state.isFocused -> borderFocused
                state.isPressed -> borderPressed
                state.isHovered -> borderHovered
                else -> border
            },
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class IconButtonMetrics(
    public val cornerSize: CornerSize,
    public val borderWidth: Dp,
    public val padding: PaddingValues,
    public val minSize: DpSize,
) {

    public companion object
}

public val LocalIconButtonStyle: ProvidableCompositionLocal<IconButtonStyle> =
    staticCompositionLocalOf {
        error("No IconButtonStyle provided. Have you forgotten the theme?")
    }

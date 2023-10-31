package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.IconButtonState

@Stable
@GenerateDataFunctions
class IconButtonStyle(
    val colors: IconButtonColors,
    val metrics: IconButtonMetrics,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class IconButtonColors(
    val foregroundSelectedActivated: Color,
    val background: Color,
    val backgroundDisabled: Color,
    val backgroundSelected: Color,
    val backgroundSelectedActivated: Color,
    val backgroundFocused: Color,
    val backgroundPressed: Color,
    val backgroundHovered: Color,
    val border: Color,
    val borderDisabled: Color,
    val borderSelected: Color,
    val borderSelectedActivated: Color,
    val borderFocused: Color,
    val borderPressed: Color,
    val borderHovered: Color,
) {

    @Composable
    fun foregroundFor(state: IconButtonState) = rememberUpdatedState(
        when {
            state.isActive && state.isSelected -> foregroundSelectedActivated
            else -> Color.Unspecified
        },
    )

    @Composable
    fun backgroundFor(state: IconButtonState) = rememberUpdatedState(
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
    fun borderFor(state: IconButtonState) = rememberUpdatedState(
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

    companion object
}

@Stable
@GenerateDataFunctions
class IconButtonMetrics(
    val cornerSize: CornerSize,
    val borderWidth: Dp,
    val padding: PaddingValues,
    val minSize: DpSize,
) {

    companion object
}

val LocalIconButtonStyle = staticCompositionLocalOf<IconButtonStyle> {
    error("No IconButtonStyle provided")
}

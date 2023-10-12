package org.jetbrains.jewel.styling

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
import org.jetbrains.jewel.ButtonState

@Stable
interface IconButtonStyle {

    val colors: IconButtonColors
    val metrics: IconButtonMetrics
}

@Immutable
interface IconButtonColors {

    val background: Color
    val backgroundDisabled: Color
    val backgroundFocused: Color
    val backgroundPressed: Color
    val backgroundHovered: Color

    val border: Color
    val borderDisabled: Color
    val borderFocused: Color
    val borderPressed: Color
    val borderHovered: Color

    @Composable
    fun backgroundFor(state: ButtonState) = rememberUpdatedState(
        when {
            !state.isEnabled -> backgroundDisabled
            state.isPressed -> backgroundPressed
            state.isHovered -> backgroundHovered
            state.isFocused -> backgroundFocused
            else -> background
        },
    )

    @Composable
    fun borderFor(state: ButtonState) = rememberUpdatedState(
        when {
            !state.isEnabled -> borderDisabled
            state.isPressed -> borderPressed
            state.isHovered -> borderHovered
            state.isFocused -> borderFocused
            else -> border
        },
    )
}

@Stable
interface IconButtonMetrics {

    val cornerSize: CornerSize
    val borderWidth: Dp
    val padding: PaddingValues
    val minSize: DpSize
}

val LocalIconButtonStyle = staticCompositionLocalOf<IconButtonStyle> {
    error("No IconButtonStyle provided")
}

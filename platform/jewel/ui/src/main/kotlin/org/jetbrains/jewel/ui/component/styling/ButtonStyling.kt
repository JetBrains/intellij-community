package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ButtonState

@Stable
@GenerateDataFunctions
class ButtonStyle(
    val colors: ButtonColors,
    val metrics: ButtonMetrics,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class ButtonColors(
    val background: Brush,
    val backgroundDisabled: Brush,
    val backgroundFocused: Brush,
    val backgroundPressed: Brush,
    val backgroundHovered: Brush,
    val content: Color,
    val contentDisabled: Color,
    val contentFocused: Color,
    val contentPressed: Color,
    val contentHovered: Color,
    val border: Brush,
    val borderDisabled: Brush,
    val borderFocused: Brush,
    val borderPressed: Brush,
    val borderHovered: Brush,
) {

    @Composable
    fun backgroundFor(state: ButtonState) = rememberUpdatedState(
        state.chooseValue(
            normal = background,
            disabled = backgroundDisabled,
            focused = backgroundFocused,
            pressed = backgroundPressed,
            hovered = backgroundHovered,
            active = background,
        ),
    )

    @Composable
    fun contentFor(state: ButtonState) = rememberUpdatedState(
        state.chooseValue(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered,
            active = content,
        ),
    )

    @Composable
    fun borderFor(state: ButtonState) = rememberUpdatedState(
        state.chooseValue(
            normal = border,
            disabled = borderDisabled,
            focused = borderFocused,
            pressed = borderPressed,
            hovered = borderHovered,
            active = border,
        ),
    )

    companion object
}

@Stable
@GenerateDataFunctions
class ButtonMetrics(
    val cornerSize: CornerSize,
    val padding: PaddingValues,
    val minSize: DpSize,
    val borderWidth: Dp,
) {

    companion object
}

val LocalDefaultButtonStyle = staticCompositionLocalOf<ButtonStyle> {
    error("No default ButtonStyle provided")
}

val LocalOutlinedButtonStyle = staticCompositionLocalOf<ButtonStyle> {
    error("No outlined ButtonStyle provided")
}

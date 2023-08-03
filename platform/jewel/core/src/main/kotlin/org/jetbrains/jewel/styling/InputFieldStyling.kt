package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.InputFieldState

@Stable
interface InputFieldStyle {

    val colors: InputFieldColors
    val metrics: InputFieldMetrics
    val textStyle: TextStyle
}

@Immutable
interface InputFieldColors {

    val background: Color
    val backgroundDisabled: Color
    val backgroundFocused: Color
    val backgroundPressed: Color
    val backgroundHovered: Color
    val backgroundWarning: Color
    val backgroundError: Color

    @Composable
    fun backgroundFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = background,
            disabled = backgroundDisabled,
            focused = backgroundFocused,
            pressed = backgroundPressed,
            hovered = backgroundHovered,
            warning = backgroundWarning,
            error = backgroundError,
            active = background
        )
    )

    val content: Color
    val contentDisabled: Color
    val contentFocused: Color
    val contentPressed: Color
    val contentHovered: Color
    val contentWarning: Color
    val contentError: Color

    @Composable
    fun contentFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered,
            warning = contentWarning,
            error = contentError,
            active = content
        )
    )

    val border: Color
    val borderDisabled: Color
    val borderFocused: Color
    val borderPressed: Color
    val borderHovered: Color
    val borderWarning: Color
    val borderError: Color

    @Composable
    fun borderFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = border,
            disabled = borderDisabled,
            focused = borderFocused,
            pressed = borderPressed,
            hovered = borderHovered,
            warning = borderWarning,
            error = borderError,
            active = border
        )
    )

    val cursor: Brush
    val cursorDisabled: Brush
    val cursorFocused: Brush
    val cursorPressed: Brush
    val cursorHovered: Brush
    val cursorWarning: Brush
    val cursorError: Brush

    @Composable
    fun cursorFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = cursor,
            disabled = cursorDisabled,
            focused = cursorFocused,
            pressed = cursorPressed,
            hovered = cursorHovered,
            warning = cursorWarning,
            error = cursorError,
            active = cursor
        )
    )
}

@Stable
interface InputFieldMetrics {

    val cornerSize: CornerSize
    val contentPadding: PaddingValues
    val minSize: DpSize
    val borderWidth: Dp
}

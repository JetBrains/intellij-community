package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.ui.component.InputFieldState

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

    @Composable
    fun backgroundFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValue(
            normal = background,
            disabled = backgroundDisabled,
            focused = backgroundFocused,
            pressed = backgroundPressed,
            hovered = backgroundHovered,
            active = background,
        ),
    )

    val content: Color
    val contentDisabled: Color
    val contentFocused: Color
    val contentPressed: Color
    val contentHovered: Color

    @Composable
    fun contentFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValue(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered,
            active = content,
        ),
    )

    val border: Color
    val borderDisabled: Color
    val borderFocused: Color
    val borderPressed: Color
    val borderHovered: Color

    @Composable
    fun borderFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValue(
            normal = border,
            disabled = borderDisabled,
            focused = borderFocused,
            pressed = borderPressed,
            hovered = borderHovered,
            active = border,
        ),
    )

    val caret: Color
    val caretDisabled: Color
    val caretFocused: Color
    val caretPressed: Color
    val caretHovered: Color

    @Composable
    fun caretFor(state: InputFieldState) = rememberUpdatedState(
        state.chooseValue(
            normal = caret,
            disabled = caretDisabled,
            focused = caretFocused,
            pressed = caretPressed,
            hovered = caretHovered,
            active = caret,
        ),
    )
}

@Stable
interface InputFieldMetrics {

    val cornerSize: CornerSize
    val contentPadding: PaddingValues
    val minSize: DpSize
    val borderWidth: Dp
}

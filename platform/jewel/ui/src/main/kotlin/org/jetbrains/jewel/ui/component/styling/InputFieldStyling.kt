package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.ui.component.InputFieldState

@Stable
public interface InputFieldStyle {
    public val colors: InputFieldColors
    public val metrics: InputFieldMetrics
}

@Immutable
public interface InputFieldColors {
    public val background: Color
    public val backgroundDisabled: Color
    public val backgroundFocused: Color
    public val backgroundPressed: Color
    public val backgroundHovered: Color

    @Composable
    public fun backgroundFor(state: InputFieldState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = background,
                disabled = backgroundDisabled,
                focused = backgroundFocused,
                pressed = backgroundPressed,
                hovered = backgroundHovered,
                active = background,
            )
        )

    public val content: Color
    public val contentDisabled: Color
    public val contentFocused: Color
    public val contentPressed: Color
    public val contentHovered: Color

    @Composable
    public fun contentFor(state: InputFieldState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = content,
                disabled = contentDisabled,
                focused = contentFocused,
                pressed = contentPressed,
                hovered = contentHovered,
                active = content,
            )
        )

    public val border: Color
    public val borderDisabled: Color
    public val borderFocused: Color
    public val borderPressed: Color
    public val borderHovered: Color

    @Composable
    public fun borderFor(state: InputFieldState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = border,
                disabled = borderDisabled,
                focused = borderFocused,
                pressed = borderPressed,
                hovered = borderHovered,
                active = border,
            )
        )

    public val caret: Color
    public val caretDisabled: Color
    public val caretFocused: Color
    public val caretPressed: Color
    public val caretHovered: Color

    @Composable
    public fun caretFor(state: InputFieldState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = caret,
                disabled = caretDisabled,
                focused = caretFocused,
                pressed = caretPressed,
                hovered = caretHovered,
                active = caret,
            )
        )
}

@Stable
public interface InputFieldMetrics {
    public val cornerSize: CornerSize
    public val contentPadding: PaddingValues
    public val minSize: DpSize
    public val borderWidth: Dp
}

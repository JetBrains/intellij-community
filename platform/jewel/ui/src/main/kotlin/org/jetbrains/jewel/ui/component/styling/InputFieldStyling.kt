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

/** Defines the overall style for an input field, combining colors and metrics. */
@Stable
public interface InputFieldStyle {
    /** The color tokens for this input field. */
    public val colors: InputFieldColors

    /** The size and spacing metrics for this input field. */
    public val metrics: InputFieldMetrics
}

/** Holds color tokens for an input field component in its various interaction states. */
@Immutable
public interface InputFieldColors {
    /** The background color in the normal state. */
    public val background: Color

    /** The background color when the input field is disabled. */
    public val backgroundDisabled: Color

    /** The background color when the input field is focused. */
    public val backgroundFocused: Color

    /** The background color when the input field is pressed. */
    public val backgroundPressed: Color

    /** The background color when the input field is hovered. */
    public val backgroundHovered: Color

    /** Returns a [State] holding the background color appropriate for the given [state]. */
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

    /** The content (text) color in the normal state. */
    public val content: Color

    /** The content (text) color when the input field is disabled. */
    public val contentDisabled: Color

    /** The content (text) color when the input field is focused. */
    public val contentFocused: Color

    /** The content (text) color when the input field is pressed. */
    public val contentPressed: Color

    /** The content (text) color when the input field is hovered. */
    public val contentHovered: Color

    /** Returns a [State] holding the content (text) color appropriate for the given [state]. */
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

    /** The border color in the normal state. */
    public val border: Color

    /** The border color when the input field is disabled. */
    public val borderDisabled: Color

    /** The border color when the input field is focused. */
    public val borderFocused: Color

    /** The border color when the input field is pressed. */
    public val borderPressed: Color

    /** The border color when the input field is hovered. */
    public val borderHovered: Color

    /** Returns a [State] holding the border color appropriate for the given [state]. */
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

    /** The caret color in the normal state. */
    public val caret: Color

    /** The caret color when the input field is disabled. */
    public val caretDisabled: Color

    /** The caret color when the input field is focused. */
    public val caretFocused: Color

    /** The caret color when the input field is pressed. */
    public val caretPressed: Color

    /** The caret color when the input field is hovered. */
    public val caretHovered: Color

    /** Returns a [State] holding the caret color appropriate for the given [state]. */
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

/** Holds size and spacing metrics for an input field component. */
@Stable
public interface InputFieldMetrics {
    /** The corner radius of the input field. */
    public val cornerSize: CornerSize

    /** The padding applied around the content inside the input field. */
    public val contentPadding: PaddingValues

    /** The minimum size of the input field. */
    public val minSize: DpSize

    /** The width of the input field border. */
    public val borderWidth: Dp
}

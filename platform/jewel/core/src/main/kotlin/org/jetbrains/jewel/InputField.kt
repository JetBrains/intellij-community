package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border
import org.jetbrains.jewel.styling.InputFieldStyle
import org.jetbrains.jewel.util.appendIf

@Composable
internal fun InputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    isError: Boolean,
    undecorated: Boolean,
    visualTransformation: VisualTransformation,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    singleLine: Boolean,
    maxLines: Int,
    onTextLayout: (TextLayoutResult) -> Unit,
    interactionSource: MutableInteractionSource,
    style: InputFieldStyle,
    textStyle: TextStyle,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit, state: InputFieldState) -> Unit,
) {
    var inputState by remember(interactionSource) {
        mutableStateOf(InputFieldState.of(enabled = enabled, error = isError))
    }
    remember(isError, enabled) {
        inputState = inputState.copy(error = isError, enabled = enabled)
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> inputState = inputState.copy(focused = true)

                is FocusInteraction.Unfocus -> inputState = inputState.copy(focused = false)
            }
        }
    }

    val colors = style.colors
    val backgroundColor by colors.backgroundFor(inputState)
    val shape = RoundedCornerShape(style.metrics.cornerSize)

    val backgroundModifier = Modifier.appendIf(!undecorated && backgroundColor.isSpecified) {
        background(backgroundColor, shape)
    }

    val borderColor by style.colors.borderFor(inputState)
    val borderModifier = Modifier.appendIf(!undecorated && borderColor.isSpecified) {
        Modifier.border(
            alignment = Stroke.Alignment.Outside,
            width = style.metrics.borderWidth,
            color = borderColor,
            shape = shape
        )
    }

    val contentColor by colors.contentFor(inputState)
    val mergedTextStyle = style.textStyle.merge(textStyle).copy(color = contentColor)
    val cursorBrush by colors.cursorFor(inputState)

    BasicTextField(
        value = value,
        modifier = modifier.then(backgroundModifier)
            .then(borderModifier),
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        cursorBrush = cursorBrush,
        visualTransformation = visualTransformation,
        onTextLayout = onTextLayout,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = @Composable { innerTextField: @Composable () -> Unit ->
            decorationBox(innerTextField, inputState)
        }
    )
}

@Immutable
@JvmInline
value class InputFieldState(val state: ULong) : StateWithOutline {

    @Stable
    override val isEnabled: Boolean
        get() = state and CommonStateBitMask.Enabled != 0UL

    @Stable
    override val isFocused: Boolean
        get() = state and CommonStateBitMask.Focused != 0UL

    @Stable
    override val isError: Boolean
        get() = state and CommonStateBitMask.Error != 0UL

    @Stable
    override val isWarning: Boolean
        get() = state and CommonStateBitMask.Warning != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and CommonStateBitMask.Hovered != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and CommonStateBitMask.Pressed != 0UL

    fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        error: Boolean = isError,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        warning: Boolean = isWarning,
    ) = of(
        enabled = enabled,
        focused = focused,
        error = error,
        pressed = pressed,
        hovered = hovered,
        warning = warning
    )

    override fun toString() =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isError=$isError, isWarning=$isWarning, " +
            "isHovered=$isHovered, isPressed=$isPressed)"

    companion object {

        fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            error: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            warning: Boolean = false,
        ) = InputFieldState(
            state = (if (enabled) CommonStateBitMask.Enabled else 0UL) or
                (if (focused) CommonStateBitMask.Focused else 0UL) or
                (if (error) CommonStateBitMask.Error else 0UL) or
                (if (hovered) CommonStateBitMask.Hovered else 0UL) or
                (if (pressed) CommonStateBitMask.Pressed else 0UL) or
                (if (warning) CommonStateBitMask.Warning else 0UL)
        )
    }
}

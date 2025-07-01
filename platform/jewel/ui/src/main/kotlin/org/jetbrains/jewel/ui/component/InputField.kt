package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.InputFieldStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.outline

@Composable
internal fun InputField(
    state: TextFieldState,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    inputTransformation: InputTransformation?,
    textStyle: TextStyle,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
    lineLimits: TextFieldLineLimits,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)?,
    interactionSource: MutableInteractionSource,
    style: InputFieldStyle,
    outline: Outline,
    outputTransformation: OutputTransformation?,
    decorator: TextFieldDecorator?,
    undecorated: Boolean,
    scrollState: ScrollState,
) {
    var inputFieldState by remember(interactionSource) { mutableStateOf(InputFieldState.of(enabled = enabled)) }
    remember(enabled) { inputFieldState = inputFieldState.copy(enabled = enabled) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> inputFieldState = inputFieldState.copy(focused = true)
                is FocusInteraction.Unfocus -> inputFieldState = inputFieldState.copy(focused = false)
            }
        }
    }

    val colors = style.colors
    val backgroundColor by colors.backgroundFor(inputFieldState)
    val shape = RoundedCornerShape(style.metrics.cornerSize)

    val backgroundModifier =
        Modifier.thenIf(!undecorated && backgroundColor.isSpecified) { background(backgroundColor, shape) }

    val borderColor by style.colors.borderFor(inputFieldState)
    val hasNoOutline = outline == Outline.None
    val borderModifier =
        Modifier.thenIf(!undecorated && borderColor.isSpecified && hasNoOutline) {
            border(
                alignment = Stroke.Alignment.Inside,
                width = style.metrics.borderWidth,
                color = borderColor,
                shape = shape,
            )
        }

    val contentColor by colors.contentFor(inputFieldState)
    val mergedTextStyle = textStyle.copy(color = contentColor)
    val caretColor by colors.caretFor(inputFieldState)

    BasicTextField(
        state = state,
        modifier =
            modifier
                .then(backgroundModifier)
                .thenIf(!undecorated && hasNoOutline) {
                    focusOutline(state = inputFieldState, outlineShape = shape, alignment = Stroke.Alignment.Center)
                }
                .then(borderModifier)
                .outline(inputFieldState, outline, shape, Stroke.Alignment.Center),
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = inputTransformation,
        textStyle = mergedTextStyle,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(caretColor),
        outputTransformation = outputTransformation,
        decorator = decorator,
        scrollState = scrollState,
    )
}

@Composable
internal fun InputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    outline: Outline,
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
    var inputState by remember(interactionSource) { mutableStateOf(InputFieldState.of(enabled = enabled)) }
    remember(enabled) { inputState = inputState.copy(enabled = enabled) }

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

    val backgroundModifier =
        Modifier.thenIf(!undecorated && backgroundColor.isSpecified) { background(backgroundColor, shape) }

    val borderColor by style.colors.borderFor(inputState)
    val hasNoOutline = outline == Outline.None
    val borderModifier =
        Modifier.thenIf(!undecorated && borderColor.isSpecified && hasNoOutline) {
            border(
                alignment = Stroke.Alignment.Center,
                width = style.metrics.borderWidth,
                color = borderColor,
                shape = shape,
            )
        }

    val contentColor by colors.contentFor(inputState)
    val mergedTextStyle = textStyle.copy(color = contentColor)
    val caretColor by colors.caretFor(inputState)

    BasicTextField(
        value = value,
        modifier =
            modifier
                .then(backgroundModifier)
                .then(borderModifier)
                .thenIf(!undecorated && hasNoOutline) { focusOutline(inputState, shape) }
                .outline(inputState, outline, shape, Stroke.Alignment.Center),
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        cursorBrush = SolidColor(caretColor),
        visualTransformation = visualTransformation,
        onTextLayout = onTextLayout,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox =
            @Composable { innerTextField: @Composable () -> Unit -> decorationBox(innerTextField, inputState) },
    )
}

@Immutable
@JvmInline
public value class InputFieldState(public val state: ULong) : FocusableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): InputFieldState = of(enabled = enabled, focused = focused, pressed = pressed, hovered = hovered, active = active)

    override fun toString(): String =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): InputFieldState =
            InputFieldState(
                state =
                    (if (enabled) Enabled else 0UL) or
                        (if (focused) Focused else 0UL) or
                        (if (hovered) Hovered else 0UL) or
                        (if (pressed) Pressed else 0UL) or
                        (if (active) Active else 0UL)
            )
    }
}

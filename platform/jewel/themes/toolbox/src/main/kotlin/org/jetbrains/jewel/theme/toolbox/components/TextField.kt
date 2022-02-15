package org.jetbrains.jewel.theme.toolbox.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.flow.collect
import org.jetbrains.jewel.components.state.TextFieldState
import org.jetbrains.jewel.shape
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalTextFieldStyle
import org.jetbrains.jewel.theme.toolbox.styles.TextFieldStyle

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: TextFieldStyle = LocalTextFieldStyle.current,
    variation: Any? = null,
) {
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)

    TextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValueState = it
            if (value != it.text) {
                onValueChange(it.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        style = style,
        variation = variation
    )
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: TextFieldStyle = LocalTextFieldStyle.current,
    variation: Any? = null,
) {
    var inputState by remember(interactionSource, enabled) { mutableStateOf(TextFieldState(enabled)) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> inputState = inputState.copy(focused = true)
                is FocusInteraction.Unfocus -> inputState = inputState.copy(focused = false)
            }
        }
    }

    val appearance = style.appearance(inputState, variation)

    val shapeModifier = if (appearance.shapeStroke != null || appearance.backgroundColor != Color.Unspecified)
        Modifier.shape(appearance.shape, appearance.shapeStroke, appearance.backgroundColor)
    else
        Modifier
    val adornmentModifier = if (appearance.adornmentStroke != null && appearance.adornmentShape != null)
        Modifier.shape(appearance.adornmentShape, appearance.adornmentStroke)
    else
        Modifier

    val haloStroke = appearance.haloStroke
    val haloModifier = when {
        haloStroke != null -> Modifier.drawBehind {
            val outline = appearance.haloShape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = haloStroke.brush,
                style = Stroke(haloStroke.width.toPx()),
            )
        }
        else -> Modifier
    }

    @OptIn(ExperimentalComposeUiApi::class)
    val pointerModifier = when {
        enabled -> Modifier.pointerMoveFilter(
            onEnter = {
                inputState = inputState.copy(hovered = true)
                false
            },
            onExit = {
                inputState = inputState.copy(hovered = false)
                false
            })
        else -> Modifier
    }

    BasicTextField(
        value = value,
        modifier = modifier
            .focusable(enabled, interactionSource)
            .defaultMinSize(appearance.minWidth, appearance.minHeight)
            .then(pointerModifier)
            .then(haloModifier)
            .then(adornmentModifier)
            .then(shapeModifier),
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = LocalTextStyle.current.merge(appearance.textStyle),
        cursorBrush = appearance.cursorBrush,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = @Composable { coreTextField ->
            Box(Modifier.padding(appearance.contentPadding)) {
                coreTextField()
            }
        }
    )
}

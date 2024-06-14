package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.theme.textAreaStyle

/**
 * @param placeholder the optional placeholder to be displayed over the
 *     component when the [value] is empty.
 */
@Composable
public fun TextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    outline: Outline = Outline.None,
    placeholder: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextAreaStyle = JewelTheme.textAreaStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)
    var lastTextValue by remember(value) { mutableStateOf(value) }

    TextArea(
        value = textFieldValue,
        onValueChange = { newTextFieldValueState ->
            textFieldValueState = newTextFieldValueState

            val stringChangedSinceLastInvocation = lastTextValue != newTextFieldValueState.text
            lastTextValue = newTextFieldValueState.text

            if (stringChangedSinceLastInvocation) {
                onValueChange(newTextFieldValueState.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        outline = outline,
        placeholder = placeholder,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
        textStyle = textStyle,
        interactionSource = interactionSource,
    )
}

/**
 * @param placeholder the optional placeholder to be displayed over the
 *     component when the [value] is empty.
 */
@Composable
public fun TextArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    outline: Outline = Outline.None,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextAreaStyle = JewelTheme.textAreaStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val minSize = style.metrics.minSize
    InputField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minWidth = minSize.width, minHeight = minSize.height),
        enabled = enabled,
        readOnly = readOnly,
        outline = outline,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = false,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
        textStyle = textStyle,
        interactionSource = interactionSource,
    ) { innerTextField, _ ->
        TextAreaDecorationBox(
            innerTextField = innerTextField,
            contentPadding = style.metrics.contentPadding,
            placeholderTextColor = style.colors.placeholder,
            placeholder = if (value.text.isEmpty()) placeholder else null,
            textStyle = textStyle,
        )
    }
}

@Composable
private fun TextAreaDecorationBox(
    innerTextField: @Composable () -> Unit,
    contentPadding: PaddingValues,
    textStyle: TextStyle,
    placeholderTextColor: Color,
    placeholder: @Composable (() -> Unit)?,
) {
    Layout(
        content = {
            if (placeholder != null) {
                Box(
                    modifier = Modifier.layoutId(PLACEHOLDER_ID),
                    contentAlignment = Alignment.TopStart,
                ) {
                    CompositionLocalProvider(
                        LocalTextStyle provides textStyle.copy(color = placeholderTextColor),
                        LocalContentColor provides placeholderTextColor,
                        content = placeholder,
                    )
                }
            }

            Box(
                modifier = Modifier.layoutId(TEXT_AREA_ID),
                contentAlignment = Alignment.TopStart,
                propagateMinConstraints = true,
            ) {
                innerTextField()
            }
        },
    ) { measurables, incomingConstraints ->
        val leftPadding = contentPadding.calculateLeftPadding(layoutDirection)
        val rightPadding = contentPadding.calculateRightPadding(layoutDirection)
        val horizontalPadding = (leftPadding + rightPadding).roundToPx()
        val verticalPadding =
            (contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding())
                .roundToPx()

        val textAreaConstraints =
            incomingConstraints
                .offset(horizontal = -horizontalPadding, vertical = -verticalPadding)
                .copy(minHeight = 0)

        val textAreaPlaceable =
            measurables.single { it.layoutId == TEXT_AREA_ID }
                .measure(textAreaConstraints)

        // Measure placeholder
        val placeholderConstraints = textAreaConstraints.copy(minWidth = 0, minHeight = 0)
        val placeholderPlaceable =
            measurables.find { it.layoutId == PLACEHOLDER_ID }
                ?.measure(placeholderConstraints)

        val width = calculateWidth(textAreaPlaceable, placeholderPlaceable, incomingConstraints)
        val height = calculateHeight(textAreaPlaceable, placeholderPlaceable, verticalPadding, incomingConstraints)

        layout(width, height) {
            val startPadding = contentPadding.calculateStartPadding(layoutDirection).roundToPx()
            val topPadding = contentPadding.calculateTopPadding().roundToPx()

            // Placed top-start
            textAreaPlaceable.placeRelative(startPadding, topPadding)

            // Placed similar to the input text above
            placeholderPlaceable?.placeRelative(startPadding, topPadding)
        }
    }
}

private fun calculateWidth(
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    incomingConstraints: Constraints,
): Int =
    maxOf(textFieldPlaceable.width, placeholderPlaceable?.width ?: 0)
        .coerceAtLeast(incomingConstraints.minWidth)

private fun calculateHeight(
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    verticalPadding: Int,
    incomingConstraints: Constraints,
): Int {
    val textAreaHeight = maxOf(textFieldPlaceable.height, placeholderPlaceable?.height ?: 0)
    return (textAreaHeight + verticalPadding).coerceAtLeast(incomingConstraints.minHeight)
}

private const val PLACEHOLDER_ID = "Placeholder"
private const val TEXT_AREA_ID = "TextField"

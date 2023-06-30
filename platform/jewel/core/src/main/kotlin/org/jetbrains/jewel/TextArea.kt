package org.jetbrains.jewel

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset
import org.jetbrains.jewel.styling.TextAreaStyle
import kotlin.math.max

@Composable
fun TextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    hint: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextAreaStyle = IntelliJTheme.textAreaStyle,
    textStyle: TextStyle = IntelliJTheme.defaultTextStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
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
        isError = isError,
        placeholder = placeholder,
        hint = hint,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
        textStyle = textStyle,
        interactionSource = interactionSource
    )
}

@Composable
fun TextArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    hint: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextAreaStyle = IntelliJTheme.textAreaStyle,
    textStyle: TextStyle = IntelliJTheme.defaultTextStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    InputField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = false,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
        textStyle = textStyle,
        interactionSource = interactionSource
    ) { innerTextField, state ->
        val minSize = style.metrics.minSize

        TextAreaDecorationBox(
            modifier = Modifier
                .defaultMinSize(minWidth = minSize.width, minHeight = minSize.height),
            innerTextField = innerTextField,
            contentPadding = style.metrics.contentPadding,
            placeholderTextColor = style.colors.placeholder,
            placeholder = if (value.text.isEmpty()) placeholder else null,
            hintTextStyle = style.hintTextStyle,
            hintTextColor = style.colors.hintContentFor(state).value,
            hint = hint
        )
    }
}

@Composable
private fun TextAreaDecorationBox(
    modifier: Modifier = Modifier,
    innerTextField: @Composable () -> Unit,
    contentPadding: PaddingValues,
    placeholderTextColor: Color,
    placeholder: @Composable (() -> Unit)?,
    hintTextStyle: TextStyle,
    hintTextColor: Color,
    hint: @Composable (() -> Unit)?
) {
    Layout(
        modifier = modifier,
        content = {
            if (placeholder != null) {
                Box(modifier = Modifier.layoutId(PLACEHOLDER_ID), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalContentColor provides placeholderTextColor,
                        content = placeholder
                    )
                }
            }

            Box(modifier = Modifier.layoutId(TEXT_FIELD_ID), propagateMinConstraints = true) {
                innerTextField()
            }

            if (hint != null) {
                Box(Modifier.layoutId(HINT_ID).fillMaxWidth()) {
                    CompositionLocalProvider(
                        LocalTextStyle provides hintTextStyle,
                        LocalContentColor provides hintTextColor,
                        content = hint
                    )
                }
            }
        }
    ) { measurables, incomingConstraints ->
        val horizontalPadding =
            (contentPadding.calculateLeftPadding(layoutDirection) + contentPadding.calculateRightPadding(layoutDirection)).roundToPx()
        val verticalPadding =
            (contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()).roundToPx()

        // measure hint
        val hintConstraints = incomingConstraints.copy(minHeight = 0)
        val hintPlaceable = measurables.find { it.layoutId == HINT_ID }?.measure(hintConstraints)
        val occupiedSpaceVertically = hintPlaceable?.height ?: 0

        val constraintsWithoutPadding = incomingConstraints.offset(
            -horizontalPadding,
            -verticalPadding - occupiedSpaceVertically
        )

        val textConstraints = constraintsWithoutPadding
        val textFieldPlaceable = measurables.first { it.layoutId == TEXT_FIELD_ID }.measure(textConstraints)

        // measure placeholder
        val placeholderConstraints = textConstraints.copy(minWidth = 0, minHeight = 0)
        val placeholderPlaceable = measurables.find { it.layoutId == PLACEHOLDER_ID }?.measure(placeholderConstraints)

        val width = calculateWidth(
            textFieldPlaceable,
            placeholderPlaceable,
            horizontalPadding,
            hintPlaceable,
            constraintsWithoutPadding
        )
        val height = calculateHeight(
            textFieldPlaceable,
            placeholderPlaceable,
            verticalPadding,
            hintPlaceable,
            constraintsWithoutPadding
        )

        layout(width, height) {
            place(
                height,
                contentPadding,
                hintPlaceable,
                textFieldPlaceable,
                placeholderPlaceable,
                layoutDirection,
                this@Layout
            )
        }
    }
}

private fun calculateWidth(
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    horizontalPadding: Int,
    hintPlaceable: Placeable?,
    constraints: Constraints
): Int {
    return maxOf(
        textFieldPlaceable.width + horizontalPadding,
        (placeholderPlaceable?.width ?: 0) + horizontalPadding,
        hintPlaceable?.width ?: 0,
        constraints.minWidth
    )
}

private fun calculateHeight(
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    verticalPadding: Int,
    hintPlaceable: Placeable?,
    constraints: Constraints
): Int {
    val middleSection = maxOf(
        textFieldPlaceable.height,
        placeholderPlaceable?.height ?: 0
    ) + verticalPadding
    val wrappedHeight = (hintPlaceable?.height ?: 0) + middleSection
    return max(wrappedHeight, constraints.minHeight)
}

private fun Placeable.PlacementScope.place(
    height: Int,
    contentPadding: PaddingValues,
    hintPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    layoutDirection: LayoutDirection,
    density: Density
) = with(density) {
    hintPlaceable?.placeRelative(
        0,
        height - hintPlaceable.height
    )

    val y = contentPadding.calculateTopPadding().roundToPx()
    val x = contentPadding.calculateLeftPadding(layoutDirection).roundToPx()

    textFieldPlaceable.placeRelative(x, y)

    placeholderPlaceable?.placeRelative(x, y)
}

private const val PLACEHOLDER_ID = "Placeholder"
private const val TEXT_FIELD_ID = "TextField"
private const val HINT_ID = "Hint"

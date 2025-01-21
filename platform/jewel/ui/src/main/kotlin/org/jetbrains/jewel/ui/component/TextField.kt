package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.unit.offset
import kotlin.math.max
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle

@Suppress("DuplicatedCode") // The dupe is scheduled for removal
@Composable
public fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: TextFieldStyle = JewelTheme.textFieldStyle,
    outline: Outline = Outline.None,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    outputTransformation: OutputTransformation? = null,
    undecorated: Boolean = false,
) {
    InputField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = inputTransformation,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = TextFieldLineLimits.SingleLine,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        style = style,
        outline = outline,
        outputTransformation = outputTransformation,
        decorator =
            if (!undecorated) {
                TextFieldDecorator { innerTextField ->
                    val minSize = style.metrics.minSize

                    TextFieldDecorationBox(
                        modifier =
                            Modifier.defaultMinSize(minWidth = minSize.width, minHeight = minSize.height)
                                .padding(style.metrics.contentPadding),
                        innerTextField = innerTextField,
                        textStyle = textStyle,
                        placeholderTextColor = style.colors.placeholder,
                        placeholder = if (state.text.isEmpty()) placeholder else null,
                        leadingIcon = leadingIcon,
                        trailingIcon = trailingIcon,
                    )
                }
            } else {
                null
            },
        scrollState = rememberScrollState(),
    )
}

@ScheduledForRemoval(inVersion = "Before 1.0")
@Deprecated("Please use TextField(state) instead. If you want to observe text changes, use snapshotFlow { state.text }")
@Composable
public fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    outline: Outline = Outline.None,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextFieldStyle = JewelTheme.textFieldStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)
    var lastTextValue by remember(value) { mutableStateOf(value) }

    @Suppress("DEPRECATION")
    TextField(
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
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        onTextLayout = onTextLayout,
        style = style,
        interactionSource = interactionSource,
    )
}

@Suppress("DuplicatedCode") // This is scheduled for removal
@ScheduledForRemoval(inVersion = "Before 1.0")
@Deprecated("Please use TextField(state) instead. If you want to observe text changes, use snapshotFlow { state.text }")
@Composable
public fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    outline: Outline = Outline.None,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextFieldStyle = JewelTheme.textFieldStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    @Suppress("DEPRECATION")
    InputField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        outline = outline,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        maxLines = 1,
        onTextLayout = onTextLayout,
        style = style,
        textStyle = textStyle,
        interactionSource = interactionSource,
    ) { innerTextField, _ ->
        val minSize = style.metrics.minSize

        TextFieldDecorationBox(
            modifier =
                Modifier.defaultMinSize(minWidth = minSize.width, minHeight = minSize.height)
                    .padding(style.metrics.contentPadding),
            innerTextField = innerTextField,
            textStyle = textStyle,
            placeholderTextColor = style.colors.placeholder,
            placeholder = if (value.text.isEmpty()) placeholder else null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

@Composable
private fun TextFieldDecorationBox(
    modifier: Modifier = Modifier,
    innerTextField: @Composable () -> Unit,
    textStyle: TextStyle,
    placeholderTextColor: Color,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Layout(
        modifier = modifier,
        content = {
            if (leadingIcon != null) {
                Box(modifier = Modifier.layoutId(LEADING_ID), contentAlignment = Alignment.Center) { leadingIcon() }
            }
            if (trailingIcon != null) {
                Box(modifier = Modifier.layoutId(TRAILING_ID), contentAlignment = Alignment.Center) { trailingIcon() }
            }
            if (placeholder != null) {
                Box(modifier = Modifier.layoutId(PLACEHOLDER_ID), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalTextStyle provides textStyle.copy(color = placeholderTextColor),
                        LocalContentColor provides placeholderTextColor,
                        content = placeholder,
                    )
                }
            }

            Box(modifier = Modifier.layoutId(TEXT_FIELD_ID), propagateMinConstraints = true) { innerTextField() }
        },
    ) { measurables, incomingConstraints ->
        // used to calculate the constraints for measuring elements that will be placed in a row
        var occupiedSpaceHorizontally = 0
        val iconConstraints = incomingConstraints.copy(minWidth = 0, minHeight = 0)

        // measure trailing icon
        val trailingPlaceable = measurables.find { it.layoutId == TRAILING_ID }?.measure(iconConstraints)

        val leadingPlaceable = measurables.find { it.layoutId == LEADING_ID }?.measure(iconConstraints)
        occupiedSpaceHorizontally += trailingPlaceable?.width ?: 0
        occupiedSpaceHorizontally += leadingPlaceable?.width ?: 0

        val textFieldConstraints =
            incomingConstraints.offset(horizontal = -occupiedSpaceHorizontally).copy(minHeight = 0)
        val textFieldPlaceable = measurables.single { it.layoutId == TEXT_FIELD_ID }.measure(textFieldConstraints)

        // measure placeholder (can't be bigger than the text)
        val placeholderConstraints =
            textFieldConstraints.copy(
                minWidth = 0,
                maxWidth = textFieldPlaceable.width,
                maxHeight = textFieldPlaceable.height,
            )
        val placeholderPlaceable = measurables.find { it.layoutId == PLACEHOLDER_ID }?.measure(placeholderConstraints)

        val width = calculateWidth(leadingPlaceable, trailingPlaceable, textFieldPlaceable, incomingConstraints)
        val height = calculateHeight(leadingPlaceable, trailingPlaceable, textFieldPlaceable, incomingConstraints)

        layout(width, height) {
            place(
                height = height,
                width = width,
                leadingPlaceable = leadingPlaceable,
                trailingPlaceable = trailingPlaceable,
                textFieldPlaceable = textFieldPlaceable,
                placeholderPlaceable = placeholderPlaceable,
            )
        }
    }
}

private fun calculateWidth(
    leadingPlaceable: Placeable?,
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    constraints: Constraints,
): Int {
    val middleSection = textFieldPlaceable.width
    val wrappedWidth = middleSection + (trailingPlaceable?.width ?: 0) + (leadingPlaceable?.width ?: 0)
    return max(wrappedWidth, constraints.minWidth)
}

private fun calculateHeight(
    leadingPlaceable: Placeable?,
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    constraints: Constraints,
): Int =
    maxOf(
        textFieldPlaceable.height,
        leadingPlaceable?.height ?: 0,
        trailingPlaceable?.height ?: 0,
        constraints.minHeight,
    )

private fun Placeable.PlacementScope.place(
    height: Int,
    width: Int,
    leadingPlaceable: Placeable?,
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
) {
    // placed center vertically and to the end edge horizontally
    leadingPlaceable?.placeRelative(0, Alignment.CenterVertically.align(leadingPlaceable.height, height))
    trailingPlaceable?.placeRelative(
        width - trailingPlaceable.width,
        Alignment.CenterVertically.align(trailingPlaceable.height, height),
    )

    // placed similar to the input text below
    placeholderPlaceable?.placeRelative(
        leadingPlaceable?.width ?: 0,
        Alignment.CenterVertically.align(placeholderPlaceable.height, height),
    )

    // placed center vertically
    textFieldPlaceable.placeRelative(
        leadingPlaceable?.width ?: 0,
        Alignment.CenterVertically.align(textFieldPlaceable.height, height),
    )
}

private const val PLACEHOLDER_ID = "Placeholder"
private const val TEXT_FIELD_ID = "TextField"
private const val TRAILING_ID = "Trailing"
private const val LEADING_ID = "Leading"

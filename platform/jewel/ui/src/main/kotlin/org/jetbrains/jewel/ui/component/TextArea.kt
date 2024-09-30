package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.jewel.ui.theme.textAreaStyle

@Composable
public fun TextArea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.MultiLine(),
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: TextAreaStyle = JewelTheme.textAreaStyle,
    outline: Outline = Outline.None,
    placeholder: @Composable (() -> Unit)? = null,
    decorationBoxModifier: Modifier = Modifier,
    outputTransformation: OutputTransformation? = null,
    undecorated: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
    scrollbarStyle: ScrollbarStyle? = JewelTheme.scrollbarStyle,
) {
    val minSize = style.metrics.minSize
    InputField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = inputTransformation,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        style = style,
        outline = outline,
        outputTransformation = outputTransformation,
        decorator =
            if (undecorated) {
                NoTextAreaDecorator(style, scrollbarStyle, scrollState)
            } else {
                TextAreaDecorator(
                    style,
                    state,
                    placeholder,
                    textStyle,
                    decorationBoxModifier,
                    minSize,
                    scrollbarStyle,
                    scrollState,
                )
            },
        undecorated = undecorated,
        scrollState = scrollState,
    )
}

@Composable
private fun NoTextAreaDecorator(style: TextAreaStyle, scrollbarStyle: ScrollbarStyle?, scrollState: ScrollState) =
    TextFieldDecorator { innerTextField ->
        val (contentPadding, innerEndPadding) =
            calculatePaddings(scrollbarStyle, style, scrollState, LocalLayoutDirection.current)

        if (scrollbarStyle != null) {
            TextAreaScrollableContainer(
                scrollState = scrollState,
                style = scrollbarStyle,
                contentModifier = Modifier.padding(style.metrics.borderWidth).padding(end = innerEndPadding),
                content = { Box(Modifier.padding(contentPadding)) { innerTextField() } },
            )
        } else {
            Box(Modifier.padding(contentPadding)) { innerTextField() }
        }
    }

@Composable
private fun TextAreaDecorator(
    style: TextAreaStyle,
    state: TextFieldState,
    placeholder: @Composable (() -> Unit)?,
    textStyle: TextStyle,
    decorationBoxModifier: Modifier,
    minSize: DpSize,
    scrollbarStyle: ScrollbarStyle?,
    scrollState: ScrollState,
) = TextFieldDecorator { innerTextField ->
    val (contentPadding, innerEndPadding) =
        calculatePaddings(scrollbarStyle, style, scrollState, LocalLayoutDirection.current)

    TextAreaDecorationBox(
        innerTextField = {
            if (scrollbarStyle != null) {
                TextAreaScrollableContainer(
                    scrollState = scrollState,
                    style = scrollbarStyle,
                    contentModifier = Modifier.padding(style.metrics.borderWidth).padding(end = innerEndPadding),
                    content = { Box(Modifier.padding(contentPadding)) { innerTextField() } },
                )
            } else {
                Box(Modifier.padding(contentPadding)) { innerTextField() }
            }
        },
        textStyle = textStyle,
        modifier = decorationBoxModifier.defaultMinSize(minWidth = minSize.width, minHeight = minSize.height),
        placeholder = if (state.text.isEmpty()) placeholder else null,
        placeholderTextColor = style.colors.placeholder,
        placeholderModifier = Modifier.padding(contentPadding).padding(style.metrics.borderWidth),
    )
}

@Composable
private fun calculatePaddings(
    scrollbarStyle: ScrollbarStyle?,
    style: TextAreaStyle,
    scrollState: ScrollState,
    layoutDirection: LayoutDirection,
): Pair<PaddingValues, Dp> =
    if (scrollbarStyle != null) {
        with(style.metrics.contentPadding) {
            val paddingValues =
                PaddingValues(
                    start = calculateStartPadding(layoutDirection),
                    top = calculateTopPadding(),
                    end = 0.dp,
                    bottom = calculateBottomPadding(),
                )

            val scrollbarExtraPadding =
                if (scrollState.canScrollForward || scrollState.canScrollBackward) {
                    scrollbarContentSafePadding(scrollbarStyle)
                } else 0.dp

            paddingValues to calculateEndPadding(layoutDirection) + scrollbarExtraPadding
        }
    } else {
        style.metrics.contentPadding to 0.dp
    }

@ScheduledForRemoval(inVersion = "Before 1.0")
@Deprecated("Please use TextArea(state) instead. If you want to observe text changes, use snapshotFlow { state.text }")
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
    decorationBoxModifier: Modifier = Modifier,
) {
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)
    var lastTextValue by remember(value) { mutableStateOf(value) }

    @Suppress("DEPRECATION")
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
        decorationBoxModifier = decorationBoxModifier,
    )
}

@ScheduledForRemoval(inVersion = "Before 1.0")
@Deprecated("Please use TextArea(state) instead. If you want to observe text changes, use snapshotFlow { state.text }")
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
    decorationBoxModifier: Modifier = Modifier,
) {
    val minSize = style.metrics.minSize
    val contentPadding = style.metrics.contentPadding

    @Suppress("DEPRECATION")
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
            textStyle = textStyle,
            modifier = decorationBoxModifier,
            placeholder = if (value.text.isEmpty()) placeholder else null,
            placeholderTextColor = style.colors.placeholder,
            placeholderModifier = Modifier.padding(contentPadding).padding(style.metrics.borderWidth),
        )
    }
}

@Composable
private fun TextAreaDecorationBox(
    innerTextField: @Composable () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier,
    placeholder: @Composable (() -> Unit)?,
    placeholderTextColor: Color,
    placeholderModifier: Modifier,
) {
    Layout(
        content = {
            if (placeholder != null) {
                Box(modifier = placeholderModifier.layoutId(PLACEHOLDER_ID), contentAlignment = Alignment.TopStart) {
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
        modifier,
    ) { measurables, incomingConstraints ->
        val textAreaConstraints = incomingConstraints.copy(minHeight = 0)

        val textAreaPlaceable = measurables.single { it.layoutId == TEXT_AREA_ID }.measure(textAreaConstraints)

        // Measure placeholder
        val placeholderConstraints = textAreaConstraints.copy(minWidth = 0, minHeight = 0)
        val placeholderPlaceable = measurables.find { it.layoutId == PLACEHOLDER_ID }?.measure(placeholderConstraints)

        val width = calculateWidth(textAreaPlaceable, placeholderPlaceable, incomingConstraints)
        val height = calculateHeight(textAreaPlaceable, placeholderPlaceable, incomingConstraints)

        layout(width, height) {
            // Placed similar to the input text below
            placeholderPlaceable?.placeRelative(0, 0)

            // Placed top-start
            textAreaPlaceable.placeRelative(0, 0)
        }
    }
}

private fun calculateWidth(
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    incomingConstraints: Constraints,
): Int = maxOf(textFieldPlaceable.width, placeholderPlaceable?.width ?: 0).coerceAtLeast(incomingConstraints.minWidth)

private fun calculateHeight(
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    incomingConstraints: Constraints,
): Int {
    val textAreaHeight = maxOf(textFieldPlaceable.height, placeholderPlaceable?.height ?: 0)
    return textAreaHeight.coerceAtLeast(incomingConstraints.minHeight)
}

private const val PLACEHOLDER_ID = "Placeholder"
private const val TEXT_AREA_ID = "TextField"

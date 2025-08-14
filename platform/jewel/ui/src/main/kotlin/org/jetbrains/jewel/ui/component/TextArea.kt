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
import androidx.compose.runtime.remember
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
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.jewel.ui.theme.textAreaStyle

/**
 * A multi-line text input component that follows the standard visual styling.
 *
 * Provides a scrollable text editing area that can display and edit multiple lines of text. The component includes
 * standard text editing capabilities, scrollbars when needed, and platform-specific key bindings.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/text-area.html)
 *
 * **Usage example:**
 * [`TextAreas.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/TextAreas.kt)
 *
 * **Swing equivalent:**
 * [`JBTextArea`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBTextArea.java)
 *
 * @param state The state object controlling the text content and selection
 * @param modifier Modifier to be applied to the text area
 * @param enabled Controls whether the text area can be interacted with
 * @param readOnly Controls whether the text can be modified
 * @param inputTransformation Transforms text input before it appears in the text area
 * @param textStyle The typography style to be applied to the text
 * @param keyboardOptions Options controlling keyboard input behavior
 * @param onKeyboardAction Handler for keyboard actions
 * @param lineLimits Constraints on the number of lines allowed
 * @param onTextLayout Callback for text layout changes
 * @param interactionSource Source of interactions for this text area
 * @param style The visual styling configuration for the text area
 * @param outline The outline style to be applied to the text area
 * @param placeholder Content to display when the text area is empty
 * @param decorationBoxModifier Modifier to be applied to the decoration box
 * @param outputTransformation Transforms text output for display
 * @param undecorated Whether to show the text area without decorations
 * @param scrollState The state object controlling scrolling behavior
 * @param scrollbarStyle The visual styling configuration for the scrollbar
 * @see com.intellij.ui.components.JBTextArea
 */
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
                NoTextAreaDecorator(style, scrollbarStyle, scrollState, state, placeholder, textStyle)
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
private fun NoTextAreaDecorator(
    style: TextAreaStyle,
    scrollbarStyle: ScrollbarStyle?,
    scrollState: ScrollState,
    state: TextFieldState,
    placeholder: @Composable (() -> Unit)?,
    textStyle: TextStyle,
) = TextFieldDecorator { innerTextField ->
    val (contentPadding, innerEndPadding) =
        calculatePaddings(scrollbarStyle, style, scrollState, LocalLayoutDirection.current)

    Box(contentAlignment = Alignment.TopStart) {
        if (state.text.isEmpty() && placeholder != null) {
            Box(modifier = Modifier.padding(contentPadding)) {
                CompositionLocalProvider(
                    LocalTextStyle provides textStyle.copy(color = style.colors.placeholder),
                    LocalContentColor provides style.colors.placeholder,
                    content = placeholder,
                )
            }
        }

        TextAreaContentWrapper(style, scrollbarStyle, scrollState, innerTextField, contentPadding, innerEndPadding)
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
            TextAreaContentWrapper(style, scrollbarStyle, scrollState, innerTextField, contentPadding, innerEndPadding)
        },
        textStyle = textStyle,
        modifier = decorationBoxModifier.defaultMinSize(minWidth = minSize.width, minHeight = minSize.height),
        placeholder = if (state.text.isEmpty()) placeholder else null,
        placeholderTextColor = style.colors.placeholder,
        placeholderModifier = Modifier.padding(contentPadding).padding(style.metrics.borderWidth),
    )
}

@Composable
private fun TextAreaContentWrapper(
    style: TextAreaStyle,
    scrollbarStyle: ScrollbarStyle?,
    scrollState: ScrollState,
    content: @Composable () -> Unit,
    contentPadding: PaddingValues,
    innerEndPadding: Dp,
) {
    if (scrollbarStyle != null) {
        TextAreaScrollableContainer(
            scrollState,
            style = scrollbarStyle,
            contentModifier = Modifier.padding(style.metrics.borderWidth).padding(end = innerEndPadding),
            content = { Box(Modifier.padding(contentPadding)) { content() } },
        )
    } else {
        Box(Modifier.padding(contentPadding)) { content() }
    }
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

/**
 * A multi-line text input component that follows the standard visual styling.
 * > **Note:** you should prefer the state-based variant whenever possible. When the `TextFieldState`-based variant will
 * > reach feature parity with this variant, we'll deprecate this variant and, after some time, remove it.
 *
 * Provides a scrollable text editing area that can display and edit multiple lines of text. The component includes
 * standard text editing capabilities and platform-specific key bindings. It **cannot** display scrollbars.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/text-area.html)
 *
 * **Usage example:**
 * [`TextAreas.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/TextAreas.kt)
 *
 * **Swing equivalent:**
 * [`JBTextArea`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBTextArea.java)
 *
 * @param value The state object controlling the text content and selection
 * @param onValueChange A lambda invoked when the internal state changes
 * @param modifier Modifier to be applied to the text area
 * @param enabled Controls whether the text area can be interacted with
 * @param readOnly Controls whether the text can be modified
 * @param placeholder Content to display when the text area is empty
 * @param undecorated Whether to show the text area without decorations
 * @param outline The outline style to be applied to the text area
 * @param visualTransformation Transforms text input before it appears in the text area
 * @param keyboardOptions Options controlling keyboard input behavior
 * @param keyboardActions Handler for keyboard actions
 * @param maxLines Sets the number of max lines allowed
 * @param onTextLayout Callback for text layout changes
 * @param style The visual styling configuration for the text area
 * @param textStyle The typography style to be applied to the text
 * @param interactionSource Source of interactions for this text area
 * @param decorationBoxModifier Modifier to be applied to the decoration box
 * @see com.intellij.ui.components.JBTextArea
 */
@ExperimentalJewelApi
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

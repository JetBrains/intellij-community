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
import androidx.compose.runtime.remember
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
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle

/**
 * A single-line text input component that follows the standard visual styling.
 *
 * Provides a text input field for single-line text entry with optional leading and trailing icons, placeholder text,
 * and various text input features. The component supports standard text editing capabilities and platform-specific key
 * bindings.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/input-field.html)
 *
 * **Usage example:**
 * [`TextFields.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/TextFields.kt)
 *
 * **Swing equivalent:**
 * [`JBTextField`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBTextField.java)
 * and [`JTextField`](https://docs.oracle.com/javase/tutorial/uiswing/components/textfield.html)
 *
 * @param state The state object controlling the text content and selection
 * @param modifier Modifier to be applied to the text field
 * @param enabled Controls whether the text field can be interacted with
 * @param readOnly Controls whether the text can be modified
 * @param inputTransformation Transforms text input before it appears in the field
 * @param textStyle The typography style to be applied to the text
 * @param keyboardOptions Options controlling keyboard input behavior
 * @param onKeyboardAction Handler for keyboard actions
 * @param onTextLayout Callback for text layout changes
 * @param interactionSource Source of interactions for this text field
 * @param style The visual styling configuration for the text field
 * @param outline The outline style to be applied to the text field
 * @param placeholder Content to display when the text field is empty
 * @param leadingIcon Optional icon to display before the text
 * @param trailingIcon Optional icon to display after the text
 * @param outputTransformation Transforms text output for display
 * @param undecorated Whether to show the text field without decorations
 * @see com.intellij.ui.components.JBTextField
 */
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
            if (undecorated) {
                TextFieldDecorator { innerTextField ->
                    UndecoratedTextFieldDecorationBox(
                        innerTextField = innerTextField,
                        textStyle = textStyle,
                        placeholderTextColor = style.colors.placeholder,
                        placeholder = if (state.text.isEmpty()) placeholder else null,
                    )
                }
            } else {
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
            },
        undecorated = undecorated,
        scrollState = rememberScrollState(),
    )
}

/**
 * A single-line text input component that follows the standard visual styling.
 * > **Note:** you should prefer the state-based variant whenever possible. When the `TextFieldState`-based variant will
 * > reach feature parity with this variant, we'll deprecate this variant and, after some time, remove it.
 *
 * Provides a text input field for single-line text entry with optional leading and trailing icons, placeholder text,
 * and various text input features. The component supports standard text editing capabilities and platform-specific key
 * bindings.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/input-field.html)
 *
 * **Usage example:**
 * [`TextFields.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/TextFields.kt)
 *
 * **Swing equivalent:**
 * [`JBTextField`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBTextField.java)
 * and [`JTextField`](https://docs.oracle.com/javase/tutorial/uiswing/components/textfield.html)
 *
 * @param value The state object controlling the text content and selection
 * @param onValueChange A lambda invoked when the internal state changes
 * @param modifier Modifier to be applied to the text field
 * @param enabled Controls whether the text field can be interacted with
 * @param readOnly Controls whether the text can be modified
 * @param outline The outline style to be applied to the text field
 * @param placeholder Content to display when the text field is empty
 * @param leadingIcon Optional icon to display before the text
 * @param trailingIcon Optional icon to display after the text
 * @param undecorated Whether to show the text field without decorations
 * @param visualTransformation Transforms text input before it appears in the text field
 * @param keyboardOptions Options controlling keyboard input behavior
 * @param keyboardActions Handler for keyboard actions
 * @param onTextLayout Callback for text layout changes
 * @param style The visual styling configuration for the text field
 * @param textStyle The typography style to be applied to the text
 * @param interactionSource Source of interactions for this text field
 * @see com.intellij.ui.components.JBTextField
 */
@ExperimentalJewelApi
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
private fun UndecoratedTextFieldDecorationBox(
    innerTextField: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)?,
    textStyle: TextStyle,
    placeholderTextColor: Color,
) {
    Box(propagateMinConstraints = true, contentAlignment = Alignment.CenterStart) {
        if (placeholder != null) {
            CompositionLocalProvider(
                LocalTextStyle provides textStyle.copy(color = placeholderTextColor),
                LocalContentColor provides placeholderTextColor,
                content = placeholder,
            )
        }

        innerTextField()
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

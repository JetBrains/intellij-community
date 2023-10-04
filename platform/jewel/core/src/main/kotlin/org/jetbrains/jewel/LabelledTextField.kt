package org.jetbrains.jewel

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.offset
import org.jetbrains.jewel.styling.LabelledTextFieldStyle

/**
 * @param label the label to display above the component.
 * @param hint the optional hint to be displayed underneath the component.
 *     By default it will have a greyed out appearance and smaller text.
 * @param placeholder the optional placeholder to be displayed over the
 *     component when the [value] is empty.
 */
@Composable
fun LabelledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    outline: Outline = Outline.None,
    hint: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: LabelledTextFieldStyle = IntelliJTheme.labelledTextFieldStyle,
    textStyle: TextStyle = IntelliJTheme.textStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)
    var lastTextValue by remember(value) { mutableStateOf(value) }

    LabelledTextField(
        label = label,
        value = textFieldValue,
        onValueChange = {
            textFieldValueState = it
            if (it.text != lastTextValue) {
                lastTextValue = it.text
                onValueChange(it.text)
            }
        },
        modifier = modifier,
        textFieldModifier = textFieldModifier,
        enabled = enabled,
        readOnly = readOnly,
        outline = outline,
        hint = hint,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        onTextLayout = onTextLayout,
        style = style,
        textStyle = textStyle,
        interactionSource = interactionSource,
    )
}

/**
 * @param label the label to display above the component.
 * @param hint the optional hint to be displayed underneath the component.
 *     By default it will have a greyed out appearance and smaller text.
 * @param placeholder the optional placeholder to be displayed over the
 *     component when the [value] is empty.
 */
@Composable
fun LabelledTextField(
    label: @Composable () -> Unit,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    outline: Outline = Outline.None,
    hint: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: LabelledTextFieldStyle = IntelliJTheme.labelledTextFieldStyle,
    textStyle: TextStyle = IntelliJTheme.textStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    LabelledTextFieldLayout(
        modifier = modifier,
        label = label,
        textField = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = textFieldModifier,
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
                textStyle = textStyle,
                interactionSource = interactionSource,
            )
        },
        hint = hint?.let {
            {
                CompositionLocalProvider(
                    LocalTextStyle provides style.textStyles.hint,
                    LocalContentColor provides style.colors.hint,
                    content = it,
                )
            }
        },
        style = style,
    )
}

@Composable
private fun LabelledTextFieldLayout(
    modifier: Modifier,
    label: @Composable () -> Unit,
    textField: @Composable () -> Unit,
    hint: (@Composable () -> Unit)?,
    style: LabelledTextFieldStyle,
) {
    val labelSpacing = style.metrics.labelSpacing
    val hintSpacing = style.metrics.hintSpacing
    Layout(
        modifier = modifier,
        content = {
            Box(modifier = Modifier.layoutId(LABEL_ID), contentAlignment = Alignment.Center) {
                label()
            }
            Box(modifier = Modifier.layoutId(TEXT_FIELD_ID), propagateMinConstraints = true) {
                textField()
            }
            if (hint != null) {
                Box(modifier = Modifier.layoutId(HINT_ID), contentAlignment = Alignment.Center) {
                    hint()
                }
            }
        },
    ) { measurables, incomingConstraints ->
        val hintMeasurable = measurables.firstOrNull { it.layoutId == HINT_ID }

        val horizontalSpacing = labelSpacing.roundToPx()
        val verticalSpacing = if (hintMeasurable == null) 0 else hintSpacing.roundToPx()

        val constraintsWithoutSpacing = incomingConstraints.offset(
            horizontal = -horizontalSpacing,
            vertical = -verticalSpacing,
        )

        val textFieldPlaceable = measurables.first { it.layoutId == TEXT_FIELD_ID }
            .measure(constraintsWithoutSpacing)

        val labelPlaceable = measurables.first { it.layoutId == LABEL_ID }
            .measure(constraintsWithoutSpacing.offset(horizontal = -textFieldPlaceable.width))

        val hintPlaceable = hintMeasurable?.measure(
            constraintsWithoutSpacing
                .offset(vertical = -textFieldPlaceable.height)
                .copy(maxWidth = textFieldPlaceable.width),
        )

        val width = labelPlaceable.width + textFieldPlaceable.width + horizontalSpacing
        val height = (hintPlaceable?.height ?: 0) + textFieldPlaceable.height + verticalSpacing

        layout(width, height) {
            labelPlaceable.placeRelative(
                x = 0,
                y = Alignment.CenterVertically.align(labelPlaceable.height, textFieldPlaceable.height),
            )
            textFieldPlaceable.placeRelative(
                x = labelPlaceable.width + horizontalSpacing,
                y = 0,
            )
            hintPlaceable?.placeRelative(
                x = labelPlaceable.width + horizontalSpacing,
                y = textFieldPlaceable.height + verticalSpacing,
            )
        }
    }
}

private const val LABEL_ID = "Label"
private const val TEXT_FIELD_ID = "TextField"
private const val HINT_ID = "Hint"

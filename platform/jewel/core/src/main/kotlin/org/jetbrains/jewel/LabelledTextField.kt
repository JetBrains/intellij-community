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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset

@Composable
fun LabelledTextField(
    label: @Composable () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    hint: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    defaults: LabelledTextFieldDefaults = IntelliJTheme.labelledTextFieldDefaults,
    colors: LabelledTextFieldColors = defaults.colors(),
    textStyle: TextStyle = defaults.textStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
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
        isError = isError,
        hint = hint,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        undecorated = undecorated,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        onTextLayout = onTextLayout,
        defaults = defaults,
        colors = colors,
        textStyle = textStyle,
        interactionSource = interactionSource
    )
}

@Composable
fun LabelledTextField(
    label: @Composable () -> Unit,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    hint: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    defaults: LabelledTextFieldDefaults = IntelliJTheme.labelledTextFieldDefaults,
    colors: LabelledTextFieldColors = defaults.colors(),
    textStyle: TextStyle = defaults.textStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    LabelledTextFieldLayout(
        modifier = modifier,
        label = {
            CompositionLocalProvider(
                LocalTextStyle provides defaults.labelTextStyle(),
                LocalTextColor provides colors.labelTextColor(),
                content = label
            )
        },
        textField = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = textFieldModifier,
                enabled = enabled,
                readOnly = readOnly,
                isError = isError,
                placeholder = placeholder,
                trailingIcon = trailingIcon,
                undecorated = undecorated,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                onTextLayout = onTextLayout,
                defaults = defaults,
                colors = colors,
                textStyle = textStyle,
                interactionSource = interactionSource
            )
        },
        hint = hint?.let {
            {
                CompositionLocalProvider(
                    LocalTextStyle provides defaults.hintTextStyle(),
                    LocalTextColor provides colors.hintTextColor(),
                    content = it
                )
            }
        },
        defaults = defaults
    )
}

interface LabelledTextFieldDefaults : TextFieldDefaults {

    @Composable
    override fun colors(): LabelledTextFieldColors

    @Composable
    fun hintTextStyle(): TextStyle

    @Composable
    fun labelTextStyle(): TextStyle

    @Composable
    fun labelSpacing(): Dp

    @Composable
    fun hintSpacing(): Dp
}

interface LabelledTextFieldColors : TextFieldColors {

    @Composable
    fun labelTextColor(): Color

    @Composable
    fun hintTextColor(): Color
}

fun labelledTextFieldColors(
    textFieldColors: TextFieldColors,
    labelTextColor: Color,
    hintTextColor: Color
): LabelledTextFieldColors = DefaultLabelledTextFieldColors(
    textFieldColors = textFieldColors,
    labelTextColor = labelTextColor,
    hintTextColor = hintTextColor
)

private class DefaultLabelledTextFieldColors(
    textFieldColors: TextFieldColors,
    val labelTextColor: Color,
    val hintTextColor: Color
) : LabelledTextFieldColors, TextFieldColors by textFieldColors {

    @Composable
    override fun hintTextColor(): Color = hintTextColor

    @Composable
    override fun labelTextColor(): Color = labelTextColor
}

@Composable
private fun LabelledTextFieldLayout(
    modifier: Modifier,
    label: @Composable () -> Unit,
    textField: @Composable () -> Unit,
    hint: (@Composable () -> Unit)?,
    defaults: LabelledTextFieldDefaults
) {
    val labelSpacing = defaults.labelSpacing()
    val hintSpacing = defaults.hintSpacing()
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
        }
    ) { measurables, incomingConstraints ->
        val hintMeasurable = measurables.firstOrNull { it.layoutId == HINT_ID }

        val horizontalSpacing = labelSpacing.roundToPx()
        val verticalSpacing = if (hintMeasurable == null) 0 else hintSpacing.roundToPx()

        val constraintsWithoutSpacing = incomingConstraints.offset(
            horizontal = -horizontalSpacing,
            vertical = -verticalSpacing
        )

        val textFieldPlaceable = measurables.first { it.layoutId == TEXT_FIELD_ID }
            .measure(constraintsWithoutSpacing)

        val labelPlaceable = measurables.first { it.layoutId == LABEL_ID }
            .measure(constraintsWithoutSpacing.offset(horizontal = -textFieldPlaceable.width))

        val hintPlaceable = hintMeasurable?.measure(
            constraintsWithoutSpacing
                .offset(vertical = -textFieldPlaceable.height)
                .copy(maxWidth = textFieldPlaceable.width)
        )

        val width = labelPlaceable.width + textFieldPlaceable.width + horizontalSpacing
        val height = (hintPlaceable?.height ?: 0) + textFieldPlaceable.height + verticalSpacing

        layout(width, height) {
            labelPlaceable.placeRelative(
                0,
                Alignment.CenterVertically.align(labelPlaceable.height, textFieldPlaceable.height)
            )
            textFieldPlaceable.placeRelative(
                labelPlaceable.width + horizontalSpacing,
                0
            )
            hintPlaceable?.placeRelative(
                labelPlaceable.width + horizontalSpacing,
                textFieldPlaceable.height + verticalSpacing
            )
        }
    }
}

private const val LABEL_ID = "Label"
private const val TEXT_FIELD_ID = "TextField"
private const val HINT_ID = "Hint"

internal val LocalLabelledTextFieldDefaults = staticCompositionLocalOf<LabelledTextFieldDefaults> {
    error("No LabelledTextFieldDefaults provided")
}

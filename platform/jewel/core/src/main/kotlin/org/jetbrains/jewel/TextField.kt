package org.jetbrains.jewel

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import org.jetbrains.jewel.foundation.Stroke
import kotlin.math.max

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    defaults: TextFieldDefaults = IntelliJTheme.textFieldDefaults,
    colors: TextFieldColors = defaults.colors(),
    textStyle: TextStyle = defaults.textStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)
    var lastTextValue by remember(value) { mutableStateOf(value) }

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
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    undecorated: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    defaults: TextFieldDefaults = IntelliJTheme.textFieldDefaults,
    colors: TextFieldColors = defaults.colors(),
    textStyle: TextStyle = defaults.textStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) = InputField(
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
    singleLine = true,
    maxLines = 1,
    onTextLayout = onTextLayout,
    defaults = defaults,
    colors = colors,
    textStyle = textStyle,
    interactionSource = interactionSource
) { innerTextField, state ->
    TextFieldDecorationBox(
        modifier = Modifier.defaultMinSize(minHeight = defaults.minHeight(), minWidth = defaults.minWidth()).padding(defaults.contentPadding()),
        innerTextField = innerTextField,
        placeholderTextColor = colors.placeholderForeground(state).value,
        placeholder = if (value.text.isEmpty()) placeholder else null,
        trailingIcon = trailingIcon
    )
}

interface TextFieldDefaults : InputFieldDefaults {

    @Composable
    override fun colors(): TextFieldColors
}

interface TextFieldColors : InputFieldColors {

    @Composable
    fun placeholderForeground(state: InputFieldState): State<Color>
}

@Composable
private fun TextFieldDecorationBox(
    modifier: Modifier = Modifier,
    innerTextField: @Composable () -> Unit,
    placeholderTextColor: Color,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Layout(
        modifier = modifier,
        content = {
            if (trailingIcon != null) {
                Box(modifier = Modifier.layoutId(TRAILING_ID), contentAlignment = Alignment.Center) {
                    trailingIcon()
                }
            }
            if (placeholder != null) {
                Box(modifier = Modifier.layoutId(PLACEHOLDER_ID), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalTextColor provides placeholderTextColor,
                        content = placeholder
                    )
                }
            }

            Box(modifier = Modifier.layoutId(TEXT_FIELD_ID), propagateMinConstraints = true) {
                innerTextField()
            }
        }
    ) { measurables, incomingConstraints ->
        // used to calculate the constraints for measuring elements that will be placed in a row
        var occupiedSpaceHorizontally = 0

        val constraintsWithoutPadding = incomingConstraints

        val iconsConstraints = constraintsWithoutPadding.copy(minWidth = 0, minHeight = 0)

        // measure trailing icon
        val trailingPlaceable = measurables.find { it.layoutId == TRAILING_ID }
            ?.measure(iconsConstraints)
        occupiedSpaceHorizontally += trailingPlaceable?.width ?: 0

        val textConstraints = constraintsWithoutPadding.offset(
            horizontal = -occupiedSpaceHorizontally
        ).copy(minHeight = 0)
        val textFieldPlaceable = measurables.first { it.layoutId == TEXT_FIELD_ID }.measure(textConstraints)

        // measure placeholder
        val placeholderConstraints = textConstraints.copy(minWidth = 0)
        val placeholderPlaceable = measurables.find { it.layoutId == PLACEHOLDER_ID }?.measure(placeholderConstraints)

        val width = calculateWidth(
            trailingPlaceable,
            textFieldPlaceable,
            placeholderPlaceable,
            incomingConstraints
        )
        val height = calculateHeight(
            trailingPlaceable,
            textFieldPlaceable,
            placeholderPlaceable,
            incomingConstraints
        )

        layout(width, height) {
            place(
                height,
                width,
                trailingPlaceable,
                textFieldPlaceable,
                placeholderPlaceable,
                this@Layout
            )
        }
    }
}

private fun calculateWidth(
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    constraints: Constraints
): Int {
    val middleSection = maxOf(
        textFieldPlaceable.width,
        placeholderPlaceable?.width ?: 0
    )
    val wrappedWidth = middleSection + (trailingPlaceable?.width ?: 0)
    return max(wrappedWidth, constraints.minWidth)
}

private fun calculateHeight(
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    constraints: Constraints
): Int {
    return maxOf(
        textFieldPlaceable.height,
        placeholderPlaceable?.height ?: 0,
        trailingPlaceable?.height ?: 0,
        constraints.minHeight
    )
}

private fun Placeable.PlacementScope.place(
    height: Int,
    width: Int,
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    density: Density
) = with(density) {
    // placed center vertically and to the end edge horizontally
    trailingPlaceable?.placeRelative(
        width - trailingPlaceable.width,
        Alignment.CenterVertically.align(trailingPlaceable.height, height)
    )

    // placed center vertically and after the leading icon horizontally if single line text field
    // placed to the top with padding for multi line text field
    textFieldPlaceable.placeRelative(
        0,
        Alignment.CenterVertically.align(textFieldPlaceable.height, height)
    )

    // placed similar to the input text above
    placeholderPlaceable?.let {
        it.placeRelative(
            0,
            Alignment.CenterVertically.align(it.height, height)
        )
    }
}

private const val PLACEHOLDER_ID = "Placeholder"
private const val TEXT_FIELD_ID = "TextField"
private const val TRAILING_ID = "Trailing"

fun textFieldColors(
    foreground: Color,
    background: Color,
    cursorBrush: Brush,
    borderStroke: Stroke,
    focusedForeground: Color,
    focusedBackground: Color,
    focusedCursorBrush: Brush,
    focusedBorderStroke: Stroke,
    errorForeground: Color,
    errorBackground: Color,
    errorCursorBrush: Brush,
    errorBorderStroke: Stroke,
    errorFocusedForeground: Color,
    errorFocusedBackground: Color,
    errorFocusedCursorBrush: Brush,
    errorFocusedBorderStroke: Stroke,
    disabledForeground: Color,
    disabledBackground: Color,
    disabledBorderStroke: Stroke,
    placeholderForeground: Color
): TextFieldColors = DefaultTextFieldColors(
    foreground = foreground,
    background = background,
    cursorBrush = cursorBrush,
    borderStroke = borderStroke,
    focusedForeground = focusedForeground,
    focusedBackground = focusedBackground,
    focusedCursorBrush = focusedCursorBrush,
    focusedBorderStroke = focusedBorderStroke,
    errorForeground = errorForeground,
    errorBackground = errorBackground,
    errorCursorBrush = errorCursorBrush,
    errorBorderStroke = errorBorderStroke,
    errorFocusedForeground = errorFocusedForeground,
    errorFocusedBackground = errorFocusedBackground,
    errorFocusedCursorBrush = errorFocusedCursorBrush,
    errorFocusedBorderStroke = errorFocusedBorderStroke,
    disabledForeground = disabledForeground,
    disabledBackground = disabledBackground,
    disabledBorderStroke = disabledBorderStroke,
    placeholderForeground = placeholderForeground
)

internal open class DefaultTextFieldColors(
    private val foreground: Color,
    private val background: Color,
    private val cursorBrush: Brush,
    private val borderStroke: Stroke,
    private val focusedForeground: Color,
    private val focusedBackground: Color,
    private val focusedCursorBrush: Brush,
    private val focusedBorderStroke: Stroke,
    private val errorForeground: Color,
    private val errorBackground: Color,
    private val errorCursorBrush: Brush,
    private val errorBorderStroke: Stroke,
    private val errorFocusedForeground: Color,
    private val errorFocusedBackground: Color,
    private val errorFocusedCursorBrush: Brush,
    private val errorFocusedBorderStroke: Stroke,
    private val disabledForeground: Color,
    private val disabledBackground: Color,
    private val disabledBorderStroke: Stroke,
    private val placeholderForeground: Color
) : TextFieldColors {

    @Composable
    override fun foreground(state: InputFieldState): State<Color> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledForeground
            state.isError && state.isFocused -> errorFocusedForeground
            state.isError -> errorForeground
            state.isFocused -> focusedForeground
            else -> foreground
        }
    )

    @Composable
    override fun background(state: InputFieldState): State<Color> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledBackground
            state.isError && state.isFocused -> errorFocusedBackground
            state.isError -> errorBackground
            state.isFocused -> focusedBackground
            else -> background
        }
    )

    @Composable
    override fun borderStroke(state: InputFieldState): State<Stroke> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledBorderStroke
            state.isError && state.isFocused -> errorFocusedBorderStroke
            state.isError -> errorBorderStroke
            state.isFocused -> focusedBorderStroke
            else -> borderStroke
        }
    )

    @Composable
    override fun cursorBrush(state: InputFieldState): State<Brush> = rememberUpdatedState(
        when {
            state.isError && state.isFocused -> errorFocusedCursorBrush
            state.isError -> errorCursorBrush
            state.isFocused -> focusedCursorBrush
            else -> cursorBrush
        }
    )

    @Composable
    override fun placeholderForeground(state: InputFieldState): State<Color> = rememberUpdatedState(
        placeholderForeground
    )
}

internal val LocalTextFieldDefaults = staticCompositionLocalOf<TextFieldDefaults> {
    error("No TextFieldDefaults provided")
}

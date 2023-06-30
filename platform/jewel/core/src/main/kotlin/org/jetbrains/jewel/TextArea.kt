package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Shape
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
import org.jetbrains.jewel.foundation.Stroke
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
    defaults: TextAreaDefaults = IntelliJTheme.textAreaDefaults,
    colors: TextAreaColors = defaults.colors(),
    textStyle: TextStyle = defaults.textStyle(),
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
        defaults = defaults,
        colors = colors,
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
    defaults: TextAreaDefaults = IntelliJTheme.textAreaDefaults,
    colors: TextAreaColors = defaults.colors(),
    textStyle: TextStyle = defaults.textStyle(),
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
        defaults = defaults,
        colors = colors,
        textStyle = textStyle,
        interactionSource = interactionSource
    ) { innerTextField, state ->
        TextAreaDecorationBox(
            modifier = Modifier
                .defaultMinSize(minHeight = defaults.minHeight(), minWidth = defaults.minWidth()),
            innerTextField = innerTextField,
            contentPadding = defaults.contentPadding(),
            placeholderTextColor = colors.placeholderForeground(state).value,
            placeholder = if (value.text.isEmpty()) placeholder else null,
            hintShape = defaults.hintShape(),
            hintContentPadding = defaults.hintContentPadding(),
            hintTextStyle = defaults.hintTextStyle(),
            hintTextColor = colors.hintForeground(state).value,
            hintBackground = colors.hintBackground(state).value,
            hint = hint
        )
    }
}

interface TextAreaDefaults : InputFieldDefaults {

    @Composable
    override fun colors(): TextAreaColors

    @Composable
    fun hintShape(): Shape

    @Composable
    fun hintContentPadding(): PaddingValues

    @Composable
    fun hintTextStyle(): TextStyle
}

interface TextAreaColors : InputFieldColors {

    @Composable
    fun placeholderForeground(state: InputFieldState): State<Color>

    @Composable
    fun hintForeground(state: InputFieldState): State<Color>

    @Composable
    fun hintBackground(state: InputFieldState): State<Color>
}

fun textAreaColors(
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
    placeholderForeground: Color,
    hintForeground: Color,
    hintBackground: Color,
    hintDisabledForeground: Color,
    hintDisabledBackground: Color
): TextAreaColors = DefaultTextAreaColors(
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
    placeholderForeground = placeholderForeground,
    hintForeground = hintForeground,
    hintBackground = hintBackground,
    hintDisabledForeground = hintDisabledForeground,
    hintDisabledBackground = hintDisabledBackground
)

internal open class DefaultTextAreaColors(
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
    private val placeholderForeground: Color,
    private val hintForeground: Color,
    private val hintBackground: Color,
    private val hintDisabledForeground: Color,
    private val hintDisabledBackground: Color
) : TextAreaColors {

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

    @Composable
    override fun hintForeground(state: InputFieldState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> hintDisabledForeground
                else -> hintForeground
            }
        )
    }

    @Composable
    override fun hintBackground(state: InputFieldState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> hintDisabledBackground
                else -> hintBackground
            }
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
    hintShape: Shape,
    hintContentPadding: PaddingValues,
    hintTextStyle: TextStyle,
    hintTextColor: Color,
    hintBackground: Color,
    hint: @Composable (() -> Unit)?
) {
    Layout(
        modifier = modifier,
        content = {
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

            if (hint != null) {
                Box(
                    modifier = Modifier.layoutId(HINT_ID)
                        .background(hintBackground, hintShape),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(Modifier.fillMaxWidth().padding(hintContentPadding)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides hintTextStyle,
                            LocalTextColor provides hintTextColor,
                            content = hint
                        )
                    }
                }
            }
        }
    ) { measurables, incomingConstraints ->
        val horizontalPadding =
            (contentPadding.calculateLeftPadding(layoutDirection) + contentPadding.calculateRightPadding(layoutDirection)).roundToPx()
        val verticalPadding = (contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()).roundToPx()

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

internal val LocalTextAreaDefaults = staticCompositionLocalOf<TextAreaDefaults> {
    error("No TextFieldDefaults provided")
}

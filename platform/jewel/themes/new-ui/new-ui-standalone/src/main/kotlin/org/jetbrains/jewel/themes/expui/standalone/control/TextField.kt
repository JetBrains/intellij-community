package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.DisabledAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.ErrorFocusAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDefaultTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDisabledAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalErrorAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalFocusAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.style.areaBorder
import org.jetbrains.jewel.themes.expui.standalone.style.areaFocusBorder
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import kotlin.math.max

data class TextFieldColors(
    override val normalAreaColors: AreaColors,
    override val errorAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors,
    override val errorFocusAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
) : AreaProvider, DisabledAreaProvider, ErrorFocusAreaProvider {

    @Composable
    fun provideArea(enabled: Boolean, focused: Boolean, isError: Boolean, content: @Composable () -> Unit) {
        val currentColors = when {
            !enabled -> disabledAreaColors
            isError -> if (focused) errorFocusAreaColors else errorAreaColors
            focused -> focusAreaColors
            else -> normalAreaColors
        }

        CompositionLocalProvider(
            LocalAreaColors provides currentColors,
            LocalDisabledAreaColors provides disabledAreaColors,
            LocalErrorAreaColors provides errorAreaColors,
            LocalFocusAreaColors provides focusAreaColors,
            LocalErrorAreaColors provides errorAreaColors,
            LocalNormalAreaColors provides normalAreaColors,
            content = content
        )
    }
}

val LocalTextFieldColors = compositionLocalOf<TextFieldColors> {
    LightTheme.TextFieldColors
}

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalDefaultTextStyle.current,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RoundedCornerShape(3.dp),
    colors: TextFieldColors = LocalTextFieldColors.current,
) {
    val focused = interactionSource.collectIsFocusedAsState()
    colors.provideArea(enabled, focused.value, isError) {
        val currentColors = LocalAreaColors.current

        val textColor = textStyle.color.takeOrElse {
            currentColors.text
        }
        val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.defaultMinSize(minWidth = 64.dp),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            maxLines = 1,
            visualTransformation = visualTransformation,
            onTextLayout = onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(currentColors.text),
        ) {
            TextFieldDecorationBox(
                focused = focused.value,
                shape = shape,
                innerTextField = it,
                placeholder = if (value.isEmpty()) placeholder else null,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
            )
        }
    }
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalDefaultTextStyle.current,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RoundedCornerShape(3.dp),
    colors: TextFieldColors = LocalTextFieldColors.current,
) {
    val focused = interactionSource.collectIsFocusedAsState()
    colors.provideArea(enabled, focused.value, isError) {
        val currentColors = LocalAreaColors.current

        val textColor = textStyle.color.takeOrElse {
            currentColors.text
        }
        val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.defaultMinSize(minWidth = 64.dp),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            maxLines = 1,
            visualTransformation = visualTransformation,
            onTextLayout = onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(currentColors.text),
        ) {
            TextFieldDecorationBox(
                focused = focused.value,
                shape = shape,
                innerTextField = it,
                placeholder = if (value.text.isEmpty()) placeholder else null,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
            )
        }
    }
}

@Composable
private fun TextFieldDecorationBox(
    focused: Boolean,
    shape: Shape,
    innerTextField: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Layout(
        modifier = Modifier.areaBackground(shape = shape).areaFocusBorder(focused, shape = shape)
            .areaBorder(shape = shape),
        content = {
            if (leadingIcon != null) {
                Box(modifier = Modifier.layoutId(LeadingId), contentAlignment = Alignment.Center) {
                    leadingIcon()
                }
            }
            if (trailingIcon != null) {
                Box(modifier = Modifier.layoutId(TrailingId), contentAlignment = Alignment.Center) {
                    trailingIcon()
                }
            }
            if (placeholder != null) {
                Box(modifier = Modifier.layoutId(PlaceholderId), contentAlignment = Alignment.Center) {
                    placeholder()
                }
            }

            Box(modifier = Modifier.layoutId(TextFieldId), propagateMinConstraints = true) {
                innerTextField()
            }
        }
    ) { measurables, incomingConstraints ->
        // used to calculate the constraints for measuring elements that will be placed in a row
        var occupiedSpaceHorizontally = 0

        val constraintsWithoutPadding = incomingConstraints.offset(
            horizontal = -2 * HorizontalTextFieldPadding.roundToPx(),
            vertical = -2 * VerticalTextFieldPadding.roundToPx()
        )

        val iconsConstraints = constraintsWithoutPadding.copy(minWidth = 0, minHeight = 0)

        // measure leading icon
        val leadingPlaceable = measurables.find { it.layoutId == LeadingId }?.measure(iconsConstraints)
        occupiedSpaceHorizontally += leadingPlaceable?.width ?: 0

        // measure trailing icon
        val trailingPlaceable = measurables.find { it.layoutId == TrailingId }
            ?.measure(iconsConstraints.offset(horizontal = -occupiedSpaceHorizontally))
        occupiedSpaceHorizontally += trailingPlaceable?.width ?: 0

        val textConstraints = constraintsWithoutPadding.offset(
            horizontal = -occupiedSpaceHorizontally
        ).copy(minHeight = 0)
        val textFieldPlaceable = measurables.first { it.layoutId == TextFieldId }.measure(textConstraints)

        // measure placeholder
        val placeholderConstraints = textConstraints.copy(minWidth = 0)
        val placeholderPlaceable = measurables.find { it.layoutId == PlaceholderId }?.measure(placeholderConstraints)

        val width = calculateWidth(
            leadingPlaceable, trailingPlaceable, textFieldPlaceable, placeholderPlaceable, incomingConstraints
        ) + 2 * HorizontalTextFieldPadding.roundToPx()
        val height = calculateHeight(
            leadingPlaceable, trailingPlaceable, textFieldPlaceable, placeholderPlaceable, incomingConstraints
        ) + 2 * VerticalTextFieldPadding.roundToPx()

        layout(width, height) {
            place(
                height,
                width,
                leadingPlaceable,
                trailingPlaceable,
                textFieldPlaceable,
                placeholderPlaceable,
                this@Layout
            )
        }
    }
}

private fun calculateWidth(
    leadingPlaceable: Placeable?,
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    constraints: Constraints,
): Int {
    val middleSection = maxOf(
        textFieldPlaceable.width, placeholderPlaceable?.width ?: 0
    )
    val wrappedWidth = (leadingPlaceable?.width ?: 0) + middleSection + (trailingPlaceable?.width ?: 0)
    return max(wrappedWidth, constraints.minWidth)
}

private fun calculateHeight(
    leadingPlaceable: Placeable?,
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    constraints: Constraints,
): Int {
    return maxOf(
        leadingPlaceable?.height ?: 0,
        textFieldPlaceable.height,
        placeholderPlaceable?.height ?: 0,
        trailingPlaceable?.height ?: 0,
        constraints.minHeight
    )
}

private fun Placeable.PlacementScope.place(
    height: Int,
    width: Int,
    leadingPlaceable: Placeable?,
    trailingPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    density: Density,
) = with(density) {
    val horizontalPadding = HorizontalTextFieldPadding.roundToPx()

    // placed center vertically and to the start edge horizontally
    leadingPlaceable?.placeRelative(
        horizontalPadding, Alignment.CenterVertically.align(leadingPlaceable.height, height)
    )

    // placed center vertically and to the end edge horizontally
    trailingPlaceable?.placeRelative(
        width - trailingPlaceable.width - horizontalPadding,
        Alignment.CenterVertically.align(trailingPlaceable.height, height)
    )

    // placed center vertically and after the leading icon horizontally if single line text field
    // placed to the top with padding for multi line text field
    textFieldPlaceable.placeRelative(
        horizontalPadding + (leadingPlaceable?.width ?: 0),
        Alignment.CenterVertically.align(textFieldPlaceable.height, height)
    )

    // placed similar to the input text above
    placeholderPlaceable?.let {
        it.placeRelative(
            horizontalPadding + (leadingPlaceable?.width ?: 0), Alignment.CenterVertically.align(it.height, height)
        )
    }
}

private const val PlaceholderId = "Placeholder"
private const val TextFieldId = "TextField"
private const val LeadingId = "Leading"
private const val TrailingId = "Trailing"

private val HorizontalTextFieldPadding = 6.dp
private val VerticalTextFieldPadding = 3.dp

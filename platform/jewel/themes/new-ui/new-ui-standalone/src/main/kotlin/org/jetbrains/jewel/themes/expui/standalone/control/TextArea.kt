package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDefaultTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.style.areaBorder
import org.jetbrains.jewel.themes.expui.standalone.style.areaFocusBorder
import kotlin.math.max

@Composable
fun TextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalDefaultTextStyle.current,
    placeholder: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RectangleShape,
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
            modifier = modifier.defaultMinSize(minWidth = 270.dp, minHeight = 55.dp),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = false,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            onTextLayout = onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(currentColors.text),
        ) {
            TextAreaDecorationBox(
                focused = focused.value,
                shape = shape,
                innerTextField = it,
                placeholder = if (value.isEmpty()) placeholder else null,
                footer = footer
            )
        }
    }
}

@Composable
private fun TextAreaDecorationBox(
    focused: Boolean,
    shape: Shape,
    innerTextField: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    Layout(
        modifier = Modifier.areaBackground(shape = shape).areaFocusBorder(focused, shape = shape)
            .areaBorder(shape = shape),
        content = {
            if (footer != null) {
                Row(modifier = Modifier.layoutId(FooterId), horizontalArrangement = Arrangement.Start) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp)) {
                        footer()
                    }
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
        var occupiedSpaceVertically = 0

        val constraintsWithoutPadding = incomingConstraints.offset(
            horizontal = -2 * TextAreaPadding.roundToPx(), vertical = -2 * TextAreaPadding.roundToPx()
        )

        val footerConstraints = constraintsWithoutPadding.copy(minWidth = 0, minHeight = 0)
        val footerPlaceable = measurables.find { it.layoutId == FooterId }?.measure(footerConstraints)
        occupiedSpaceVertically += footerPlaceable?.height ?: 0

        val textConstraints = constraintsWithoutPadding.offset(
            vertical = -occupiedSpaceVertically
        ).copy(minWidth = 0)
        val textFieldPlaceable = measurables.first { it.layoutId == TextFieldId }.measure(textConstraints)

        // measure placeholder
        val placeholderConstraints = textConstraints.copy(minHeight = 0)
        val placeholderPlaceable = measurables.find { it.layoutId == PlaceholderId }?.measure(placeholderConstraints)

        val width = calculateWidth(
            footerPlaceable, textFieldPlaceable, placeholderPlaceable, incomingConstraints
        ) + 2 * TextAreaPadding.roundToPx()
        val height = calculateHeight(
            footerPlaceable, textFieldPlaceable, placeholderPlaceable, incomingConstraints
        ) + 2 * TextAreaPadding.roundToPx()

        layout(width, height) {
            place(
                height, width, footerPlaceable, textFieldPlaceable, placeholderPlaceable, this@Layout
            )
        }
    }
}

private fun calculateWidth(
    footerPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    constraints: Constraints,
): Int {
    return maxOf(
        footerPlaceable?.width ?: 0, textFieldPlaceable.width, placeholderPlaceable?.width ?: 0, constraints.minWidth
    )
}

private fun calculateHeight(
    footerPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    constraints: Constraints,
): Int {
    val middleSection = maxOf(
        textFieldPlaceable.height, placeholderPlaceable?.height ?: 0
    )
    val wrappedHeight = (footerPlaceable?.height ?: 0) + middleSection
    return max(wrappedHeight, constraints.minHeight)
}

private fun Placeable.PlacementScope.place(
    height: Int,
    width: Int,
    footerPlaceable: Placeable?,
    textFieldPlaceable: Placeable,
    placeholderPlaceable: Placeable?,
    density: Density,
) = with(density) {
    val padding = TextAreaPadding.roundToPx()

    // placed center vertically and to the start edge horizontally
    footerPlaceable?.placeRelative(
        0, height - footerPlaceable.height
    )

    // placed center vertically and after the leading icon horizontally if single line text field
    // placed to the top with padding for multi line text field
    textFieldPlaceable.placeRelative(padding, padding)

    // placed similar to the input text above
    placeholderPlaceable?.placeRelative(padding, padding)
}

private const val PlaceholderId = "Placeholder"
private const val TextFieldId = "TextField"
private const val FooterId = "Footer"

private val TextAreaPadding = 6.dp

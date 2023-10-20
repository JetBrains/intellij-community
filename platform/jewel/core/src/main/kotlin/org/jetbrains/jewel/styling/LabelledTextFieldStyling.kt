package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.GenerateDataFunctions

@Stable
@GenerateDataFunctions
class LabelledTextFieldStyle(
    override val colors: LabelledTextFieldColors,
    override val metrics: LabelledTextFieldMetrics,
    override val textStyle: TextStyle,
    val textStyles: LabelledTextFieldTextStyles,
) : InputFieldStyle {

    fun asTextFieldStyle(): TextFieldStyle =
        TextFieldStyle(
            colors = colors.asTextFieldColors(),
            metrics = metrics.asTextFieldMetrics(),
            textStyle = textStyle,
        )

    companion object
}

@Immutable
@GenerateDataFunctions
class LabelledTextFieldColors(
    override val background: Color,
    override val backgroundDisabled: Color,
    override val backgroundFocused: Color,
    override val backgroundPressed: Color,
    override val backgroundHovered: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val border: Color,
    override val borderDisabled: Color,
    override val borderFocused: Color,
    override val borderPressed: Color,
    override val borderHovered: Color,
    override val caret: Color,
    override val caretDisabled: Color,
    override val caretFocused: Color,
    override val caretPressed: Color,
    override val caretHovered: Color,
    val placeholder: Color,
    val label: Color,
    val hint: Color,
) : InputFieldColors {

    fun asTextFieldColors() =
        TextFieldColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            border,
            borderDisabled,
            borderFocused,
            borderPressed,
            borderHovered,
            caret,
            caretDisabled,
            caretFocused,
            caretPressed,
            caretHovered,
            placeholder,
        )

    companion object
}

@Stable
@GenerateDataFunctions
class LabelledTextFieldMetrics(
    override val borderWidth: Dp,
    override val contentPadding: PaddingValues,
    override val cornerSize: CornerSize,
    override val minSize: DpSize,
    val labelSpacing: Dp,
    val hintSpacing: Dp,
) : InputFieldMetrics {

    fun asTextFieldMetrics() =
        TextFieldMetrics(borderWidth, contentPadding, cornerSize, minSize)

    companion object
}

@Immutable
@GenerateDataFunctions
class LabelledTextFieldTextStyles(
    val label: TextStyle,
    val hint: TextStyle,
) {

    companion object
}

val LocalLabelledTextFieldStyle = staticCompositionLocalOf<LabelledTextFieldStyle> {
    error("No LabelledTextFieldStyle provided")
}

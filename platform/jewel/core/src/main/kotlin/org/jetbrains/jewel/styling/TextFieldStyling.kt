package org.jetbrains.jewel.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

@Stable
interface TextFieldStyle : InputFieldStyle {

    override val colors: TextFieldColors
}

@Immutable
interface TextFieldColors : InputFieldColors {

    val placeholder: Color
}

val LocalTextFieldStyle = staticCompositionLocalOf<TextFieldStyle> {
    error("No TextFieldStyle provided")
}

@Stable
interface LabelledTextFieldStyle : TextFieldStyle {

    override val colors: LabelledTextFieldColors
    override val metrics: LabelledTextFieldMetrics
    val textStyles: LabelledTextFieldTextStyles
}

@Immutable
interface LabelledTextFieldColors : TextFieldColors {

    val label: Color
    val hint: Color
}

@Stable
interface LabelledTextFieldMetrics : InputFieldMetrics {

    val labelSpacing: Dp
    val hintSpacing: Dp
}

@Immutable
interface LabelledTextFieldTextStyles {

    val label: TextStyle
    val hint: TextStyle
}

val LocalLabelledTextFieldStyle = staticCompositionLocalOf<LabelledTextFieldStyle> {
    error("No LabelledTextFieldStyle provided")
}

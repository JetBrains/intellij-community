package org.jetbrains.jewel.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Stable
interface TextAreaStyle : InputFieldStyle {

    override val colors: TextAreaColors
    override val metrics: InputFieldMetrics
}

@Immutable
interface TextAreaColors : InputFieldColors {

    val placeholder: Color
}

val LocalTextAreaStyle = staticCompositionLocalOf<TextAreaStyle> {
    error("No TextAreaStyle provided")
}

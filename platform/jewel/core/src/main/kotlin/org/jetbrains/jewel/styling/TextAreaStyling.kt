package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.InputFieldState

@Stable
interface TextAreaStyle : InputFieldStyle {

    override val colors: TextAreaColors
    override val metrics: InputFieldMetrics
    val hintTextStyle: TextStyle
}

@Immutable
interface TextAreaColors : InputFieldColors {

    val placeholder: Color
    val hintContent: Color
    val hintContentDisabled: Color

    @Composable
    fun hintContentFor(state: InputFieldState) = rememberUpdatedState(
        if (state.isEnabled) hintContent else hintContentDisabled
    )
}

val LocalTextAreaStyle = staticCompositionLocalOf<TextAreaStyle> {
    error("No TextAreaStyle provided")
}

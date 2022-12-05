package org.jetbrains.jewel

import androidx.compose.ui.text.TextStyle

data class IntelliJTypography(
    val default: TextStyle,
    val button: TextStyle,
    val checkBox: TextStyle,
    val radioButton: TextStyle,
    val textField: TextStyle,
    val slider: TextStyle
) {

    companion object
}

package org.jetbrains.jewel.theme.intellij

import androidx.compose.ui.text.TextStyle

data class IntelliJTypography(
    val default: TextStyle,
    val button: TextStyle,
    val checkBox: TextStyle,
    val radioButton: TextStyle,
    val textField: TextStyle
) {

    companion object
}

package org.jetbrains.jewel.theme.intellij

import androidx.compose.ui.text.TextStyle

val IntelliJTypography.Companion.default
    get() = IntelliJTypography(
        default = TextStyle.Default,
        button = TextStyle.Default,
        checkBox = TextStyle.Default,
        radioButton = TextStyle.Default,
        textField = TextStyle.Default
    )

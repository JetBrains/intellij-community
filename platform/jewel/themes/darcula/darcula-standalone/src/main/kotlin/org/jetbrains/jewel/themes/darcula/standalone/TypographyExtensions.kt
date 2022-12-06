package org.jetbrains.jewel.themes.darcula.standalone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.IntelliJTypography

val TextStyle.Companion.IntelliJLight
    get() = TextStyle(
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal,
        fontFamily = FontFamily.Default
    )

val TextStyle.Companion.IntelliJDark
    get() = TextStyle(
        color = Color(0xFFBBBBBB),
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal,
        fontFamily = FontFamily.Default
    )

val IntelliJTypography.Companion.light
    get() = IntelliJTypography(
        default = TextStyle.IntelliJLight,
        button = TextStyle.IntelliJLight,
        checkBox = TextStyle.IntelliJLight,
        radioButton = TextStyle.IntelliJLight,
        textField = TextStyle.IntelliJLight,
        slider = TextStyle.IntelliJLight
    )

val IntelliJTypography.Companion.darcula
    get() = IntelliJTypography(
        default = TextStyle.IntelliJDark,
        button = TextStyle.IntelliJDark,
        checkBox = TextStyle.IntelliJDark,
        radioButton = TextStyle.IntelliJDark,
        textField = TextStyle.IntelliJDark,
        slider = TextStyle.IntelliJDark,
    )

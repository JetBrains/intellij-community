package org.jetbrains.jewel.theme.toolbox

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

val basicDark = Color(0xFF19191c)
val toolboxDarkPalette = Palette(
    primaryBackground = basicDark,
    secondaryBackground = Color(0xFFF4F4F4),
    text = Color.White,
    textDisabled = Color.Gray,
    textActive = Color(0xFF4CA6FF),
    controlBackground = Color(0xFF4CA6FF),
    controlBackgroundActive = Color(0xFF4CA6FF),
    controlBackgroundHover = Color(0xFF4CA6FF).copy(alpha = 0.8f).compositeOver(basicDark),
    controlBackgroundOff = Color(0xFFFFFFFF).copy(alpha = 0.4f).compositeOver(basicDark),
    controlContent = Color.White,
    controlContentActive = Color.White,
    controlAdornments = Color.LightGray,
    controlAdornmentsActive = Color(0xFF167DFF),
    controlAdornmentsHover = Color(0xFF4CA6FF).copy(0.2f).compositeOver(basicDark),
    controlBackgroundDisabled = Color.Gray,
    controlContentDisabled = Color.LightGray,
    dimmed = Color.LightGray
)

val toolboxLightPalette = Palette(
    primaryBackground = Color.White,
    secondaryBackground = Color(0xFFF4F4F4),
    text = Color(0xFF19191c),
    textDisabled = Color.Gray,
    textActive = Color(0xFF167DFF),
    controlBackground = Color(0xFF167DFF),
    controlBackgroundActive = Color(0xFF167DFF),
    controlBackgroundHover = Color(0xFF167DFF).copy(alpha = 0.8f).compositeOver(Color.White),
    controlBackgroundOff = Color(0xFF19191c).copy(alpha = 0.4f).compositeOver(Color.White),
    controlContent = Color.White,
    controlContentActive = Color.White,
    controlAdornments = Color.LightGray,
    controlAdornmentsActive = Color(0xFF167DFF),
    controlAdornmentsHover = Color(0xFF167DFF).copy(0.2f).compositeOver(Color.White),
    controlBackgroundDisabled = Color.Gray,
    controlContentDisabled = Color.LightGray,
    dimmed = Color.LightGray
)

@Immutable
data class Palette(
    val primaryBackground: Color,
    val secondaryBackground: Color,
    val text: Color,
    val textDisabled: Color,
    val textActive: Color,

    val controlContent: Color,
    val controlContentActive: Color,
    val controlBackground: Color,
    val controlBackgroundActive: Color,
    val controlBackgroundHover: Color,
    val controlBackgroundOff: Color,

    val controlContentDisabled: Color,
    val controlBackgroundDisabled: Color,

    val controlAdornments: Color,
    val controlAdornmentsActive: Color,
    val controlAdornmentsHover: Color,
    val dimmed: Color,
)

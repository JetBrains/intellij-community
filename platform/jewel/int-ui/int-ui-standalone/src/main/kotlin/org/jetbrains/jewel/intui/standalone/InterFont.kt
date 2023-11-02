package org.jetbrains.jewel.intui.standalone

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private val InterFontFamily =
    FontFamily(
        Font(resource = "/fonts/inter/Inter-Thin.ttf", weight = FontWeight.Thin),
        Font(resource = "/fonts/inter/Inter-ExtraLight.ttf", weight = FontWeight.ExtraLight),
        Font(resource = "/fonts/inter/Inter-Light.ttf", weight = FontWeight.Light),
        Font(resource = "/fonts/inter/Inter-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "/fonts/inter/Inter-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "/fonts/inter/Inter-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "/fonts/inter/Inter-Bold.ttf", weight = FontWeight.Bold),
        Font(resource = "/fonts/inter/Inter-ExtraBold.ttf", weight = FontWeight.ExtraBold),
        Font(resource = "/fonts/inter/Inter-Black.ttf", weight = FontWeight.Black),
    )

public val FontFamily.Companion.Inter: FontFamily
    get() = InterFontFamily

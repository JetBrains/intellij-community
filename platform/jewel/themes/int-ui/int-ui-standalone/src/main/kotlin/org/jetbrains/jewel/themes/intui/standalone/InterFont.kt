package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private val InterRegular = Font(resource = "/fonts/inter/Inter-Regular.ttf", weight = FontWeight.Normal)

private val InterBold = Font(resource = "/fonts/inter/Inter-Bold.ttf", weight = FontWeight.Bold)

private val InterMedium = Font(resource = "/fonts/inter/Inter-Medium.ttf", weight = FontWeight.Medium)

private val InterSemiBold = Font(resource = "/fonts/inter/Inter-SemiBold.ttf", weight = FontWeight.SemiBold)

private val InterLight = Font(resource = "/fonts/inter/Inter-Light.ttf", weight = FontWeight.Light)

private val InterThin = Font(resource = "/fonts/inter/Inter-Thin.ttf", weight = FontWeight.Thin)

private val InterExtraLight = Font(resource = "/fonts/inter/Inter-ExtraLight.ttf", weight = FontWeight.ExtraLight)

private val InterExtraBold = Font(resource = "/fonts/inter/Inter-ExtraBold.ttf", weight = FontWeight.ExtraBold)

private val InterBlack = Font(resource = "/fonts/inter/Inter-Black.ttf", weight = FontWeight.Black)

private val Inter = FontFamily(
    InterRegular,
    InterBold,
    InterMedium,
    InterSemiBold,
    InterLight,
    InterThin,
    InterExtraLight,
    InterExtraBold
)

val FontFamily.Companion.Inter
    get() = org.jetbrains.jewel.themes.intui.standalone.Inter

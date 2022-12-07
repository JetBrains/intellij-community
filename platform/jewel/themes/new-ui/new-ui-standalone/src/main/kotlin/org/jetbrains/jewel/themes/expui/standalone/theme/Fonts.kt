package org.jetbrains.jewel.themes.expui.standalone.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

object Fonts {

    val InterRegular = Font(resource = "/fonts/Inter-Regular.ttf", weight = FontWeight.Normal)

    val InterBold = Font(resource = "/fonts/Inter-Bold.ttf", weight = FontWeight.Bold)

    val InterMedium = Font(resource = "/fonts/Inter-Medium.ttf", weight = FontWeight.Medium)

    val InterSemiBold = Font(resource = "/fonts/Inter-SemiBold.ttf", weight = FontWeight.SemiBold)

    val InterLight = Font(resource = "/fonts/Inter-Light.ttf", weight = FontWeight.Light)

    val InterThin = Font(resource = "/fonts/Inter-Thin.ttf", weight = FontWeight.Thin)

    val InterExtraLight = Font(resource = "/fonts/Inter-ExtraLight.ttf", weight = FontWeight.ExtraLight)

    val InterExtraBold = Font(resource = "/fonts/Inter-ExtraBold.ttf", weight = FontWeight.ExtraBold)

    val InterBlack = Font(resource = "/fonts/Inter-Black.ttf", weight = FontWeight.Black)

    val Inter = FontFamily(
        InterRegular,
        InterBold,
        InterMedium,
        InterSemiBold,
        InterLight,
        InterThin,
        InterExtraLight,
        InterExtraBold
    )
}

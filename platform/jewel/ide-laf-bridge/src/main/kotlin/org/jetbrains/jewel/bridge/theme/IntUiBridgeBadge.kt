package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrNull
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeMetrics
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.component.styling.BadgeStyles

internal fun readBadgeStyle(isDark: Boolean): BadgeStyles {
    val metrics =
        BadgeMetrics(cornerSize = CornerSize(100), padding = PaddingValues(horizontal = 6.dp), minHeight = 16.dp)
    val disabledColors = getDisabledColors(isDark)

    val blueColors = getBlueColors(isDark, disabledColors)
    val blueSecondaryColors = getBlueSecondaryColors(isDark, disabledColors)
    val greenColors = getGreenColors(isDark, disabledColors)
    val greenSecondaryColors = getGreenSecondaryColors(isDark, disabledColors)
    val purpleSecondaryColors = getPurpleSecondaryColors(isDark, disabledColors)
    val graySecondaryColors = getGraySecondaryColors(isDark, disabledColors)

    return BadgeStyles(
        blueSecondary = BadgeStyle(colors = blueSecondaryColors, metrics = metrics),
        blue = BadgeStyle(colors = blueColors, metrics = metrics),
        purpleSecondary = BadgeStyle(colors = purpleSecondaryColors, metrics = metrics),
        green = BadgeStyle(colors = greenColors, metrics = metrics),
        greenSecondary = BadgeStyle(colors = greenSecondaryColors, metrics = metrics),
        graySecondary = BadgeStyle(colors = graySecondaryColors, metrics = metrics),
    )
}

private fun getBlueColors(isDark: Boolean, disabledBadgeColors: DisabledBadgeColors): BadgeColors =
    getBadgeColors(
        foregroundKey = "Badge.blueForeground",
        foregroundFallbackColor = if (!isDark) Color(0xFFFFFFFF) else Color(0xFF212326),
        backgroundKey = "Badge.blueBackground",
        backgroundFallbackColor = if (!isDark) Color(0xFF3871E1) else Color(0xFF538AF9),
        disabledBadgeColors = disabledBadgeColors,
    )

private fun getBlueSecondaryColors(isDark: Boolean, disabledBadgeColors: DisabledBadgeColors): BadgeColors =
    getBadgeColors(
        foregroundKey = "Badge.blueSecondaryForeground",
        foregroundFallbackColor = if (!isDark) Color(0xFF2F5EB9) else Color(0xFFD0DFFE),
        backgroundKey = "Badge.blueSecondaryBackground",
        backgroundFallbackColor = if (!isDark) Color(0x293871E1) else Color(0xCC2E4D89),
        disabledBadgeColors = disabledBadgeColors,
    )

private fun getPurpleSecondaryColors(isDark: Boolean, disabledBadgeColors: DisabledBadgeColors): BadgeColors {
    return getBadgeColors(
        foregroundKey = "Badge.purpleSecondaryForeground",
        foregroundFallbackColor = if (!isDark) Color(0xFF6C4EBB) else Color(0xFFE2DBFC),
        backgroundKey = "Badge.purpleSecondaryBackground",
        backgroundFallbackColor = if (!isDark) Color(0x298060DB) else Color(0xCC574092),
        disabledBadgeColors = disabledBadgeColors,
    )
}

private fun getGreenColors(isDark: Boolean, disabledBadgeColors: DisabledBadgeColors): BadgeColors =
    getBadgeColors(
        foregroundKey = "Badge.greenForeground",
        foregroundFallbackColor = if (!isDark) Color(0xFFFFFFFF) else Color(0xFF212326),
        backgroundKey = "Badge.greenBackground",
        backgroundFallbackColor = if (!isDark) Color(0xFF338555) else Color(0xFF4E9D6C),
        disabledBadgeColors = disabledBadgeColors,
    )

private fun getGreenSecondaryColors(isDark: Boolean, disabledBadgeColors: DisabledBadgeColors): BadgeColors {
    return getBadgeColors(
        foregroundKey = "Badge.greenSecondaryForeground",
        foregroundFallbackColor = if (!isDark) Color(0xFF2A6E47) else Color(0xFFCDE5D1),
        backgroundKey = "Badge.greenSecondaryBackground",
        backgroundFallbackColor = if (!isDark) Color(0x29338555) else Color(0xCC29583C),
        disabledBadgeColors = disabledBadgeColors,
    )
}

private fun getGraySecondaryColors(isDark: Boolean, disabledBadgeColors: DisabledBadgeColors): BadgeColors {
    return getBadgeColors(
        foregroundKey = "Badge.graySecondaryForeground",
        foregroundFallbackColor = if (!isDark) Color(0xFF73767C) else Color(0xFFB5B7BD),
        backgroundKey = "Badge.graySecondaryBackground",
        backgroundFallbackColor = if (!isDark) Color(0x1F73767C) else Color(0x33B5B7BD),
        disabledBadgeColors = disabledBadgeColors,
    )
}

private fun getBadgeColors(
    foregroundKey: String,
    foregroundFallbackColor: Color,
    backgroundKey: String,
    backgroundFallbackColor: Color,
    disabledBadgeColors: DisabledBadgeColors,
): BadgeColors {
    val foreground = retrieveColorOrNull(foregroundKey) ?: foregroundFallbackColor
    val background = retrieveColorOrNull(backgroundKey) ?: backgroundFallbackColor

    return BadgeColors(
        background = SolidColor(background),
        backgroundDisabled = SolidColor(disabledBadgeColors.background),
        backgroundFocused = SolidColor(background),
        backgroundPressed = SolidColor(background),
        backgroundHovered = SolidColor(background),
        content = foreground,
        contentDisabled = disabledBadgeColors.content,
        contentFocused = foreground,
        contentPressed = foreground,
        contentHovered = foreground,
    )
}

private fun getDisabledColors(isDark: Boolean): DisabledBadgeColors {
    val foreground =
        retrieveColorOrUnspecified("Badge.disabledForeground").takeOrElse {
            if (!isDark) Color(0xFFB5B7BD) else Color(0xFF8B8E94)
        }
    val background =
        retrieveColorOrUnspecified("Badge.disabledBackground").takeOrElse {
            if (!isDark) Color(0x1F73767C) else Color(0x33B5B7BD)
        }

    return DisabledBadgeColors(background = background, content = foreground)
}

private data class DisabledBadgeColors(val background: Color, val content: Color)

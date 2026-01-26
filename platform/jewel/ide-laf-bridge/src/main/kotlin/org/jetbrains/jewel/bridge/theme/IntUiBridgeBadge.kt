package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeMetrics
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.component.styling.BadgeStyles

internal fun readBadgeStyle(): BadgeStyles {
    val metrics = BadgeMetrics(cornerSize = CornerSize(100), padding = PaddingValues(horizontal = 6.dp), height = 16.dp)

    val defaultColors = getDefaultColors()
    val newColors = getNewColors()
    val betaColors = getBetaColors()
    val freeColors = getFreeColors()
    val trialColors = getTrialColors()
    val informationColors = getInformationColors()

    return BadgeStyles(
        default = BadgeStyle(colors = defaultColors, metrics = metrics),
        new = BadgeStyle(colors = newColors, metrics = metrics),
        beta = BadgeStyle(colors = betaColors, metrics = metrics),
        free = BadgeStyle(colors = freeColors, metrics = metrics),
        trial = BadgeStyle(colors = trialColors, metrics = metrics),
        information = BadgeStyle(colors = informationColors, metrics = metrics),
    )
}

private data class DisabledBadgeColors(val background: Color, val content: Color)

private fun getDisabledColors(): DisabledBadgeColors {
    return if (isDark) {
        DisabledBadgeColors(background = Color(0xFFB4B8BF).copy(alpha = .20f), content = Color(0xFF868A91))
    } else {
        DisabledBadgeColors(background = Color(0xFF6C707E).copy(alpha = .12f), content = Color(0xFFA8ADBD))
    }
}

private fun getDefaultColors(): BadgeColors {
    val disabledColors = getDisabledColors()

    return if (isDark) {
        val background = Color(0xFF35538F)
        val content = Color(0xFFB5CEFF)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.80f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.80f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.80f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.80f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    } else {
        val background = Color(0xFF3574F0)
        val content = Color(0xFF2E55A3)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.16f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.16f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.16f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.16f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    }
}

private fun getNewColors(): BadgeColors {
    val disabledColors = getDisabledColors()

    return if (isDark) {
        val background = Color(0xFF548AF7)
        val content = Color(0xFF1E1F22)
        BadgeColors(
            background = SolidColor(background),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background),
            backgroundPressed = SolidColor(background),
            backgroundHovered = SolidColor(background),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    } else {
        val background = Color(0xFF3574F0)
        val content = Color(0xFFFFFFFF)
        BadgeColors(
            background = SolidColor(background),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background),
            backgroundPressed = SolidColor(background),
            backgroundHovered = SolidColor(background),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    }
}

private fun getBetaColors(): BadgeColors {
    val disabledColors = getDisabledColors()

    return if (isDark) {
        val background = Color(0xFF6C469C)
        val content = Color(0xFFE4CEFF)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.80f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.80f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.80f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.80f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    } else {
        val background = Color(0xFF834DF0)
        val content = Color(0xFF55339C)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.16f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.16f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.16f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.16f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    }
}

private fun getFreeColors(): BadgeColors {
    val disabledColors = getDisabledColors()

    return if (isDark) {
        val background = Color(0xFF57965C)
        val content = Color(0xFF1E1F22)
        BadgeColors(
            background = SolidColor(background),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background),
            backgroundPressed = SolidColor(background),
            backgroundHovered = SolidColor(background),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    } else {
        val background = Color(0xFF208A3C)
        val content = Color(0xFFFFFFFF)
        BadgeColors(
            background = SolidColor(background),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background),
            backgroundPressed = SolidColor(background),
            backgroundHovered = SolidColor(background),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    }
}

private fun getTrialColors(): BadgeColors {
    val disabledColors = getDisabledColors()

    return if (isDark) {
        val background = Color(0xFF436946)
        val content = Color(0xFFD4FAD7)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.80f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.80f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.80f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.80f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    } else {
        val background = Color(0xFF208A3C)
        val content = Color(0xFF1E6B33)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.16f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.16f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.16f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.16f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    }
}

private fun getInformationColors(): BadgeColors {
    val disabledColors = getDisabledColors()

    return if (isDark) {
        val background = Color(0xFFB4B8BF)
        val content = Color(0xFFB4B8BF)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.20f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.20f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.20f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.20f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    } else {
        val background = Color(0xFF6C707E)
        val content = Color(0xFF6C707E)
        BadgeColors(
            background = SolidColor(background.copy(alpha = 0.12f)),
            backgroundDisabled = SolidColor(disabledColors.background),
            backgroundFocused = SolidColor(background.copy(alpha = 0.12f)),
            backgroundPressed = SolidColor(background.copy(alpha = 0.12f)),
            backgroundHovered = SolidColor(background.copy(alpha = 0.12f)),
            content = content,
            contentDisabled = disabledColors.content,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
        )
    }
}

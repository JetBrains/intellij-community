package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
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

private fun getDefaultColors(): BadgeColors {
    val disabledColors = getDisabledColors()
    val foreground = retrieveColorOrUnspecified("Badge.blueSecondaryForeground")
    val background = retrieveColorOrUnspecified("Badge.blueSecondaryBackground")

    return BadgeColors(
        background = SolidColor(background),
        backgroundDisabled = SolidColor(disabledColors.background),
        backgroundFocused = SolidColor(background),
        backgroundPressed = SolidColor(background),
        backgroundHovered = SolidColor(background),
        content = foreground,
        contentDisabled = disabledColors.content,
        contentFocused = foreground,
        contentPressed = foreground,
        contentHovered = foreground,
    )
}

private fun getNewColors(): BadgeColors {
    val disabledColors = getDisabledColors()
    val foreground = retrieveColorOrUnspecified("Badge.blueForeground")
    val background = retrieveColorOrUnspecified("Badge.blueBackground")

    return BadgeColors(
        background = SolidColor(background),
        backgroundDisabled = SolidColor(disabledColors.background),
        backgroundFocused = SolidColor(background),
        backgroundPressed = SolidColor(background),
        backgroundHovered = SolidColor(background),
        content = foreground,
        contentDisabled = disabledColors.content,
        contentFocused = foreground,
        contentPressed = foreground,
        contentHovered = foreground,
    )
}

private fun getBetaColors(): BadgeColors {
    val disabledColors = getDisabledColors()
    val foreground = retrieveColorOrUnspecified("Badge.purpleSecondaryForeground")
    val background = retrieveColorOrUnspecified("Badge.purpleSecondaryBackground")

    return BadgeColors(
        background = SolidColor(background),
        backgroundDisabled = SolidColor(disabledColors.background),
        backgroundFocused = SolidColor(background),
        backgroundPressed = SolidColor(background),
        backgroundHovered = SolidColor(background),
        content = foreground,
        contentDisabled = disabledColors.content,
        contentFocused = foreground,
        contentPressed = foreground,
        contentHovered = foreground,
    )
}

private fun getFreeColors(): BadgeColors {
    val disabledColors = getDisabledColors()
    val foreground = retrieveColorOrUnspecified("Badge.greenForeground")
    val background = retrieveColorOrUnspecified("Badge.greenBackground")

    return BadgeColors(
        background = SolidColor(background),
        backgroundDisabled = SolidColor(disabledColors.background),
        backgroundFocused = SolidColor(background),
        backgroundPressed = SolidColor(background),
        backgroundHovered = SolidColor(background),
        content = foreground,
        contentDisabled = disabledColors.content,
        contentFocused = foreground,
        contentPressed = foreground,
        contentHovered = foreground,
    )
}

private fun getTrialColors(): BadgeColors {
    val disabledColors = getDisabledColors()
    val foreground = retrieveColorOrUnspecified("Badge.greenSecondaryForeground")
    val background = retrieveColorOrUnspecified("Badge.greenSecondaryBackground")

    return BadgeColors(
        background = SolidColor(background),
        backgroundDisabled = SolidColor(disabledColors.background),
        backgroundFocused = SolidColor(background),
        backgroundPressed = SolidColor(background),
        backgroundHovered = SolidColor(background),
        content = foreground,
        contentDisabled = disabledColors.content,
        contentFocused = foreground,
        contentPressed = foreground,
        contentHovered = foreground,
    )
}

private fun getInformationColors(): BadgeColors {
    val disabledColors = getDisabledColors()
    val foreground = retrieveColorOrUnspecified("Badge.graySecondaryForeground")
    val background = retrieveColorOrUnspecified("Badge.graySecondaryBackground")

    return BadgeColors(
        background = SolidColor(background),
        backgroundDisabled = SolidColor(disabledColors.background),
        backgroundFocused = SolidColor(background),
        backgroundPressed = SolidColor(background),
        backgroundHovered = SolidColor(background),
        content = foreground,
        contentDisabled = disabledColors.content,
        contentFocused = foreground,
        contentPressed = foreground,
        contentHovered = foreground,
    )
}

private fun getDisabledColors(): DisabledBadgeColors {
    val foreground = retrieveColorOrUnspecified("Badge.disabledForeground")
    val background = retrieveColorOrUnspecified("Badge.disabledBackground")

    return DisabledBadgeColors(background = background, content = foreground)
}

private data class DisabledBadgeColors(val background: Color, val content: Color)

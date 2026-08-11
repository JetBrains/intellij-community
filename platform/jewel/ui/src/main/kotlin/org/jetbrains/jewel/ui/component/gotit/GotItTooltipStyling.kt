// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle

/** Combines [GotItColors] and [GotItMetrics] to fully style a Got It tooltip component. */
@Stable
@GenerateDataFunctions
public class GotItTooltipStyle(
    /** The color tokens for the Got It tooltip. */
    public val colors: GotItColors,
    /** The size and spacing metrics for the Got It tooltip. */
    public val metrics: GotItMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GotItTooltipStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "GotItStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [GotItTooltipStyle]. */
    public companion object
}

/** The color tokens for a Got It tooltip component. */
@Stable
@GenerateDataFunctions
public class GotItColors(
    /** The color of the tooltip's body text. */
    public val foreground: Color,
    /** The background color of the tooltip's balloon. */
    public val background: Color,
    /** The color of the step number shown in place of an icon. */
    public val stepForeground: Color,
    /** The color of the secondary action, rendered as a link next to the primary button. */
    public val secondaryActionForeground: Color,
    /** The color of the tooltip's header text. */
    public val headerForeground: Color,
    /** The color of the balloon's border, including the arrow. */
    public val balloonBorderColor: Color,
    /** The color of the border drawn around the image, when [GotItImage.showBorder] is enabled. */
    public val imageBorderColor: Color,
    /** The color of links, both in the body text and below it. */
    public val link: Color,
    /** The text color of inline code spans in the body. */
    public val codeForeground: Color,
    /** The background color of inline code spans in the body. */
    public val codeBackground: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GotItColors

        if (foreground != other.foreground) return false
        if (background != other.background) return false
        if (stepForeground != other.stepForeground) return false
        if (secondaryActionForeground != other.secondaryActionForeground) return false
        if (headerForeground != other.headerForeground) return false
        if (balloonBorderColor != other.balloonBorderColor) return false
        if (imageBorderColor != other.imageBorderColor) return false
        if (link != other.link) return false
        if (codeForeground != other.codeForeground) return false
        if (codeBackground != other.codeBackground) return false

        return true
    }

    override fun hashCode(): Int {
        var result = foreground.hashCode()
        result = 31 * result + background.hashCode()
        result = 31 * result + stepForeground.hashCode()
        result = 31 * result + secondaryActionForeground.hashCode()
        result = 31 * result + headerForeground.hashCode()
        result = 31 * result + balloonBorderColor.hashCode()
        result = 31 * result + imageBorderColor.hashCode()
        result = 31 * result + link.hashCode()
        result = 31 * result + codeForeground.hashCode()
        result = 31 * result + codeBackground.hashCode()
        return result
    }

    override fun toString(): String =
        "GotItColors(" +
            "foreground=$foreground, " +
            "background=$background, " +
            "stepForeground=$stepForeground, " +
            "secondaryActionForeground=$secondaryActionForeground, " +
            "headerForeground=$headerForeground, " +
            "borderColor=$balloonBorderColor, " +
            "imageBorderColor=$imageBorderColor, " +
            "link=$link, " +
            "codeForeground=$codeForeground, " +
            "codeBackground=$codeBackground" +
            ")"

    /** Companion object for [GotItColors]. */
    public companion object
}

/** The size and spacing metrics for a Got It tooltip component. */
@Stable
@GenerateDataFunctions
public class GotItMetrics(
    /** The padding applied inside the balloon, around the entire tooltip content. */
    public val contentPadding: PaddingValues,
    /** The vertical spacing between the tooltip's text blocks (header, body, and link). */
    public val textPadding: Dp,
    /** The padding applied around the button row. */
    public val buttonPadding: PaddingValues,
    /** The horizontal spacing between the icon (or step number) and the text next to it. */
    public val iconPadding: Dp,
    /** The padding applied around the image, when one is present. */
    public val imagePadding: PaddingValues,
    /** The corner radius of the balloon, and of the image's border. */
    public val cornerRadius: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GotItMetrics

        if (contentPadding != other.contentPadding) return false
        if (textPadding != other.textPadding) return false
        if (buttonPadding != other.buttonPadding) return false
        if (iconPadding != other.iconPadding) return false
        if (imagePadding != other.imagePadding) return false
        if (cornerRadius != other.cornerRadius) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentPadding.hashCode()
        result = 31 * result + textPadding.hashCode()
        result = 31 * result + buttonPadding.hashCode()
        result = 31 * result + iconPadding.hashCode()
        result = 31 * result + imagePadding.hashCode()
        result = 31 * result + cornerRadius.hashCode()
        return result
    }

    override fun toString(): String =
        "GotItMetrics(" +
            "contentPadding=$contentPadding, " +
            "textPadding=$textPadding, " +
            "buttonPadding=$buttonPadding, " +
            "iconPadding=$iconPadding, " +
            "imagePadding=$imagePadding, " +
            "cornerRadius=$cornerRadius" +
            ")"

    /** Companion object for [GotItMetrics]. */
    public companion object
}

/** Provides the [GotItTooltipStyle] to use for Got It tooltips in the current composition. */
public val LocalGotItTooltipStyle: ProvidableCompositionLocal<GotItTooltipStyle> = staticCompositionLocalOf {
    error("No GotItStyle provided. Have you forgotten the theme?")
}

/** Provides the [ButtonStyle] to use for the buttons shown inside Got It tooltips in the current composition. */
public val LocalGotItButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No GotIt ButtonStyle provided. Have you forgotten the theme?")
}

internal fun fallbackGotItTooltipStyle() =
    GotItTooltipStyle(
        colors =
            GotItColors(
                foreground = Color(0xFF000000),
                background = Color(0xFFF7F7F7),
                stepForeground = Color(0xFF000000),
                secondaryActionForeground = Color(0xFF000000),
                headerForeground = Color(0xFF000000),
                balloonBorderColor = Color(0xFFABABAB),
                imageBorderColor = Color(0xFFF7F7F7),
                link = Color(0xFF88ADF7),
                codeForeground = Color(0xFFC9CCD6),
                codeBackground = Color(0xFF393B40),
            ),
        metrics =
            GotItMetrics(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                textPadding = 4.dp,
                buttonPadding = PaddingValues(top = 12.dp, bottom = 6.dp),
                iconPadding = 6.dp,
                imagePadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                cornerRadius = 8.dp,
            ),
    )

internal fun fallbackGotItTooltipButtonStyle(): ButtonStyle {
    val bg = SolidColor(Color(0xFF494B57))
    return ButtonStyle(
        colors =
            ButtonColors(
                background = bg,
                backgroundDisabled = SolidColor(Color.Unspecified),
                backgroundFocused = bg,
                backgroundPressed = SolidColor(Color(0xFF383A42)),
                backgroundHovered = SolidColor(Color(0xFF5A5D6B)),
                content = Color(0xFFFFFFFF),
                contentDisabled = Color(0xFF818594),
                contentFocused = Color(0xFFFFFFFF),
                contentPressed = Color(0xFFFFFFFF),
                contentHovered = Color(0xFFFFFFFF),
                border = bg,
                borderDisabled = SolidColor(Color.Unspecified),
                borderFocused = bg,
                borderPressed = SolidColor(Color(0xFF383A42)),
                borderHovered = SolidColor(Color(0xFF5A5D6B)),
            ),
        metrics =
            ButtonMetrics(
                cornerSize = CornerSize(4.dp),
                padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                minSize = DpSize(72.dp, 28.dp),
                borderWidth = 1.dp,
                focusOutlineExpand = Dp.Unspecified,
            ),
        focusOutlineAlignment = Stroke.Alignment.Center,
    )
}

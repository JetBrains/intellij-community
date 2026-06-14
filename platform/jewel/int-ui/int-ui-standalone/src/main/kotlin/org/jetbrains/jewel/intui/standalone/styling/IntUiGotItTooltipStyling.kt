// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.gotit.GotItColors
import org.jetbrains.jewel.ui.component.gotit.GotItMetrics
import org.jetbrains.jewel.ui.component.gotit.GotItTooltipStyle
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle

public val ButtonStyle.Companion.GotIt: IntUiGotItButtonStyleFactory
    get() = IntUiGotItButtonStyleFactory

public object IntUiGotItButtonStyleFactory {
    public fun light(
        colors: ButtonColors = ButtonColors.GotIt.light(),
        metrics: ButtonMetrics = ButtonMetrics.default(focusOutlineExpand = 0.dp, borderWidth = 0.dp),
        focusOutlineAlignment: Stroke.Alignment = Stroke.Alignment.Center,
    ): ButtonStyle = ButtonStyle(colors, metrics, focusOutlineAlignment)

    public fun dark(
        colors: ButtonColors = ButtonColors.GotIt.dark(),
        metrics: ButtonMetrics = ButtonMetrics.default(focusOutlineExpand = 0.dp),
        focusOutlineAlignment: Stroke.Alignment = Stroke.Alignment.Center,
    ): ButtonStyle = ButtonStyle(colors, metrics, focusOutlineAlignment)
}

public val ButtonColors.Companion.GotIt: IntUiGotItButtonColorFactory
    get() = IntUiGotItButtonColorFactory

public object IntUiGotItButtonColorFactory {
    public fun light(
        background: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
        backgroundDisabled: Brush = SolidColor(Color.Unspecified),
        backgroundFocused: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
        backgroundPressed: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
        backgroundHovered: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
        content: Color = IntUiLightTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        contentDisabled: Color = IntUiLightTheme.colors.grayOrNull(7) ?: Color(0xFF818594),
        contentFocused: Color = IntUiLightTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        contentPressed: Color = IntUiLightTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        contentHovered: Color = IntUiLightTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        border: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
        borderDisabled: Brush = SolidColor(Color.Unspecified),
        borderFocused: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
        borderPressed: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
        borderHovered: Brush = SolidColor(IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57)),
    ): ButtonColors =
        ButtonColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            border = border,
            borderDisabled = borderDisabled,
            borderFocused = borderFocused,
            borderPressed = borderPressed,
            borderHovered = borderHovered,
        )

    public fun dark(
        background: Brush = SolidColor(IntUiDarkTheme.colors.blueOrNull(4) ?: Color(0xFF375FAD)),
        backgroundDisabled: Brush = SolidColor(Color.Unspecified),
        backgroundFocused: Brush = SolidColor(IntUiDarkTheme.colors.blueOrNull(4) ?: Color(0xFF375FAD)),
        backgroundPressed: Brush = SolidColor(IntUiDarkTheme.colors.blueOrNull(4) ?: Color(0xFF375FAD)),
        backgroundHovered: Brush = SolidColor(IntUiDarkTheme.colors.blueOrNull(4) ?: Color(0xFF375FAD)),
        content: Color = IntUiDarkTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        contentDisabled: Color = IntUiDarkTheme.colors.gray(6),
        contentFocused: Color = IntUiDarkTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        contentPressed: Color = IntUiDarkTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        contentHovered: Color = IntUiDarkTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
        border: Brush = SolidColor(Color(0x80FFFFFF)),
        borderDisabled: Brush = SolidColor(Color.Unspecified),
        borderFocused: Brush = SolidColor(Color(0x80FFFFFF)),
        borderPressed: Brush = SolidColor(Color(0x80FFFFFF)),
        borderHovered: Brush = SolidColor(Color(0x80FFFFFF)),
    ): ButtonColors =
        ButtonColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            border = border,
            borderDisabled = borderDisabled,
            borderFocused = borderFocused,
            borderPressed = borderPressed,
            borderHovered = borderHovered,
        )
}

public fun GotItTooltipStyle.Companion.light(
    colors: GotItColors = GotItColors.light(),
    metrics: GotItMetrics = GotItMetrics.light(),
): GotItTooltipStyle = GotItTooltipStyle(colors, metrics)

public fun GotItColors.Companion.light(
    foreground: Color = IntUiLightTheme.colors.grayOrNull(9) ?: Color(0xFFC9CCD6),
    background: Color = IntUiLightTheme.colors.grayOrNull(2) ?: Color(0xFF27282E),
    stepForeground: Color = IntUiLightTheme.colors.grayOrNull(7) ?: Color(0xFF818594),
    secondaryActionForeground: Color = IntUiLightTheme.colors.grayOrNull(7) ?: Color(0xFF818594),
    headerForeground: Color = IntUiLightTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
    balloonBorderColor: Color = IntUiLightTheme.colors.grayOrNull(2) ?: Color(0xFF27282E),
    imageBorderColor: Color = IntUiLightTheme.colors.grayOrNull(4) ?: Color(0xFF494B57),
    link: Color = IntUiLightTheme.colors.blueOrNull(8) ?: Color(0xFF88ADF7),
    codeForeground: Color = IntUiLightTheme.colors.grayOrNull(9) ?: Color(0xFFC9CCD6),
    codeBackground: Color = IntUiLightTheme.colors.grayOrNull(3) ?: Color(0xFF393B40),
): GotItColors =
    GotItColors(
        foreground,
        background,
        stepForeground,
        secondaryActionForeground,
        headerForeground,
        balloonBorderColor,
        imageBorderColor,
        link,
        codeForeground,
        codeBackground,
    )

public fun GotItMetrics.Companion.light(
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    textPadding: Dp = 4.dp,
    buttonPadding: PaddingValues = PaddingValues(top = 12.dp, bottom = 6.dp),
    iconPadding: Dp = 6.dp,
    imagePadding: PaddingValues = PaddingValues(top = 4.dp, bottom = 12.dp),
    cornerRadius: Dp = 8.dp,
): GotItMetrics = GotItMetrics(contentPadding, textPadding, buttonPadding, iconPadding, imagePadding, cornerRadius)

public fun GotItTooltipStyle.Companion.dark(
    colors: GotItColors = GotItColors.dark(),
    metrics: GotItMetrics = GotItMetrics.dark(),
): GotItTooltipStyle = GotItTooltipStyle(colors, metrics)

public fun GotItColors.Companion.dark(
    foreground: Color = Color(0xCCFFFFFF),
    background: Color = IntUiDarkTheme.colors.blueOrNull(4) ?: Color(0xFF375FAD),
    stepForeground: Color = IntUiDarkTheme.colors.blueOrNull(11) ?: Color(0xFF99BBFF),
    secondaryActionForeground: Color = IntUiDarkTheme.colors.blueOrNull(11) ?: Color(0xFF99BBFF),
    headerForeground: Color = IntUiDarkTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
    borderColor: Color = IntUiDarkTheme.colors.blueOrNull(4) ?: Color(0xFF366ACE),
    imageBorderColor: Color = IntUiDarkTheme.colors.grayOrNull(3) ?: Color(0xFF393B40),
    link: Color = Color(0xCCFFFFFF),
    codeForeground: Color = IntUiDarkTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF),
    codeBackground: Color = IntUiDarkTheme.colors.blueOrNull(4) ?: Color(0xFF375FAD),
): GotItColors =
    GotItColors(
        foreground,
        background,
        stepForeground,
        secondaryActionForeground,
        headerForeground,
        borderColor,
        imageBorderColor,
        link,
        codeForeground,
        codeBackground,
    )

public fun GotItMetrics.Companion.dark(
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    textPadding: Dp = 4.dp,
    buttonPadding: PaddingValues = PaddingValues(top = 12.dp, bottom = 6.dp),
    iconPadding: Dp = 6.dp,
    imagePadding: PaddingValues = PaddingValues(top = 4.dp, bottom = 12.dp),
    cornerRadius: Dp = 8.dp,
): GotItMetrics = GotItMetrics(contentPadding, textPadding, buttonPadding, iconPadding, imagePadding, cornerRadius)

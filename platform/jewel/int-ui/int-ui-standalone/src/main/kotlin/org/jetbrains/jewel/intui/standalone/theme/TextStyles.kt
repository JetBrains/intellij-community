package org.jetbrains.jewel.intui.standalone.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.Inter
import org.jetbrains.jewel.intui.standalone.JetBrainsMono
import org.jetbrains.jewel.ui.component.Typography

public fun JewelTheme.Companion.createDefaultTextStyle(
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = FontWeight.Normal,
    fontStyle: FontStyle? = FontStyle.Normal,
    fontSynthesis: FontSynthesis? = null,
    fontFamily: FontFamily? = FontFamily.Inter,
    fontFeatureSettings: String? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    baselineShift: BaselineShift? = null,
    textGeometricTransform: TextGeometricTransform? = null,
    localeList: LocaleList? = null,
    background: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    shadow: Shadow? = null,
    drawStyle: DrawStyle? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    textDirection: TextDirection = TextDirection.Unspecified,
    lineHeight: TextUnit = fontSize * Typography.DefaultLineHeightMultiplier,
    textIndent: TextIndent? = null,
    platformStyle: PlatformTextStyle? = null,
    lineHeightStyle: LineHeightStyle? = null,
    lineBreak: LineBreak = LineBreak.Unspecified,
    hyphens: Hyphens = Hyphens.Unspecified,
    textMotion: TextMotion? = null,
): TextStyle =
    TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSynthesis = fontSynthesis,
        fontFamily = fontFamily,
        fontFeatureSettings = fontFeatureSettings,
        letterSpacing = letterSpacing,
        baselineShift = baselineShift,
        textGeometricTransform = textGeometricTransform,
        localeList = localeList,
        background = background,
        textDecoration = textDecoration,
        shadow = shadow,
        drawStyle = drawStyle,
        textAlign = textAlign,
        textDirection = textDirection,
        lineHeight = lineHeight,
        textIndent = textIndent,
        platformStyle = platformStyle,
        lineHeightStyle = lineHeightStyle,
        lineBreak = lineBreak,
        hyphens = hyphens,
        textMotion = textMotion,
    )

public fun JewelTheme.Companion.createDefaultTextStyle(
    brush: Brush?,
    alpha: Float = Float.NaN,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = FontWeight.Normal,
    fontStyle: FontStyle? = FontStyle.Normal,
    fontSynthesis: FontSynthesis? = null,
    fontFamily: FontFamily? = FontFamily.Inter,
    fontFeatureSettings: String? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    baselineShift: BaselineShift? = null,
    textGeometricTransform: TextGeometricTransform? = null,
    localeList: LocaleList? = null,
    background: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    shadow: Shadow? = null,
    drawStyle: DrawStyle? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    textDirection: TextDirection = TextDirection.Unspecified,
    lineHeight: TextUnit = fontSize * Typography.DefaultLineHeightMultiplier,
    textIndent: TextIndent? = null,
    platformStyle: PlatformTextStyle? = null,
    lineHeightStyle: LineHeightStyle? = null,
    lineBreak: LineBreak = LineBreak.Unspecified,
    hyphens: Hyphens = Hyphens.Unspecified,
    textMotion: TextMotion? = null,
): TextStyle =
    TextStyle(
        brush = brush,
        alpha = alpha,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSynthesis = fontSynthesis,
        fontFamily = fontFamily,
        fontFeatureSettings = fontFeatureSettings,
        letterSpacing = letterSpacing,
        baselineShift = baselineShift,
        textGeometricTransform = textGeometricTransform,
        localeList = localeList,
        background = background,
        textDecoration = textDecoration,
        shadow = shadow,
        drawStyle = drawStyle,
        textAlign = textAlign,
        textDirection = textDirection,
        lineHeight = lineHeight,
        textIndent = textIndent,
        platformStyle = platformStyle,
        lineHeightStyle = lineHeightStyle,
        lineBreak = lineBreak,
        hyphens = hyphens,
        textMotion = textMotion,
    )

public fun JewelTheme.Companion.createEditorTextStyle(
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = FontWeight.Normal,
    fontStyle: FontStyle? = FontStyle.Normal,
    fontSynthesis: FontSynthesis? = null,
    fontFamily: FontFamily? = FontFamily.JetBrainsMono,
    fontFeatureSettings: String? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    baselineShift: BaselineShift? = null,
    textGeometricTransform: TextGeometricTransform? = null,
    localeList: LocaleList? = null,
    background: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    shadow: Shadow? = null,
    drawStyle: DrawStyle? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    textDirection: TextDirection = TextDirection.Unspecified,
    lineHeight: TextUnit = fontSize * Typography.EditorLineHeightMultiplier,
    textIndent: TextIndent? = null,
    platformStyle: PlatformTextStyle? = null,
    lineHeightStyle: LineHeightStyle? = null,
    lineBreak: LineBreak = LineBreak.Unspecified,
    hyphens: Hyphens = Hyphens.Unspecified,
    textMotion: TextMotion? = null,
): TextStyle =
    TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSynthesis = fontSynthesis,
        fontFamily = fontFamily,
        fontFeatureSettings = fontFeatureSettings,
        letterSpacing = letterSpacing,
        baselineShift = baselineShift,
        textGeometricTransform = textGeometricTransform,
        localeList = localeList,
        background = background,
        textDecoration = textDecoration,
        shadow = shadow,
        drawStyle = drawStyle,
        textAlign = textAlign,
        textDirection = textDirection,
        lineHeight = lineHeight,
        textIndent = textIndent,
        platformStyle = platformStyle,
        lineHeightStyle = lineHeightStyle,
        lineBreak = lineBreak,
        hyphens = hyphens,
        textMotion = textMotion,
    )

public fun JewelTheme.Companion.createEditorTextStyle(
    brush: Brush?,
    alpha: Float = Float.NaN,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = FontWeight.Normal,
    fontStyle: FontStyle? = FontStyle.Normal,
    fontSynthesis: FontSynthesis? = null,
    fontFamily: FontFamily? = FontFamily.JetBrainsMono,
    fontFeatureSettings: String? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    baselineShift: BaselineShift? = null,
    textGeometricTransform: TextGeometricTransform? = null,
    localeList: LocaleList? = null,
    background: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    shadow: Shadow? = null,
    drawStyle: DrawStyle? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    textDirection: TextDirection = TextDirection.Unspecified,
    lineHeight: TextUnit = fontSize * Typography.EditorLineHeightMultiplier,
    textIndent: TextIndent? = null,
    platformStyle: PlatformTextStyle? = null,
    lineHeightStyle: LineHeightStyle? = null,
    lineBreak: LineBreak = LineBreak.Unspecified,
    hyphens: Hyphens = Hyphens.Unspecified,
    textMotion: TextMotion? = null,
): TextStyle =
    TextStyle(
        brush = brush,
        alpha = alpha,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSynthesis = fontSynthesis,
        fontFamily = fontFamily,
        fontFeatureSettings = fontFeatureSettings,
        letterSpacing = letterSpacing,
        baselineShift = baselineShift,
        textGeometricTransform = textGeometricTransform,
        localeList = localeList,
        background = background,
        textDecoration = textDecoration,
        shadow = shadow,
        drawStyle = drawStyle,
        textAlign = textAlign,
        textDirection = textDirection,
        lineHeight = lineHeight,
        textIndent = textIndent,
        platformStyle = platformStyle,
        lineHeightStyle = lineHeightStyle,
        lineBreak = lineBreak,
        hyphens = hyphens,
        textMotion = textMotion,
    )

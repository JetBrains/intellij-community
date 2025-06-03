@file:Suppress("DuplicatedCode")

package org.jetbrains.jewel.intui.standalone.theme

import androidx.annotation.Px
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.Paragraph
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
import java.awt.Font
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.Inter
import org.jetbrains.jewel.intui.standalone.JetBrainsMono
import org.jetbrains.jewel.ui.Typography
import org.jetbrains.jewel.ui.component.computeLineHeightPx

internal val DefaultFontSize = 13.sp

/**
 * Create the default text style to use in the Jewel theme.
 *
 * By default, it is the bundled [Inter][FontFamily.Companion.Inter] with a text size of 13 sp.
 *
 * @param color The text color.
 * @param fontSize The size of glyphs to use when painting the text. This may be [TextUnit.Unspecified] for inheriting
 *   from another [TextStyle].
 * @param fontWeight The typeface thickness to use when painting the text (e.g., bold).
 * @param fontStyle The typeface variant to use when drawing the letters (e.g., italic).
 * @param fontSynthesis Whether to synthesize font weight and/or style when the requested weight or style cannot be
 *   found in the provided font family.
 * @param fontFamily The font family to be used when rendering the text.
 * @param fontFeatureSettings The advanced typography settings provided by font. The format is the same as the CSS
 *   font-feature-settings attribute: https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop
 * @param letterSpacing The amount of space to add between each letter.
 * @param baselineShift The amount by which the text is shifted up from the current baseline.
 * @param textGeometricTransform The geometric transformation applied the text.
 * @param localeList The locale list used to select region-specific glyphs.
 * @param background The background color for the text.
 * @param textDecoration The decorations to paint on the text (e.g., an underline).
 * @param shadow The shadow effect applied on the text.
 * @param drawStyle Drawing style of text, whether fill in the text while drawing or stroke around the edges.
 * @param textAlign The alignment of the text within the lines of the paragraph.
 * @param textDirection The algorithm to be used to resolve the final text and paragraph direction: Left To Right or
 *   Right To Left. If no value is provided the system will use the [androidx.compose.ui.unit.LayoutDirection] as the
 *   primary signal.
 * @param lineHeight Line height for the [Paragraph] in [TextUnit] unit, e.g. SP or EM.
 * @param textIndent The indentation of the paragraph.
 * @param platformStyle Platform specific [TextStyle] parameters.
 * @param lineHeightStyle the configuration for line height such as vertical alignment of the line, whether to apply
 *   additional space as a result of line height to top of first line top and bottom of last line. The configuration is
 *   applied only when a [lineHeight] is defined. When null, [LineHeightStyle.Default] is used.
 * @param lineBreak The line breaking configuration for the text.
 * @param hyphens The configuration of hyphenation.
 * @param textMotion Text character placement, whether to optimize for animated or static text.
 */
public fun JewelTheme.Companion.createDefaultTextStyle(
    color: Color = Color.Unspecified,
    fontSize: TextUnit = DefaultFontSize,
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
    lineHeight: TextUnit = computeInterLineHeightPx(fontSize.spValue()).sp,
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

/**
 * Create the default text style to use in the Jewel theme.
 *
 * By default, it is the bundled [Inter][FontFamily.Companion.Inter] with a text size of 13 sp.
 *
 * @param brush The brush to use when painting the text. If brush is given as null, it will be treated as unspecified.
 *   It is equivalent to calling the alternative color constructor with [Color.Unspecified]
 * @param alpha Opacity to be applied to [brush] from 0.0f to 1.0f representing fully transparent to fully opaque
 *   respectively.
 * @param fontSize The size of glyphs to use when painting the text. This may be [TextUnit.Unspecified] for inheriting
 *   from another [TextStyle].
 * @param fontWeight The typeface thickness to use when painting the text (e.g., bold).
 * @param fontStyle The typeface variant to use when drawing the letters (e.g., italic).
 * @param fontSynthesis Whether to synthesize font weight and/or style when the requested weight or style cannot be
 *   found in the provided font family.
 * @param fontFamily The font family to be used when rendering the text.
 * @param fontFeatureSettings The advanced typography settings provided by font. The format is the same as the CSS
 *   font-feature-settings attribute: https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop
 * @param letterSpacing The amount of space to add between each letter.
 * @param baselineShift The amount by which the text is shifted up from the current baseline.
 * @param textGeometricTransform The geometric transformation applied the text.
 * @param localeList The locale list used to select region-specific glyphs.
 * @param background The background color for the text.
 * @param textDecoration The decorations to paint on the text (e.g., an underline).
 * @param shadow The shadow effect applied on the text.
 * @param drawStyle Drawing style of text, whether fill in the text while drawing or stroke around the edges.
 * @param textAlign The alignment of the text within the lines of the paragraph.
 * @param textDirection The algorithm to be used to resolve the final text and paragraph direction: Left To Right or
 *   Right To Left. If no value is provided the system will use the [androidx.compose.ui.unit.LayoutDirection] as the
 *   primary signal.
 * @param lineHeight Line height for the [Paragraph] in [TextUnit] unit, e.g. SP or EM.
 * @param textIndent The indentation of the paragraph.
 * @param platformStyle Platform specific [TextStyle] parameters.
 * @param lineHeightStyle the configuration for line height such as vertical alignment of the line, whether to apply
 *   additional space as a result of line height to top of first line top and bottom of last line. The configuration is
 *   applied only when a [lineHeight] is defined.
 * @param lineBreak The line breaking configuration for the text.
 * @param hyphens The configuration of hyphenation.
 * @param textMotion Text character placement, whether to optimize for animated or static text.
 */
public fun JewelTheme.Companion.createDefaultTextStyle(
    brush: Brush?,
    alpha: Float = Float.NaN,
    fontSize: TextUnit = DefaultFontSize,
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
    lineHeight: TextUnit = computeInterLineHeightPx(fontSize.spValue()).sp,
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

/**
 * Create the editor text style to use in the Jewel theme.
 *
 * By default, it is the bundled [Inter][FontFamily.Companion.JetBrainsMono] with a text size of 13 sp and a line height
 * multiplier of [EditorLineHeightMultiplier] (1.2).
 *
 * @param color The text color.
 * @param fontSize The size of glyphs to use when painting the text. This may be [TextUnit.Unspecified] for inheriting
 *   from another [TextStyle].
 * @param fontWeight The typeface thickness to use when painting the text (e.g., bold).
 * @param fontStyle The typeface variant to use when drawing the letters (e.g., italic).
 * @param fontSynthesis Whether to synthesize font weight and/or style when the requested weight or style cannot be
 *   found in the provided font family.
 * @param fontFamily The font family to be used when rendering the text.
 * @param fontFeatureSettings The advanced typography settings provided by font. The format is the same as the CSS
 *   font-feature-settings attribute: https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop
 * @param letterSpacing The amount of space to add between each letter.
 * @param baselineShift The amount by which the text is shifted up from the current baseline.
 * @param textGeometricTransform The geometric transformation applied the text.
 * @param localeList The locale list used to select region-specific glyphs.
 * @param background The background color for the text.
 * @param textDecoration The decorations to paint on the text (e.g., an underline).
 * @param shadow The shadow effect applied on the text.
 * @param drawStyle Drawing style of text, whether fill in the text while drawing or stroke around the edges.
 * @param textAlign The alignment of the text within the lines of the paragraph.
 * @param textDirection The algorithm to be used to resolve the final text and paragraph direction: Left To Right or
 *   Right To Left. If no value is provided the system will use the [androidx.compose.ui.unit.LayoutDirection] as the
 *   primary signal.
 * @param lineHeight Line height for the [Paragraph] in [TextUnit] unit, e.g. SP or EM.
 * @param textIndent The indentation of the paragraph.
 * @param platformStyle Platform specific [TextStyle] parameters.
 * @param lineHeightStyle the configuration for line height such as vertical alignment of the line, whether to apply
 *   additional space as a result of line height to top of first line top and bottom of last line. The configuration is
 *   applied only when a [lineHeight] is defined. When null, [LineHeightStyle.Default] is used.
 * @param lineBreak The line breaking configuration for the text.
 * @param hyphens The configuration of hyphenation.
 * @param textMotion Text character placement, whether to optimize for animated or static text.
 */
public fun JewelTheme.Companion.createEditorTextStyle(
    color: Color = Color.Unspecified,
    fontSize: TextUnit = DefaultFontSize,
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
    lineHeight: TextUnit = (computeJetBrainsMonoLineHeightPx(fontSize.spValue()) * EditorLineHeightMultiplier).sp,
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

/**
 * Create the editor text style to use in the Jewel theme.
 *
 * By default, it is the bundled [Inter][FontFamily.Companion.JetBrainsMono] with a text size of 13 sp and a line height
 * multiplier of [EditorLineHeightMultiplier] (1.2).
 *
 * @param brush The brush to use when painting the text. If brush is given as null, it will be treated as unspecified.
 *   It is equivalent to calling the alternative color constructor with [Color.Unspecified]
 * @param alpha Opacity to be applied to [brush] from 0.0f to 1.0f representing fully transparent to fully opaque
 *   respectively.
 * @param fontSize The size of glyphs to use when painting the text. This may be [TextUnit.Unspecified] for inheriting
 *   from another [TextStyle].
 * @param fontWeight The typeface thickness to use when painting the text (e.g., bold).
 * @param fontStyle The typeface variant to use when drawing the letters (e.g., italic).
 * @param fontSynthesis Whether to synthesize font weight and/or style when the requested weight or style cannot be
 *   found in the provided font family.
 * @param fontFamily The font family to be used when rendering the text.
 * @param fontFeatureSettings The advanced typography settings provided by font. The format is the same as the CSS
 *   font-feature-settings attribute: https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop
 * @param letterSpacing The amount of space to add between each letter.
 * @param baselineShift The amount by which the text is shifted up from the current baseline.
 * @param textGeometricTransform The geometric transformation applied the text.
 * @param localeList The locale list used to select region-specific glyphs.
 * @param background The background color for the text.
 * @param textDecoration The decorations to paint on the text (e.g., an underline).
 * @param shadow The shadow effect applied on the text.
 * @param drawStyle Drawing style of text, whether fill in the text while drawing or stroke around the edges.
 * @param textAlign The alignment of the text within the lines of the paragraph.
 * @param textDirection The algorithm to be used to resolve the final text and paragraph direction: Left To Right or
 *   Right To Left. If no value is provided the system will use the [androidx.compose.ui.unit.LayoutDirection] as the
 *   primary signal.
 * @param lineHeight Line height for the [Paragraph] in [TextUnit] unit, e.g. SP or EM.
 * @param textIndent The indentation of the paragraph.
 * @param platformStyle Platform specific [TextStyle] parameters.
 * @param lineHeightStyle the configuration for line height such as vertical alignment of the line, whether to apply
 *   additional space as a result of line height to top of first line top and bottom of last line. The configuration is
 *   applied only when a [lineHeight] is defined.
 * @param lineBreak The line breaking configuration for the text.
 * @param hyphens The configuration of hyphenation.
 * @param textMotion Text character placement, whether to optimize for animated or static text.
 */
public fun JewelTheme.Companion.createEditorTextStyle(
    brush: Brush?,
    alpha: Float = Float.NaN,
    fontSize: TextUnit = DefaultFontSize,
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
    lineHeight: TextUnit = (computeJetBrainsMonoLineHeightPx(fontSize.spValue()) * EditorLineHeightMultiplier).sp,
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

private fun TextUnit.spValue(): Float {
    check(isSp) { "Only Sp font sizes are supported" }
    return value
}

@Px
private fun computeInterLineHeightPx(@Px fontSize: Float): Int {
    val stream = Typography::class.java.classLoader.getResourceAsStream("fonts/inter/Inter-Regular.ttf")
    val font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(fontSize)
    return font.computeLineHeightPx()
}

@Px
private fun computeJetBrainsMonoLineHeightPx(@Px fontSize: Float): Int {
    val stream = Typography::class.java.classLoader.getResourceAsStream("fonts/inter/Inter-Regular.ttf")
    val font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(fontSize)
    return (font.computeLineHeightPx() * EDITOR_LINE_HEIGHT_FACTOR).roundToInt()
}

/**
 * Compensates for some difference in how line height is applied between Skia and Swing. Matches perfectly with the
 * default editor font.
 */
private const val EDITOR_LINE_HEIGHT_FACTOR = 0.87f

@ExperimentalJewelApi
@Suppress("ktlint:standard:property-naming", "TopLevelPropertyNaming")
public const val EditorLineHeightMultiplier: Float = 1.2f

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.typography

/**
 * Displays info text, based on [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info]
 * and [`Typography.medium`][org.jetbrains.jewel.ui.Typography.medium].
 *
 * If you are setting your own style, you may want to consider first retrieving
 * [`Typography.medium`][org.jetbrains.jewel.ui.Typography.medium], and using [TextStyle.copy] to set the color to
 * [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info], while keeping any
 * theme-defined attributes, only modifying the specific attributes you want to override.
 *
 * For ease of use, commonly used parameters from [TextStyle] are also present here. The order of precedence is as
 * follows:
 * - If a parameter is explicitly set here (i.e., it is _not_ `null` or [TextUnit.Unspecified]), then this parameter
 *   will be used.
 * - If a parameter is _not_ set, (`null` or [TextUnit.Unspecified]), then the corresponding value from [style] will be
 *   used instead.
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param color [Color] to apply to the text. If [Color.Unspecified], and [style] has no color set, this will be
 *   [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info].
 * @param fontSize The size of glyphs to use when painting the text. See [TextStyle.fontSize].
 * @param fontStyle The typeface variant to use when drawing the letters (e.g., italic). See [TextStyle.fontStyle].
 * @param fontWeight The typeface thickness to use when painting the text (e.g., [FontWeight.Bold]).
 * @param fontFamily The font family to be used when rendering the text. See [TextStyle.fontFamily].
 * @param letterSpacing The amount of space to add between each letter. See [TextStyle.letterSpacing].
 * @param textDecoration The decorations to paint on the text (e.g., an underline). See [TextStyle.textDecoration].
 * @param textAlign The alignment of the text within the lines of the paragraph. See [TextStyle.textAlign].
 * @param lineHeight Line height for the paragraph in [TextUnit] unit, e.g., SP or EM. See [TextStyle.lineHeight].
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the text will be
 *   positioned as if there was unlimited horizontal space. If [softWrap] is false, [overflow] and [textAlign] may have
 *   unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary. If the text exceeds
 *   the given number of lines, it will be truncated according to [overflow] and [softWrap]. It is required that
 *   [maxLines] >= 1.
 * @param inlineContent A map to store composables that replaces certain ranges of the text. It's used to insert
 *   composables into the text layout. Check [InlineTextContent] for more information.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A [TextLayoutResult] object that
 *   callback provides contains paragraph information, size of the text, baselines and other details. The callback can
 *   be used to add additional decoration or functionality to the text. For example, to draw selection around the text.
 * @param style Style configuration for the text such as color, font, line height, etc.
 */
@Composable
public fun InfoText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = JewelTheme.globalColors.text.info,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = JewelTheme.typography.medium,
) {
    Text(
        text,
        modifier,
        color,
        fontSize,
        fontStyle,
        fontWeight,
        fontFamily,
        letterSpacing,
        textDecoration,
        textAlign,
        lineHeight,
        overflow,
        softWrap,
        maxLines,
        inlineContent,
        onTextLayout,
        style,
    )
}

/**
 * Displays info text, based on [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info]
 * and [`Typography.medium`][org.jetbrains.jewel.ui.Typography.medium].
 *
 * If you are setting your own style, you may want to consider first retrieving
 * [`Typography.medium`][org.jetbrains.jewel.ui.Typography.medium], and using [TextStyle.copy] to set the color to
 * [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info], while keeping any
 * theme-defined attributes, only modifying the specific attributes you want to override.
 *
 * For ease of use, commonly used parameters from [TextStyle] are also present here. The order of precedence is as
 * follows:
 * - If a parameter is explicitly set here (i.e., it is _not_ `null` or [TextUnit.Unspecified]), then this parameter
 *   will be used.
 * - If a parameter is _not_ set, (`null` or [TextUnit.Unspecified]), then the corresponding value from [style] will be
 *   used instead.
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param color [Color] to apply to the text. If [Color.Unspecified], and [style] has no color set, this will be
 *   [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info].
 * @param fontSize The size of glyphs to use when painting the text. See [TextStyle.fontSize].
 * @param fontStyle The typeface variant to use when drawing the letters (e.g., italic). See [TextStyle.fontStyle].
 * @param fontWeight The typeface thickness to use when painting the text (e.g., [FontWeight.Bold]).
 * @param fontFamily The font family to be used when rendering the text. See [TextStyle.fontFamily].
 * @param letterSpacing The amount of space to add between each letter. See [TextStyle.letterSpacing].
 * @param textDecoration The decorations to paint on the text (e.g., an underline). See [TextStyle.textDecoration].
 * @param textAlign The alignment of the text within the lines of the paragraph. See [TextStyle.textAlign].
 * @param lineHeight Line height for the paragraph in [TextUnit] unit, e.g., SP or EM. See [TextStyle.lineHeight].
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the text will be
 *   positioned as if there was unlimited horizontal space. If [softWrap] is false, [overflow] and [textAlign] may have
 *   unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary. If the text exceeds
 *   the given number of lines, it will be truncated according to [overflow] and [softWrap]. It is required that
 *   [maxLines] >= 1.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A [TextLayoutResult] object that
 *   callback provides contains paragraph information, size of the text, baselines and other details. The callback can
 *   be used to add additional decoration or functionality to the text. For example, to draw selection around the text.
 * @param style Style configuration for the text such as color, font, line height, etc.
 */
@Composable
public fun InfoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = JewelTheme.globalColors.text.info,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = JewelTheme.typography.medium,
) {
    Text(
        text,
        modifier,
        color,
        fontSize,
        fontStyle,
        fontWeight,
        fontFamily,
        letterSpacing,
        textDecoration,
        textAlign,
        lineHeight,
        overflow,
        softWrap,
        maxLines,
        onTextLayout,
        style,
    )
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.markdown.extensions.markdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.markdownProcessor
import org.jetbrains.jewel.markdown.extensions.markdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * High-level element that renders Markdown text.
 *
 * @param text The text to be displayed.
 * @param enabled True if the block should be enabled, false otherwise.
 * @param modifier The modifier to be applied to the composable.
 * @param color [Color] to apply to the text. If [Color.Unspecified], and [style] has no color set, this will be
 *   [LocalContentColor].
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
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A [TextLayoutResult] object that
 *   callback provides contains paragraph information, size of the text, baselines and other details. The callback can
 *   be used to add additional decoration or functionality to the text. For example, to draw selection around the text.
 * @param onUrlClick The callback invoked when the user clicks on a URL.
 * @param styling The [`Paragraph`][MarkdownStyling.Paragraph] styling to use to render.
 * @param processor The processor to use for parsing the Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 */
@Composable
public fun MarkdownText(
    @Language("Markdown") text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
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
    onUrlClick: (String) -> Unit = LocalUriHandler.current::openUri,
    styling: MarkdownStyling.Paragraph = JewelTheme.markdownStyling.paragraph,
    processor: MarkdownProcessor = JewelTheme.markdownProcessor,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    val paragraph =
        remember(text, processor) {
            processor.processMarkdownDocument(text).filterIsInstance<MarkdownBlock.Paragraph>().first()
        }

    MarkdownText(
        paragraph = paragraph,
        modifier = modifier,
        enabled = enabled,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        onUrlClick = onUrlClick,
        styling = styling,
        blockRenderer = blockRenderer,
    )
}

/**
 * High-level element that renders Markdown text.
 *
 * @param paragraph The paragraph to render.
 * @param enabled True if the block should be enabled, false otherwise.
 * @param modifier The modifier to be applied to the composable.
 * @param color [Color] to apply to the text. If [Color.Unspecified], and [style] has no color set, this will be
 *   [LocalContentColor].
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
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A [TextLayoutResult] object that
 *   callback provides contains paragraph information, size of the text, baselines and other details. The callback can
 *   be used to add additional decoration or functionality to the text. For example, to draw selection around the text.
 * @param onUrlClick The callback invoked when the user clicks on a URL.
 * @param styling The [`Paragraph`][MarkdownStyling.Paragraph] styling to use to render.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 */
@Composable
public fun MarkdownText(
    paragraph: MarkdownBlock.Paragraph,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
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
    onUrlClick: (String) -> Unit = LocalUriHandler.current::openUri,
    styling: MarkdownStyling.Paragraph = JewelTheme.markdownStyling.paragraph,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    val parametersTextStyle by
        rememberUpdatedState(
            TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
                lineHeight = lineHeight,
                fontFamily = fontFamily,
                textDecoration = textDecoration,
                fontStyle = fontStyle,
                letterSpacing = letterSpacing,
            )
        )

    val parametersSpanStyle by remember { derivedStateOf { parametersTextStyle.toSpanStyle() } }

    val mergedStyling by remember {
        derivedStateOf {
            MarkdownStyling.Paragraph(
                inlinesStyling =
                    InlinesStyling(
                        textStyle = styling.inlinesStyling.textStyle.merge(parametersTextStyle),
                        inlineCode = styling.inlinesStyling.inlineCode.merge(parametersSpanStyle),
                        link = styling.inlinesStyling.link.merge(parametersSpanStyle),
                        linkDisabled = styling.inlinesStyling.linkDisabled.merge(parametersSpanStyle),
                        linkFocused = styling.inlinesStyling.linkFocused.merge(parametersSpanStyle),
                        linkHovered = styling.inlinesStyling.linkHovered.merge(parametersSpanStyle),
                        linkPressed = styling.inlinesStyling.linkPressed.merge(parametersSpanStyle),
                        linkVisited = styling.inlinesStyling.linkVisited.merge(parametersSpanStyle),
                        emphasis = styling.inlinesStyling.emphasis.merge(parametersSpanStyle),
                        strongEmphasis = styling.inlinesStyling.strongEmphasis.merge(parametersSpanStyle),
                        inlineHtml = styling.inlinesStyling.inlineHtml.merge(parametersSpanStyle),
                    )
            )
        }
    }

    blockRenderer.RenderParagraph(
        block = paragraph,
        styling = mergedStyling,
        enabled = enabled,
        onUrlClick = onUrlClick,
        modifier = modifier,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
    )
}

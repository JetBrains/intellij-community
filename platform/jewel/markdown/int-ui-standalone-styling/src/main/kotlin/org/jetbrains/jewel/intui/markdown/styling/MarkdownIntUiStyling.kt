package org.jetbrains.jewel.intui.markdown.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.BlockQuote
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Indented
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Heading
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.HtmlBlock
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Image
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Unordered
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Paragraph
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.ThematicBreak

public fun MarkdownStyling.Companion.light(
    blockVerticalSpacing: Dp = 16.dp,
    paragraph: Paragraph = Paragraph.light(),
    heading: Heading = Heading.light(),
    blockQuote: BlockQuote = BlockQuote.light(),
    code: Code = Code.light(),
    list: List = List.light(),
    image: Image = Image.default(),
    thematicBreak: ThematicBreak = ThematicBreak.light(),
    htmlBlock: HtmlBlock = HtmlBlock.light(),
): MarkdownStyling =
    MarkdownStyling(
        blockVerticalSpacing,
        paragraph,
        heading,
        blockQuote,
        code,
        list,
        image,
        thematicBreak,
        htmlBlock,
    )

public fun MarkdownStyling.Companion.dark(
    blockVerticalSpacing: Dp = 16.dp,
    paragraph: Paragraph = Paragraph.dark(),
    heading: Heading = Heading.dark(),
    blockQuote: BlockQuote = BlockQuote.dark(),
    code: Code = Code.dark(),
    list: List = List.dark(),
    image: Image = Image.default(),
    thematicBreak: ThematicBreak = ThematicBreak.dark(),
    htmlBlock: HtmlBlock = HtmlBlock.dark(),
): MarkdownStyling =
    MarkdownStyling(
        blockVerticalSpacing,
        paragraph,
        heading,
        blockQuote,
        code,
        list,
        image,
        thematicBreak,
        htmlBlock,
    )

public fun Paragraph.Companion.light(
    inlinesStyling: InlinesStyling = InlinesStyling.light(),
): Paragraph = Paragraph(inlinesStyling)

public fun Paragraph.Companion.dark(
    inlinesStyling: InlinesStyling = InlinesStyling.dark(),
): Paragraph = Paragraph(inlinesStyling)

public fun Heading.Companion.light(
    h1: Heading.H1 = Heading.H1.light(),
    h2: Heading.H2 = Heading.H2.light(),
    h3: Heading.H3 = Heading.H3.light(),
    h4: Heading.H4 = Heading.H4.light(),
    h5: Heading.H5 = Heading.H5.light(),
    h6: Heading.H6 = Heading.H6.light(),
): Heading = Heading(h1, h2, h3, h4, h5, h6)

public fun Heading.Companion.dark(
    h1: Heading.H1 = Heading.H1.dark(),
    h2: Heading.H2 = Heading.H2.dark(),
    h3: Heading.H3 = Heading.H3.dark(),
    h4: Heading.H4 = Heading.H4.dark(),
    h5: Heading.H5 = Heading.H5.dark(),
    h6: Heading.H6 = Heading.H6.dark(),
): Heading = Heading(h1, h2, h3, h4, h5, h6)

public fun Heading.H1.Companion.light(
    inlinesStyling: InlinesStyling =
        InlinesStyling.light(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * 2,
                lineHeight = defaultTextSize * 2 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFFD8DEE4),
    underlineGap: Dp = 10.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H1 = Heading.H1(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

public fun Heading.H1.Companion.dark(
    inlinesStyling: InlinesStyling =
        InlinesStyling.dark(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * 2,
                lineHeight = defaultTextSize * 2 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFF21262d),
    underlineGap: Dp = 10.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H1 = Heading.H1(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

public fun Heading.H2.Companion.light(
    inlinesStyling: InlinesStyling =
        InlinesStyling.light(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * 1.5,
                lineHeight = defaultTextSize * 1.5 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFFD8DEE4),
    underlineGap: Dp = 6.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H2 = Heading.H2(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

public fun Heading.H2.Companion.dark(
    inlinesStyling: InlinesStyling =
        InlinesStyling.dark(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * 1.5,
                lineHeight = defaultTextSize * 1.5 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFF21262d),
    underlineGap: Dp = 6.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H2 = Heading.H2(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
public fun Heading.H3.Companion.light(
    inlinesStyling: InlinesStyling =
        InlinesStyling.light(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * 1.25,
                lineHeight = defaultTextSize * 1.25 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H3 = Heading.H3(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
public fun Heading.H3.Companion.dark(
    inlinesStyling: InlinesStyling =
        InlinesStyling.dark(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * 1.25,
                lineHeight = defaultTextSize * 1.25 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H3 = Heading.H3(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
public fun Heading.H4.Companion.light(
    inlinesStyling: InlinesStyling =
        InlinesStyling.light(
            defaultTextStyle.copy(
                fontSize = defaultTextSize,
                lineHeight = defaultTextSize * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H4 = Heading.H4(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
public fun Heading.H4.Companion.dark(
    inlinesStyling: InlinesStyling =
        InlinesStyling.dark(
            defaultTextStyle.copy(
                fontSize = defaultTextSize,
                lineHeight = defaultTextSize * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H4 = Heading.H4(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H5 is identical to H4 and H6
public fun Heading.H5.Companion.light(
    inlinesStyling: InlinesStyling =
        InlinesStyling.light(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * .875,
                lineHeight = defaultTextSize * .875 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H5 = Heading.H5(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H5 is identical to H4 and H6
public fun Heading.H5.Companion.dark(
    inlinesStyling: InlinesStyling =
        InlinesStyling.dark(
            defaultTextStyle.copy(
                fontSize = defaultTextSize * .875,
                lineHeight = defaultTextSize * .875 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H5 = Heading.H5(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H6 is identical to H4 and H5
public fun Heading.H6.Companion.light(
    inlinesStyling: InlinesStyling =
        InlinesStyling.light(
            defaultTextStyle.copy(
                color = Color(0xFF656d76),
                fontSize = defaultTextSize * .85,
                lineHeight = defaultTextSize * .85 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H6 = Heading.H6(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H6 is identical to H4 and H5
public fun Heading.H6.Companion.dark(
    inlinesStyling: InlinesStyling =
        InlinesStyling.dark(
            defaultTextStyle.copy(
                color = Color(0xFF848d97),
                fontSize = defaultTextSize * .85,
                lineHeight = defaultTextSize * .85 * 1.25,
                fontWeight = FontWeight.SemiBold,
            ),
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H6 = Heading.H6(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

public fun BlockQuote.Companion.light(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 4.dp,
    lineColor: Color = Color(0xFFD0D7DE),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    textColor: Color = Color(0xFF656d76),
): BlockQuote = BlockQuote(padding, lineWidth, lineColor, pathEffect, strokeCap, textColor)

public fun BlockQuote.Companion.dark(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 4.dp,
    lineColor: Color = Color.DarkGray,
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    textColor: Color = Color(0xFF848d97),
): BlockQuote = BlockQuote(padding, lineWidth, lineColor, pathEffect, strokeCap, textColor)

public fun List.Companion.light(
    ordered: Ordered = Ordered.light(),
    unordered: Unordered = Unordered.light(),
): List = List(ordered, unordered)

public fun List.Companion.dark(
    ordered: Ordered = Ordered.dark(),
    unordered: Unordered = Unordered.dark(),
): List = List(ordered, unordered)

public fun Ordered.Companion.light(
    numberStyle: TextStyle = defaultTextStyle,
    numberContentGap: Dp = 8.dp,
    numberMinWidth: Dp = 16.dp,
    numberTextAlign: TextAlign = TextAlign.End,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 16.dp),
): Ordered =
    Ordered(
        numberStyle,
        numberContentGap,
        numberMinWidth,
        numberTextAlign,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
    )

public fun Ordered.Companion.dark(
    numberStyle: TextStyle = defaultTextStyle,
    numberContentGap: Dp = 8.dp,
    numberMinWidth: Dp = 16.dp,
    numberTextAlign: TextAlign = TextAlign.End,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 16.dp),
): Ordered =
    Ordered(
        numberStyle,
        numberContentGap,
        numberMinWidth,
        numberTextAlign,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
    )

public fun Unordered.Companion.light(
    bullet: Char? = '•',
    bulletStyle: TextStyle = defaultTextStyle.copy(fontWeight = FontWeight.Black),
    bulletContentGap: Dp = 16.dp,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 16.dp),
): Unordered =
    Unordered(
        bullet,
        bulletStyle,
        bulletContentGap,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
    )

public fun Unordered.Companion.dark(
    bullet: Char? = '•',
    bulletStyle: TextStyle = defaultTextStyle.copy(fontWeight = FontWeight.Black),
    bulletContentGap: Dp = 16.dp,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 16.dp),
): Unordered =
    Unordered(
        bullet,
        bulletStyle,
        bulletContentGap,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
    )

public fun Code.Companion.light(
    indented: Indented = Indented.light(),
    fenced: Fenced = Fenced.light(),
): Code = Code(indented, fenced)

public fun Code.Companion.dark(
    indented: Indented = Indented.dark(),
    fenced: Fenced = Fenced.dark(),
): Code = Code(indented, fenced)

public fun Indented.Companion.light(
    textStyle: TextStyle =
        defaultTextStyle.copy(
            color = Color(0xFF1F2328),
            fontFamily = FontFamily.Monospace,
            fontSize = defaultTextSize * .85,
            lineHeight = defaultTextSize * .85 * 1.45,
        ),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = Color(0xFFf6f8fa),
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
): Indented =
    Indented(
        textStyle,
        padding,
        shape,
        background,
        borderWidth,
        borderColor,
        fillWidth,
        scrollsHorizontally,
    )

public fun Indented.Companion.dark(
    textStyle: TextStyle =
        defaultTextStyle.copy(
            color = Color(0xFFe6edf3),
            fontFamily = FontFamily.Monospace,
            fontSize = defaultTextSize * .85,
            lineHeight = defaultTextSize * .85 * 1.45,
        ),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = Color(0xff161b22),
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
): Indented =
    Indented(
        textStyle,
        padding,
        shape,
        background,
        borderWidth,
        borderColor,
        fillWidth,
        scrollsHorizontally,
    )

public fun Fenced.Companion.light(
    textStyle: TextStyle =
        defaultTextStyle.copy(
            color = Color(0xFF1F2328),
            fontFamily = FontFamily.Monospace,
            fontSize = defaultTextSize * .85,
            lineHeight = defaultTextSize * .85 * 1.45,
        ),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = Color(0xFFf6f8fa),
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
    infoTextStyle: TextStyle = TextStyle(color = Color.Gray, fontSize = 12.sp),
    infoPadding: PaddingValues = PaddingValues(bottom = 16.dp),
    infoPosition: InfoPosition = InfoPosition.Hide,
): Fenced =
    Fenced(
        textStyle,
        padding,
        shape,
        background,
        borderWidth,
        borderColor,
        fillWidth,
        scrollsHorizontally,
        infoTextStyle,
        infoPadding,
        infoPosition,
    )

public fun Fenced.Companion.dark(
    textStyle: TextStyle =
        defaultTextStyle.copy(
            color = Color(0xFFe6edf3),
            fontFamily = FontFamily.Monospace,
            fontSize = defaultTextSize * .85,
            lineHeight = defaultTextSize * .85 * 1.45,
        ),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = Color(0xff161b22),
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
    infoTextStyle: TextStyle = TextStyle(color = Color.Gray, fontSize = 12.sp),
    infoPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
    infoPosition: InfoPosition = InfoPosition.Hide,
): Fenced =
    Fenced(
        textStyle,
        padding,
        shape,
        background,
        borderWidth,
        borderColor,
        fillWidth,
        scrollsHorizontally,
        infoTextStyle,
        infoPadding,
        infoPosition,
    )

public fun Image.Companion.default(
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    padding: PaddingValues = PaddingValues(),
    shape: Shape = RectangleShape,
    background: Color = Color.Unspecified,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
): Image = Image(alignment, contentScale, padding, shape, background, borderWidth, borderColor)

public fun ThematicBreak.Companion.light(
    padding: PaddingValues = PaddingValues(),
    lineWidth: Dp = 2.dp,
    lineColor: Color = Color.LightGray,
): ThematicBreak = ThematicBreak(padding, lineWidth, lineColor)

public fun ThematicBreak.Companion.dark(
    padding: PaddingValues = PaddingValues(),
    lineWidth: Dp = 2.dp,
    lineColor: Color = Color.DarkGray,
): ThematicBreak = ThematicBreak(padding, lineWidth, lineColor)

public fun HtmlBlock.Companion.light(
    textStyle: TextStyle =
        defaultTextStyle.copy(color = Color.DarkGray, fontFamily = FontFamily.Monospace),
    padding: PaddingValues = PaddingValues(8.dp),
    shape: Shape = RoundedCornerShape(4.dp),
    background: Color = Color.LightGray,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.Gray,
    fillWidth: Boolean = true,
): HtmlBlock = HtmlBlock(textStyle, padding, shape, background, borderWidth, borderColor, fillWidth)

public fun HtmlBlock.Companion.dark(
    textStyle: TextStyle =
        defaultTextStyle.copy(color = Color.Gray, fontFamily = FontFamily.Monospace),
    padding: PaddingValues = PaddingValues(8.dp),
    shape: Shape = RoundedCornerShape(4.dp),
    background: Color = Color.DarkGray,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.Gray,
    fillWidth: Boolean = true,
): HtmlBlock = HtmlBlock(textStyle, padding, shape, background, borderWidth, borderColor, fillWidth)

public fun InlinesStyling.Companion.light(
    textStyle: TextStyle = defaultTextStyle,
    inlineCode: SpanStyle =
        textStyle
            .copy(
                fontSize = textStyle.fontSize * .85,
                background = Color(0xFFEFF1F2),
                fontFamily = FontFamily.Monospace,
            )
            .toSpanStyle(),
    link: SpanStyle =
        textStyle.copy(color = Color(0xFF0969DA), textDecoration = TextDecoration.Underline).toSpanStyle(),
    emphasis: SpanStyle = textStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
    strongEmphasis: SpanStyle = textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
    inlineHtml: SpanStyle = textStyle.toSpanStyle(),
    renderInlineHtml: Boolean = false,
): InlinesStyling =
    InlinesStyling(
        textStyle,
        inlineCode,
        link,
        emphasis,
        strongEmphasis,
        inlineHtml,
        renderInlineHtml,
    )

public fun InlinesStyling.Companion.dark(
    textStyle: TextStyle = defaultTextStyle,
    inlineCode: SpanStyle =
        textStyle
            .copy(
                fontSize = textStyle.fontSize * .85,
                background = Color(0xFF343941),
                fontFamily = FontFamily.Monospace,
            )
            .toSpanStyle(),
    link: SpanStyle =
        textStyle
            .copy(color = Color(0xFF2F81F7), textDecoration = TextDecoration.Underline)
            .toSpanStyle(),
    emphasis: SpanStyle = textStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
    strongEmphasis: SpanStyle = textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
    inlineHtml: SpanStyle = textStyle.toSpanStyle(),
    renderInlineHtml: Boolean = false,
): InlinesStyling =
    InlinesStyling(
        textStyle,
        inlineCode,
        link,
        emphasis,
        strongEmphasis,
        inlineHtml,
        renderInlineHtml,
    )

private val defaultTextSize = 13.sp

private val defaultTextStyle
    get() =
        JewelTheme.createDefaultTextStyle(
            fontSize = defaultTextSize,
            lineHeight = defaultTextSize * 1.5,
        )

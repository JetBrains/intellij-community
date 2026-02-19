package org.jetbrains.jewel.intui.markdown.standalone.styling

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.createEditorTextStyle
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
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered.NumberFormatStyles.NumberFormatStyle
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Unordered
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Paragraph
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.ThematicBreak

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun MarkdownStyling.Companion.light(
    baseTextStyle: TextStyle = defaultTextStyle,
    editorTextStyle: TextStyle = defaultEditorTextStyle,
    inlinesStyling: InlinesStyling = InlinesStyling.light(baseTextStyle, editorTextStyle),
    blockVerticalSpacing: Dp = 16.dp,
    paragraph: Paragraph = Paragraph.light(inlinesStyling),
    heading: Heading = Heading.light(baseTextStyle),
    blockQuote: BlockQuote = BlockQuote.light(textColor = baseTextStyle.color),
    code: Code = Code.light(editorTextStyle),
    list: List = List.light(baseTextStyle),
    image: Image = Image.default(),
    thematicBreak: ThematicBreak = ThematicBreak.light(),
    htmlBlock: HtmlBlock = HtmlBlock.light(editorTextStyle.copy(color = blockContentColorLight)),
): MarkdownStyling =
    MarkdownStyling(blockVerticalSpacing, paragraph, heading, blockQuote, code, list, image, thematicBreak, htmlBlock)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun MarkdownStyling.Companion.dark(
    baseTextStyle: TextStyle = defaultTextStyle,
    editorTextStyle: TextStyle = defaultEditorTextStyle,
    inlinesStyling: InlinesStyling = InlinesStyling.dark(defaultTextStyle, editorTextStyle),
    blockVerticalSpacing: Dp = 16.dp,
    paragraph: Paragraph = Paragraph.dark(inlinesStyling),
    heading: Heading = Heading.dark(baseTextStyle),
    blockQuote: BlockQuote = BlockQuote.dark(textColor = baseTextStyle.color),
    code: Code = Code.dark(editorTextStyle),
    list: List = List.dark(baseTextStyle),
    image: Image = Image.default(),
    thematicBreak: ThematicBreak = ThematicBreak.dark(),
    htmlBlock: HtmlBlock = HtmlBlock.dark(editorTextStyle.copy(color = blockContentColorLight)),
): MarkdownStyling =
    MarkdownStyling(blockVerticalSpacing, paragraph, heading, blockQuote, code, list, image, thematicBreak, htmlBlock)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Paragraph.Companion.light(
    inlinesStyling: InlinesStyling = InlinesStyling.light(defaultTextStyle, defaultEditorTextStyle)
): Paragraph = Paragraph(inlinesStyling)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Paragraph.Companion.dark(
    inlinesStyling: InlinesStyling = InlinesStyling.dark(defaultTextStyle, defaultEditorTextStyle)
): Paragraph = Paragraph(inlinesStyling)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.Companion.light(
    baseTextStyle: TextStyle = defaultTextStyle,
    h1: Heading.H1 =
        Heading.H1.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 2,
                lineHeight = baseTextStyle.fontSize * 2 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h2: Heading.H2 =
        Heading.H2.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.5,
                lineHeight = baseTextStyle.fontSize * 1.5 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h3: Heading.H3 =
        Heading.H3.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.25,
                lineHeight = baseTextStyle.fontSize * 1.25 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h4: Heading.H4 =
        Heading.H4.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize,
                lineHeight = baseTextStyle.fontSize * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h5: Heading.H5 =
        Heading.H5.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * .875,
                lineHeight = baseTextStyle.fontSize * .875 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h6: Heading.H6 =
        Heading.H6.light(
            baseTextStyle.copy(
                color = Color(0xFF656d76),
                fontSize = baseTextStyle.fontSize * .85,
                lineHeight = baseTextStyle.fontSize * .85 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
): Heading = Heading(h1, h2, h3, h4, h5, h6)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.Companion.dark(
    baseTextStyle: TextStyle = defaultTextStyle,
    h1: Heading.H1 =
        Heading.H1.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 2,
                lineHeight = baseTextStyle.fontSize * 2 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h2: Heading.H2 =
        Heading.H2.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.5,
                lineHeight = baseTextStyle.fontSize * 1.5 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h3: Heading.H3 =
        Heading.H3.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.25,
                lineHeight = baseTextStyle.fontSize * 1.25 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h4: Heading.H4 =
        Heading.H4.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize,
                lineHeight = baseTextStyle.fontSize * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h5: Heading.H5 =
        Heading.H5.light(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * .875,
                lineHeight = baseTextStyle.fontSize * .875 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h6: Heading.H6 =
        Heading.H6.light(
            baseTextStyle.copy(
                color = Color(0xFF656d76),
                fontSize = baseTextStyle.fontSize * .85,
                lineHeight = baseTextStyle.fontSize * .85 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
): Heading = Heading(h1, h2, h3, h4, h5, h6)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H1.Companion.light(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * 2,
            lineHeight = defaultTextSize * 2 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.light(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFFD8DEE4),
    underlineGap: Dp = 10.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H1 = Heading.H1(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H1.Companion.dark(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * 2,
            lineHeight = defaultTextSize * 2 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.dark(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFF21262d),
    underlineGap: Dp = 10.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H1 = Heading.H1(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H2.Companion.light(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * 1.5,
            lineHeight = defaultTextSize * 1.5 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.light(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFFD8DEE4),
    underlineGap: Dp = 6.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H2 = Heading.H2(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H2.Companion.dark(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * 1.5,
            lineHeight = defaultTextSize * 1.5 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.dark(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = Color(0xFF21262d),
    underlineGap: Dp = 6.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H2 = Heading.H2(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H3.Companion.light(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * 1.25,
            lineHeight = defaultTextSize * 1.25 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.light(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H3 = Heading.H3(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H3.Companion.dark(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * 1.25,
            lineHeight = defaultTextSize * 1.25 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.dark(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H3 = Heading.H3(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H4.Companion.light(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize,
            lineHeight = defaultTextSize * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.light(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H4 = Heading.H4(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H4.Companion.dark(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize,
            lineHeight = defaultTextSize * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.dark(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H4 = Heading.H4(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H5 is identical to H4 and H6
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H5.Companion.light(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * .875,
            lineHeight = defaultTextSize * .875 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.light(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H5 = Heading.H5(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H5 is identical to H4 and H6
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H5.Companion.dark(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            fontSize = defaultTextSize * .875,
            lineHeight = defaultTextSize * .875 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.dark(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H5 = Heading.H5(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H6 is identical to H4 and H5
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H6.Companion.light(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            color = Color(0xFF656d76),
            fontSize = defaultTextSize * .85,
            lineHeight = defaultTextSize * .85 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.light(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H6 = Heading.H6(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// H6 is identical to H4 and H5
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H6.Companion.dark(
    baseTextStyle: TextStyle =
        defaultTextStyle.copy(
            color = Color(0xFF656d76),
            fontSize = defaultTextSize * .85,
            lineHeight = defaultTextSize * .85 * 1.25,
            fontWeight = FontWeight.SemiBold,
        ),
    inlinesStyling: InlinesStyling = InlinesStyling.dark(baseTextStyle, defaultEditorTextStyle),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H6 = Heading.H6(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun BlockQuote.Companion.light(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 4.dp,
    lineColor: Color = Color(0xFFD0D7DE),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    textColor: Color = Color(0xFF656d76),
): BlockQuote = BlockQuote(padding, lineWidth, lineColor, pathEffect, strokeCap, textColor)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun BlockQuote.Companion.dark(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 4.dp,
    lineColor: Color = Color.DarkGray,
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    textColor: Color = Color(0xFF848d97),
): BlockQuote = BlockQuote(padding, lineWidth, lineColor, pathEffect, strokeCap, textColor)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun List.Companion.light(
    baseTextStyle: TextStyle = defaultTextStyle,
    ordered: Ordered =
        Ordered.light(
            numberStyle = baseTextStyle,
            numberFormatStyles =
                Ordered.NumberFormatStyles(
                    firstLevel = NumberFormatStyle.Decimal,
                    secondLevel = NumberFormatStyle.Roman,
                    thirdLevel = NumberFormatStyle.Alphabetical,
                ),
        ),
    unordered: Unordered =
        Unordered.light(
            bulletStyle = baseTextStyle.copy(fontWeight = FontWeight.Black),
            bulletCharStyles = Unordered.BulletCharStyles(firstLevel = '•', secondLevel = '◦', thirdLevel = '▪'),
        ),
): List = List(ordered, unordered)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun List.Companion.dark(
    baseTextStyle: TextStyle = defaultTextStyle,
    ordered: Ordered =
        Ordered.dark(
            numberStyle = baseTextStyle,
            numberFormatStyles =
                Ordered.NumberFormatStyles(
                    firstLevel = NumberFormatStyle.Decimal,
                    secondLevel = NumberFormatStyle.Roman,
                    thirdLevel = NumberFormatStyle.Alphabetical,
                ),
        ),
    unordered: Unordered =
        Unordered.dark(
            bulletStyle = baseTextStyle.copy(fontWeight = FontWeight.Black),
            bulletCharStyles = Unordered.BulletCharStyles(firstLevel = '•', secondLevel = '◦', thirdLevel = '▪'),
        ),
): List = List(ordered, unordered)

@ApiStatus.Experimental
@ExperimentalJewelApi
@Deprecated("Please, use the overload with numberFormatStyles.")
public fun Ordered.Companion.light(
    numberStyle: TextStyle = defaultTextStyle,
    numberContentGap: Dp = 4.dp,
    numberMinWidth: Dp = 16.dp,
    numberTextAlign: TextAlign = TextAlign.End,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
): Ordered =
    Ordered(
        numberStyle,
        numberContentGap,
        numberMinWidth,
        numberTextAlign,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        Ordered.NumberFormatStyles(
            firstLevel = NumberFormatStyle.Decimal,
            secondLevel = NumberFormatStyle.Roman,
            thirdLevel = NumberFormatStyle.Alphabetical,
        ),
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Ordered.Companion.light(
    numberStyle: TextStyle = defaultTextStyle,
    numberContentGap: Dp = 4.dp,
    numberMinWidth: Dp = 16.dp,
    numberTextAlign: TextAlign = TextAlign.End,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
    numberFormatStyles: Ordered.NumberFormatStyles =
        Ordered.NumberFormatStyles(
            firstLevel = NumberFormatStyle.Decimal,
            secondLevel = NumberFormatStyle.Roman,
            thirdLevel = NumberFormatStyle.Alphabetical,
        ),
): Ordered =
    Ordered(
        numberStyle,
        numberContentGap,
        numberMinWidth,
        numberTextAlign,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        numberFormatStyles,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
@Deprecated("Please, use the overload with numberFormatStyles.")
public fun Ordered.Companion.dark(
    numberStyle: TextStyle = defaultTextStyle,
    numberContentGap: Dp = 4.dp,
    numberMinWidth: Dp = 16.dp,
    numberTextAlign: TextAlign = TextAlign.End,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
): Ordered =
    Ordered(
        numberStyle,
        numberContentGap,
        numberMinWidth,
        numberTextAlign,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        Ordered.NumberFormatStyles(
            firstLevel = NumberFormatStyle.Decimal,
            secondLevel = NumberFormatStyle.Roman,
            thirdLevel = NumberFormatStyle.Alphabetical,
        ),
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Ordered.Companion.dark(
    numberStyle: TextStyle = defaultTextStyle,
    numberContentGap: Dp = 4.dp,
    numberMinWidth: Dp = 16.dp,
    numberTextAlign: TextAlign = TextAlign.End,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
    numberFormatStyles: Ordered.NumberFormatStyles =
        Ordered.NumberFormatStyles(
            firstLevel = NumberFormatStyle.Decimal,
            secondLevel = NumberFormatStyle.Roman,
            thirdLevel = NumberFormatStyle.Alphabetical,
        ),
): Ordered =
    Ordered(
        numberStyle,
        numberContentGap,
        numberMinWidth,
        numberTextAlign,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        numberFormatStyles,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
@Deprecated("Please, use the version with bulletCharStyles.")
public fun Unordered.Companion.light(
    bullet: Char? = '•',
    bulletStyle: TextStyle = defaultTextStyle.copy(fontWeight = FontWeight.Black),
    bulletContentGap: Dp = 4.dp,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
    markerMinWidth: Dp = 16.dp,
): Unordered =
    Unordered(
        bullet,
        bulletStyle,
        bulletContentGap,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        markerMinWidth,
        Unordered.BulletCharStyles(firstLevel = '•', secondLevel = '◦', thirdLevel = '▪'),
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Unordered.Companion.light(
    bullet: Char? = '•',
    bulletStyle: TextStyle = defaultTextStyle.copy(fontWeight = FontWeight.Black),
    bulletContentGap: Dp = 4.dp,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
    markerMinWidth: Dp = 16.dp,
    bulletCharStyles: Unordered.BulletCharStyles? =
        Unordered.BulletCharStyles(firstLevel = '•', secondLevel = '◦', thirdLevel = '▪'),
): Unordered =
    Unordered(
        bullet,
        bulletStyle,
        bulletContentGap,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        markerMinWidth,
        bulletCharStyles,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
@Deprecated("Please, use the version with bulletCharStyles.")
public fun Unordered.Companion.dark(
    bullet: Char? = '•',
    bulletStyle: TextStyle = defaultTextStyle.copy(fontWeight = FontWeight.Black),
    bulletContentGap: Dp = 4.dp,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
    markerMinWidth: Dp = 16.dp,
): Unordered =
    Unordered(
        bullet,
        bulletStyle,
        bulletContentGap,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        markerMinWidth,
        Unordered.BulletCharStyles(firstLevel = '•', secondLevel = '◦', thirdLevel = '▪'),
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Unordered.Companion.dark(
    bullet: Char? = '•',
    bulletStyle: TextStyle = defaultTextStyle.copy(fontWeight = FontWeight.Black),
    bulletContentGap: Dp = 4.dp,
    itemVerticalSpacing: Dp = 16.dp,
    itemVerticalSpacingTight: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(start = 6.dp),
    markerMinWidth: Dp = 16.dp,
    bulletCharStyles: Unordered.BulletCharStyles? =
        Unordered.BulletCharStyles(firstLevel = '•', secondLevel = '◦', thirdLevel = '▪'),
): Unordered =
    Unordered(
        bullet,
        bulletStyle,
        bulletContentGap,
        itemVerticalSpacing,
        itemVerticalSpacingTight,
        padding,
        markerMinWidth,
        bulletCharStyles,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Code.Companion.light(
    editorTextStyle: TextStyle = defaultEditorTextStyle.copy(color = blockContentColorLight),
    indented: Indented = Indented.light(editorTextStyle),
    fenced: Fenced = Fenced.light(editorTextStyle),
): Code = Code(indented, fenced)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Code.Companion.dark(
    editorTextStyle: TextStyle = defaultEditorTextStyle.copy(color = blockContentColorDark),
    indented: Indented = Indented.dark(editorTextStyle),
    fenced: Fenced = Fenced.dark(editorTextStyle),
): Code = Code(indented, fenced)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Indented.Companion.light(
    editorTextStyle: TextStyle = defaultEditorTextStyle.copy(color = blockContentColorLight),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = blockBackgroundColorLight,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
): Indented =
    Indented(editorTextStyle, padding, shape, background, borderWidth, borderColor, fillWidth, scrollsHorizontally)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Indented.Companion.dark(
    editorTextStyle: TextStyle = defaultEditorTextStyle.copy(color = blockContentColorDark),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = blockBackgroundColorDark,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
): Indented =
    Indented(editorTextStyle, padding, shape, background, borderWidth, borderColor, fillWidth, scrollsHorizontally)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Fenced.Companion.light(
    editorTextStyle: TextStyle = defaultEditorTextStyle.copy(color = blockContentColorLight),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = blockBackgroundColorLight,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
    infoTextStyle: TextStyle = TextStyle(color = Color.Gray, fontSize = 12.sp),
    infoPadding: PaddingValues = PaddingValues(bottom = 16.dp),
    infoPosition: InfoPosition = InfoPosition.Hide,
): Fenced =
    Fenced(
        editorTextStyle,
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

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Fenced.Companion.dark(
    editorTextStyle: TextStyle = defaultEditorTextStyle.copy(color = blockContentColorDark),
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = blockBackgroundColorDark,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
    infoTextStyle: TextStyle = TextStyle(color = Color.Gray, fontSize = 12.sp),
    infoPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
    infoPosition: InfoPosition = InfoPosition.Hide,
): Fenced =
    Fenced(
        editorTextStyle,
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

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Image.Companion.default(
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    padding: PaddingValues = PaddingValues(),
    shape: Shape = RectangleShape,
    background: Color = Color.Unspecified,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
): Image = Image(alignment, contentScale, padding, shape, background, borderWidth, borderColor)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun ThematicBreak.Companion.light(
    padding: PaddingValues = PaddingValues(),
    lineWidth: Dp = 2.dp,
    lineColor: Color = Color.LightGray,
): ThematicBreak = ThematicBreak(padding, lineWidth, lineColor)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun ThematicBreak.Companion.dark(
    padding: PaddingValues = PaddingValues(),
    lineWidth: Dp = 2.dp,
    lineColor: Color = Color.DarkGray,
): ThematicBreak = ThematicBreak(padding, lineWidth, lineColor)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun HtmlBlock.Companion.light(
    textStyle: TextStyle = defaultEditorTextStyle.copy(color = Color.DarkGray),
    padding: PaddingValues = PaddingValues(8.dp),
    shape: Shape = RoundedCornerShape(4.dp),
    background: Color = Color.LightGray,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.Gray,
    fillWidth: Boolean = true,
): HtmlBlock = HtmlBlock(textStyle, padding, shape, background, borderWidth, borderColor, fillWidth)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun HtmlBlock.Companion.dark(
    textStyle: TextStyle = defaultEditorTextStyle.copy(color = Color.Gray),
    padding: PaddingValues = PaddingValues(8.dp),
    shape: Shape = RoundedCornerShape(4.dp),
    background: Color = Color.DarkGray,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.Gray,
    fillWidth: Boolean = true,
): HtmlBlock = HtmlBlock(textStyle, padding, shape, background, borderWidth, borderColor, fillWidth)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun InlinesStyling.Companion.light(
    textStyle: TextStyle = defaultTextStyle,
    editorTextStyle: TextStyle = defaultEditorTextStyle,
    inlineCode: SpanStyle =
        editorTextStyle
            .merge(
                fontSize = textStyle.fontSize * .85,
                background = inlineCodeBackgroundColorLight,
                color = textStyle.color,
            )
            .toSpanStyle(),
    link: SpanStyle = SpanStyle(color = IntUiLightTheme.colors.blue(2)),
    linkDisabled: SpanStyle = SpanStyle(color = IntUiLightTheme.colors.gray(8)),
    linkHovered: SpanStyle =
        SpanStyle(color = IntUiLightTheme.colors.blue(2), textDecoration = TextDecoration.Underline),
    linkFocused: SpanStyle = SpanStyle(background = Color(0x12000000), textDecoration = TextDecoration.Underline),
    linkPressed: SpanStyle = SpanStyle(background = Color(0x1D000000), textDecoration = TextDecoration.Underline),
    linkVisited: SpanStyle = link,
    emphasis: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    strongEmphasis: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    inlineHtml: SpanStyle =
        editorTextStyle
            .merge(
                fontSize = textStyle.fontSize * .85,
                color = IntUiLightTheme.colors.gray(8),
                background = Color.Unspecified,
            )
            .toSpanStyle(),
): InlinesStyling =
    InlinesStyling(
        textStyle = textStyle,
        inlineCode = inlineCode,
        link = link,
        linkDisabled = linkDisabled,
        linkFocused = linkFocused,
        linkHovered = linkHovered,
        linkPressed = linkPressed,
        linkVisited = linkVisited,
        emphasis = emphasis,
        strongEmphasis = strongEmphasis,
        inlineHtml = inlineHtml,
    )

@Deprecated(
    "Use the new variant, without renderInlineHtml and with editorTextStyle, instead",
    level = DeprecationLevel.HIDDEN,
)
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun InlinesStyling.Companion.light(
    textStyle: TextStyle = defaultTextStyle,
    inlineCode: SpanStyle =
        defaultEditorTextStyle
            .merge(fontSize = textStyle.fontSize * .85, background = inlineCodeBackgroundColorLight)
            .toSpanStyle(),
    link: SpanStyle = textStyle.copy(color = IntUiLightTheme.colors.blue(2)).toSpanStyle(),
    linkDisabled: SpanStyle = link.copy(color = IntUiLightTheme.colors.gray(8)),
    linkHovered: SpanStyle = link.copy(textDecoration = TextDecoration.Underline),
    linkFocused: SpanStyle = link.copy(background = Color(0x12000000), textDecoration = TextDecoration.Underline),
    linkPressed: SpanStyle = link.copy(background = Color(0x1D000000), textDecoration = TextDecoration.Underline),
    linkVisited: SpanStyle = link,
    emphasis: SpanStyle = textStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
    strongEmphasis: SpanStyle = textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
    inlineHtml: SpanStyle = textStyle.toSpanStyle(),
    // Detekt suppression — this is for API stability
    @Suppress("UnusedParameter") renderInlineHtml: Boolean = true,
): InlinesStyling =
    InlinesStyling(
        textStyle = textStyle,
        inlineCode = inlineCode,
        link = link,
        linkDisabled = linkDisabled,
        linkFocused = linkFocused,
        linkHovered = linkHovered,
        linkPressed = linkPressed,
        linkVisited = linkVisited,
        emphasis = emphasis,
        strongEmphasis = strongEmphasis,
        inlineHtml = inlineHtml,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun InlinesStyling.Companion.dark(
    textStyle: TextStyle = defaultTextStyle,
    editorTextStyle: TextStyle = defaultEditorTextStyle,
    inlineCode: SpanStyle =
        editorTextStyle
            .merge(
                fontSize = textStyle.fontSize * .85,
                background = inlineCodeBackgroundColorLight,
                color = textStyle.color,
            )
            .toSpanStyle(),
    link: SpanStyle = SpanStyle(color = IntUiDarkTheme.colors.blue(9)),
    linkDisabled: SpanStyle = SpanStyle(color = IntUiDarkTheme.colors.gray(8)),
    linkHovered: SpanStyle =
        SpanStyle(color = IntUiDarkTheme.colors.blue(9), textDecoration = TextDecoration.Underline),
    linkFocused: SpanStyle = SpanStyle(background = Color(0x16FFFFFF), textDecoration = TextDecoration.Underline),
    linkPressed: SpanStyle = SpanStyle(background = Color(0x26FFFFFF), textDecoration = TextDecoration.Underline),
    linkVisited: SpanStyle = link,
    emphasis: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    strongEmphasis: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    inlineHtml: SpanStyle =
        editorTextStyle
            .merge(
                fontSize = textStyle.fontSize * .85,
                color = IntUiDarkTheme.colors.gray(8),
                background = Color.Unspecified,
            )
            .toSpanStyle(),
): InlinesStyling =
    InlinesStyling(
        textStyle = textStyle,
        inlineCode = inlineCode,
        link = link,
        linkDisabled = linkDisabled,
        linkFocused = linkFocused,
        linkHovered = linkHovered,
        linkPressed = linkPressed,
        linkVisited = linkVisited,
        emphasis = emphasis,
        strongEmphasis = strongEmphasis,
        inlineHtml = inlineHtml,
    )

@Deprecated(
    "Use the new variant, without renderInlineHtml and with editorTextStyle, instead",
    level = DeprecationLevel.HIDDEN,
)
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun InlinesStyling.Companion.dark(
    textStyle: TextStyle = defaultTextStyle,
    inlineCode: SpanStyle =
        defaultEditorTextStyle
            .copy(fontSize = textStyle.fontSize * .85, background = inlineCodeBackgroundColorDark)
            .toSpanStyle(),
    link: SpanStyle = textStyle.copy(color = IntUiDarkTheme.colors.blue(9)).toSpanStyle(),
    linkDisabled: SpanStyle = link.copy(color = IntUiDarkTheme.colors.gray(8)),
    linkHovered: SpanStyle = link.copy(textDecoration = TextDecoration.Underline),
    linkFocused: SpanStyle = link.copy(background = Color(0x16FFFFFF), textDecoration = TextDecoration.Underline),
    linkPressed: SpanStyle = link.copy(background = Color(0x26FFFFFF), textDecoration = TextDecoration.Underline),
    linkVisited: SpanStyle = link,
    emphasis: SpanStyle = textStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
    strongEmphasis: SpanStyle = textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
    inlineHtml: SpanStyle = textStyle.toSpanStyle(),
    // Detekt suppression — this is for API stability
    @Suppress("UnusedParameter") renderInlineHtml: Boolean = true,
): InlinesStyling =
    InlinesStyling(
        textStyle = textStyle,
        inlineCode = inlineCode,
        link = link,
        linkDisabled = linkDisabled,
        linkFocused = linkFocused,
        linkHovered = linkHovered,
        linkPressed = linkPressed,
        linkVisited = linkVisited,
        emphasis = emphasis,
        strongEmphasis = strongEmphasis,
        inlineHtml = inlineHtml,
    )

private val blockBackgroundColorLight = Color(0xFFF6F8FA)
private val blockBackgroundColorDark = Color(0xFF161B22)

private val blockContentColorLight = Color(0xFF080808)
private val blockContentColorDark = Color(0xFFBCBEC4)

private val defaultTextSize = 13.sp

private val defaultTextStyle
    get() = JewelTheme.createDefaultTextStyle(fontSize = defaultTextSize)

private val defaultEditorTextStyle
    get() = JewelTheme.createEditorTextStyle(fontSize = defaultTextSize)

private val inlineCodeBackgroundColorLight = Color(red = 212, green = 222, blue = 231, alpha = 255 / 4)
private val inlineCodeBackgroundColorDark = Color(red = 212, green = 222, blue = 231, alpha = 25)

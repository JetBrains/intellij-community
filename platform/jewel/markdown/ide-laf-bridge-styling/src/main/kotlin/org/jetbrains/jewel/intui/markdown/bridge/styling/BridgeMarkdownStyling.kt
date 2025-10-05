package org.jetbrains.jewel.intui.markdown.bridge.styling

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
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveEditorColorScheme
import org.jetbrains.jewel.bridge.theme.retrieveDefaultTextStyle
import org.jetbrains.jewel.bridge.theme.retrieveEditorTextStyle
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
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
public fun MarkdownStyling.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    editorTextStyle: TextStyle = defaultEditorTextStyle,
    inlinesStyling: InlinesStyling = InlinesStyling.create(baseTextStyle, editorTextStyle),
    blockVerticalSpacing: Dp = 16.dp,
    paragraph: Paragraph = Paragraph.create(inlinesStyling),
    heading: Heading = Heading.create(baseTextStyle),
    blockQuote: BlockQuote = BlockQuote.create(textColor = baseTextStyle.color),
    code: Code = Code.create(editorTextStyle),
    list: List = List.create(baseTextStyle),
    image: Image = Image.default(),
    thematicBreak: ThematicBreak = ThematicBreak.create(),
    htmlBlock: HtmlBlock = HtmlBlock.create(editorTextStyle),
): MarkdownStyling =
    MarkdownStyling(blockVerticalSpacing, paragraph, heading, blockQuote, code, list, image, thematicBreak, htmlBlock)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Paragraph.Companion.create(inlinesStyling: InlinesStyling = InlinesStyling.create()): Paragraph =
    Paragraph(inlinesStyling)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    h1: Heading.H1 =
        Heading.H1.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 2,
                lineHeight = baseTextStyle.fontSize * 2 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h2: Heading.H2 =
        Heading.H2.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.5,
                lineHeight = baseTextStyle.fontSize * 1.5 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h3: Heading.H3 =
        Heading.H3.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.25,
                lineHeight = baseTextStyle.fontSize * 1.25 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h4: Heading.H4 =
        Heading.H4.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize,
                lineHeight = baseTextStyle.fontSize * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h5: Heading.H5 =
        Heading.H5.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * .875,
                lineHeight = baseTextStyle.fontSize * .875 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    h6: Heading.H6 =
        Heading.H6.create(
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
public fun Heading.H1.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    inlinesStyling: InlinesStyling =
        InlinesStyling.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 2,
                lineHeight = baseTextStyle.fontSize * 2 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = dividerColor,
    underlineGap: Dp = 10.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H1 = Heading.H1(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H2.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    inlinesStyling: InlinesStyling =
        InlinesStyling.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.5,
                lineHeight = baseTextStyle.fontSize * 1.5 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    underlineWidth: Dp = 1.dp,
    underlineColor: Color = dividerColor,
    underlineGap: Dp = 6.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H2 = Heading.H2(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H3.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    inlinesStyling: InlinesStyling =
        InlinesStyling.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * 1.25,
                lineHeight = baseTextStyle.fontSize * 1.25 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H3 = Heading.H3(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H4.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    inlinesStyling: InlinesStyling =
        InlinesStyling.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize,
                lineHeight = baseTextStyle.fontSize * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H4 = Heading.H4(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H5.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    inlinesStyling: InlinesStyling =
        InlinesStyling.create(
            baseTextStyle.copy(
                fontSize = baseTextStyle.fontSize * .875,
                lineHeight = baseTextStyle.fontSize * .875 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H5 = Heading.H5(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

// This doesn't match Int UI specs as there is no spec for HTML rendering
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Heading.H6.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    inlinesStyling: InlinesStyling =
        InlinesStyling.create(
            baseTextStyle.copy(
                color = Color(0xFF656d76),
                fontSize = baseTextStyle.fontSize * .85,
                lineHeight = baseTextStyle.fontSize * .85 * 1.25,
                fontWeight = FontWeight.SemiBold,
            )
        ),
    underlineWidth: Dp = 0.dp,
    underlineColor: Color = Color.Unspecified,
    underlineGap: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(top = 24.dp, bottom = 16.dp),
): Heading.H6 = Heading.H6(inlinesStyling, underlineWidth, underlineColor, underlineGap, padding)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun BlockQuote.Companion.create(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 4.dp,
    lineColor: Color = Color(0xFFD0D7DE),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    textColor: Color = Color(0xFF656d76),
): BlockQuote = BlockQuote(padding, lineWidth, lineColor, pathEffect, strokeCap, textColor)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun List.Companion.create(
    baseTextStyle: TextStyle = defaultTextStyle,
    ordered: Ordered = Ordered.create(numberStyle = baseTextStyle),
    unordered: Unordered = Unordered.create(bulletStyle = baseTextStyle.copy(fontWeight = FontWeight.Black)),
): List = List(ordered, unordered)

@ApiStatus.Experimental
@ExperimentalJewelApi
@Deprecated("Please, use the overload with [Ordered.NumberFormatStyles].")
public fun Ordered.Companion.create(
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
public fun Ordered.Companion.create(
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
@Deprecated("Please, use the overload with [Unordered.BulletCharStyles].")
public fun Unordered.Companion.create(
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
public fun Unordered.Companion.create(
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
public fun Code.Companion.create(
    editorTextStyle: TextStyle = defaultEditorTextStyle,
    indented: Indented = Indented.create(editorTextStyle),
    fenced: Fenced = Fenced.create(editorTextStyle),
): Code = Code(indented, fenced)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Indented.Companion.create(
    textStyle: TextStyle = defaultEditorTextStyle,
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = blockBackgroundColor,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
): Indented = Indented(textStyle, padding, shape, background, borderWidth, borderColor, fillWidth, scrollsHorizontally)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Fenced.Companion.create(
    textStyle: TextStyle = defaultEditorTextStyle,
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RectangleShape,
    background: Color = blockBackgroundColor,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    fillWidth: Boolean = true,
    scrollsHorizontally: Boolean = true,
    infoTextStyle: TextStyle = TextStyle(color = infoContentColor, fontSize = 12.sp),
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
public fun ThematicBreak.Companion.create(
    padding: PaddingValues = PaddingValues(),
    lineWidth: Dp = 2.dp,
    lineColor: Color = dividerColor,
): ThematicBreak = ThematicBreak(padding, lineWidth, lineColor)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun HtmlBlock.Companion.create(
    textStyle: TextStyle = defaultEditorTextStyle,
    padding: PaddingValues = PaddingValues(8.dp),
    shape: Shape = RoundedCornerShape(4.dp),
    background: Color = blockBackgroundColor,
    borderWidth: Dp = 1.dp,
    borderColor: Color = dividerColor,
    fillWidth: Boolean = true,
): HtmlBlock = HtmlBlock(textStyle, padding, shape, background, borderWidth, borderColor, fillWidth)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun InlinesStyling.Companion.create(
    textStyle: TextStyle = defaultTextStyle,
    editorTextStyle: TextStyle = defaultEditorTextStyle,
    inlineCode: SpanStyle =
        editorTextStyle
            .merge(fontSize = textStyle.fontSize * .85, background = inlineCodeBackgroundColor, color = textStyle.color)
            .toSpanStyle(),
    link: SpanStyle = SpanStyle(color = JBUI.CurrentTheme.Link.Foreground.ENABLED.toComposeColor()),
    linkDisabled: SpanStyle = SpanStyle(color = JBUI.CurrentTheme.Link.Foreground.DISABLED.toComposeColor()),
    linkHovered: SpanStyle =
        SpanStyle(
            color = JBUI.CurrentTheme.Link.Foreground.HOVERED.toComposeColor(),
            textDecoration = TextDecoration.Underline,
        ),
    linkFocused: SpanStyle =
        SpanStyle(
            color = JBUI.CurrentTheme.Link.Foreground.ENABLED.toComposeColor(),
            background = JBUI.CurrentTheme.ActionButton.hoverBackground().toComposeColor(),
            textDecoration = TextDecoration.Underline,
        ),
    linkPressed: SpanStyle =
        SpanStyle(
            color = JBUI.CurrentTheme.Link.Foreground.PRESSED.toComposeColor(),
            background = JBUI.CurrentTheme.ActionButton.pressedBackground().toComposeColor(),
            textDecoration = TextDecoration.Underline,
        ),
    linkVisited: SpanStyle = SpanStyle(color = JBUI.CurrentTheme.Link.Foreground.VISITED.toComposeColor()),
    emphasis: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    strongEmphasis: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    inlineHtml: SpanStyle =
        editorTextStyle
            .merge(
                fontSize = textStyle.fontSize * .85,
                color = JBUI.CurrentTheme.Label.disabledForeground().toComposeColor(),
                background = Color.Unspecified,
            )
            .toSpanStyle(),
): InlinesStyling =
    InlinesStyling(
        textStyle = textStyle,
        inlineCode = inlineCode,
        link = link,
        linkDisabled = linkDisabled,
        linkHovered = linkHovered,
        linkFocused = linkFocused,
        linkPressed = linkPressed,
        linkVisited = linkVisited,
        emphasis = emphasis,
        strongEmphasis = strongEmphasis,
        inlineHtml = inlineHtml,
    )

@Deprecated("Use the variant without renderInlineHtml instead", level = DeprecationLevel.HIDDEN)
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun InlinesStyling.Companion.create(
    textStyle: TextStyle = defaultTextStyle,
    inlineCode: SpanStyle =
        defaultEditorTextStyle
            .copy(fontSize = textStyle.fontSize * .85, background = inlineCodeBackgroundColor)
            .toSpanStyle(),
    link: SpanStyle = textStyle.copy(color = JBUI.CurrentTheme.Link.Foreground.ENABLED.toComposeColor()).toSpanStyle(),
    linkDisabled: SpanStyle = link.copy(color = JBUI.CurrentTheme.Link.Foreground.DISABLED.toComposeColor()),
    linkHovered: SpanStyle =
        link.copy(
            color = JBUI.CurrentTheme.Link.Foreground.HOVERED.toComposeColor(),
            textDecoration = TextDecoration.Underline,
        ),
    linkFocused: SpanStyle =
        link.copy(
            color = JBUI.CurrentTheme.Link.Foreground.ENABLED.toComposeColor(),
            background = JBUI.CurrentTheme.ActionButton.hoverBackground().toComposeColor(),
            textDecoration = TextDecoration.Underline,
        ),
    linkPressed: SpanStyle =
        link.copy(
            color = JBUI.CurrentTheme.Link.Foreground.PRESSED.toComposeColor(),
            background = JBUI.CurrentTheme.ActionButton.pressedBackground().toComposeColor(),
            textDecoration = TextDecoration.Underline,
        ),
    linkVisited: SpanStyle = link.copy(color = JBUI.CurrentTheme.Link.Foreground.VISITED.toComposeColor()),
    emphasis: SpanStyle = textStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
    strongEmphasis: SpanStyle = textStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
    inlineHtml: SpanStyle = textStyle.toSpanStyle(),
    @Suppress("UnusedParameter") renderInlineHtml: Boolean = true,
): InlinesStyling =
    InlinesStyling(
        textStyle = textStyle,
        inlineCode = inlineCode,
        link = link,
        linkDisabled = linkDisabled,
        linkHovered = linkHovered,
        linkFocused = linkFocused,
        linkPressed = linkPressed,
        linkVisited = linkVisited,
        emphasis = emphasis,
        strongEmphasis = strongEmphasis,
        inlineHtml = inlineHtml,
    )

private val defaultTextStyle
    get() = retrieveDefaultTextStyle().copy(color = Color.Unspecified)

private val defaultEditorTextStyle
    get() = retrieveEditorTextStyle().copy(color = blockContentColor)

private val dividerColor
    get() = retrieveColorOrUnspecified("Group.separatorColor")

private val blockBackgroundColor
    get() = retrieveEditorColorScheme().defaultBackground.toComposeColor()

private val blockContentColor
    get() = retrieveEditorColorScheme().defaultForeground.toComposeColor()

private val infoContentColor
    get() = retrieveColorOrUnspecified("Component.infoForeground")

// Copied from org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles#createStylesheet
private val inlineCodeBackgroundColor
    get() =
        if (JBColor.isBright()) {
            Color(red = 212, green = 222, blue = 231, alpha = 255 / 4)
        } else {
            Color(red = 212, green = 222, blue = 231, alpha = 25)
        }

package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withCompositionLocal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.BlockQuote
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.FencedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.IndentedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading
import org.jetbrains.jewel.markdown.MarkdownBlock.HtmlBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.OrderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.UnorderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListItem
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.MarkdownBlock.ThematicBreak
import org.jetbrains.jewel.markdown.WithInlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered.NumberFormatStyles
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Unordered.BulletCharStyles
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontallyScrollableContainer
import org.jetbrains.jewel.ui.component.Text

private const val DISABLED_CODE_ALPHA = .5f
private val MIME_TYPE_REGEX = "^\\w+/.+$".toRegex()

/**
 * Default implementation of [MarkdownBlockRenderer] that uses the provided styling, extensions, and inline renderer to
 * render [MarkdownBlock]s into Compose UI elements.
 *
 * @see MarkdownBlockRenderer
 */
@Suppress("OVERRIDE_DEPRECATION", "LargeClass")
@ApiStatus.Experimental
@ExperimentalJewelApi
public open class DefaultMarkdownBlockRenderer(
    override val rootStyling: MarkdownStyling,
    override val rendererExtensions: List<MarkdownRendererExtension> = emptyList(),
    override val inlineRenderer: InlineMarkdownRenderer = InlineMarkdownRenderer.create(rendererExtensions),
) : MarkdownBlockRenderer {
    @Composable
    override fun render(
        blocks: List<MarkdownBlock>,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderBlocks(blocks, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderBlocks(
        blocks: List<MarkdownBlock>,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(rootStyling.blockVerticalSpacing)) {
            for (block in blocks) {
                RenderBlock(block, enabled, onUrlClick, Modifier)
            }
        }
    }

    @Composable
    override fun render(
        block: MarkdownBlock,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderBlock(block, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderBlock(block: MarkdownBlock, enabled: Boolean, onUrlClick: (String) -> Unit, modifier: Modifier) {
        when (block) {
            is BlockQuote -> RenderBlockQuote(block, rootStyling.blockQuote, enabled, onUrlClick, modifier)
            is FencedCodeBlock -> RenderFencedCodeBlock(block, rootStyling.code.fenced, enabled, modifier)
            is IndentedCodeBlock -> RenderIndentedCodeBlock(block, rootStyling.code.indented, enabled, modifier)
            is Heading -> RenderHeading(block, rootStyling.heading, enabled, onUrlClick, modifier)
            is HtmlBlock -> RenderHtmlBlock(block, rootStyling.htmlBlock, enabled, modifier)
            is OrderedList -> RenderOrderedList(block, rootStyling.list.ordered, enabled, onUrlClick, modifier)
            is UnorderedList -> RenderUnorderedList(block, rootStyling.list.unordered, enabled, onUrlClick, modifier)
            is ListItem -> RenderListItem(block, enabled, onUrlClick, modifier)
            is Paragraph -> RenderParagraph(block, rootStyling.paragraph, enabled, onUrlClick, modifier)
            ThematicBreak -> RenderThematicBreak(rootStyling.thematicBreak, enabled, modifier)
            is MarkdownBlock.HtmlBlockWithAttributes ->
                RenderHtmlBlockWithAttributes(block, enabled, onUrlClick, modifier)

            is CustomBlock -> {
                rendererExtensions
                    .find { it.blockRenderer?.canRender(block) == true }
                    ?.blockRenderer
                    ?.RenderCustomBlock(
                        block = block,
                        blockRenderer = this,
                        inlineRenderer = inlineRenderer,
                        enabled = enabled,
                        modifier = modifier,
                        onUrlClick = onUrlClick,
                    )
            }
        }
    }

    @Composable
    override fun render(
        block: Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderParagraph(block, styling, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderParagraph(
        block: Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        RenderParagraph(
            block = block,
            styling = styling,
            enabled = enabled,
            onUrlClick = onUrlClick,
            onTextLayout = {},
            modifier = modifier,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
        )
    }

    @Composable
    override fun RenderParagraph(
        block: Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextLayout: (TextLayoutResult) -> Unit,
        modifier: Modifier,
        overflow: TextOverflow,
        softWrap: Boolean,
        maxLines: Int,
    ) {
        RenderBlockWithInlines(
            block,
            styling.inlinesStyling,
            enabled,
            onUrlClick,
            onTextLayout,
            modifier,
            overflow,
            softWrap,
            maxLines,
        )
    }

    @Composable
    private fun RenderBlockWithInlines(
        block: WithInlineMarkdown,
        inlinesStyling: InlinesStyling,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextLayout: (TextLayoutResult) -> Unit,
        modifier: Modifier = Modifier,
        overflow: TextOverflow = TextOverflow.Clip,
        softWrap: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
    ) {
        val originalImages = renderedImages(block)

        val renderedContent = rememberRenderedContent(block, inlinesStyling, enabled, onUrlClick)
        val textColor = inlinesStyling.textStyle.color.takeOrElse { LocalContentColor.current }
        val mergedStyle = inlinesStyling.textStyle.merge(TextStyle(color = textColor))
        val density = LocalDensity.current

        TextWithAdaptiveInlineContent(
            text = renderedContent,
            inlineContent = originalImages,
            density = density,
            modifier = modifier,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            style = mergedStyle,
            textAlign = LocalTextAlignment.current,
        )
    }

    @Composable
    override fun render(
        block: Heading,
        styling: MarkdownStyling.Heading,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderHeading(block, styling, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderHeading(
        block: Heading,
        styling: MarkdownStyling.Heading,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        when (block.level) {
            1 -> RenderHeading(block, styling.h1, enabled, onUrlClick, modifier)
            2 -> RenderHeading(block, styling.h2, enabled, onUrlClick, modifier)
            3 -> RenderHeading(block, styling.h3, enabled, onUrlClick, modifier)
            4 -> RenderHeading(block, styling.h4, enabled, onUrlClick, modifier)
            5 -> RenderHeading(block, styling.h5, enabled, onUrlClick, modifier)
            6 -> RenderHeading(block, styling.h6, enabled, onUrlClick, modifier)
            else -> JewelLogger.getInstance(javaClass).error("Heading level ${block.level} not supported:\n$block")
        }
    }

    @Composable
    override fun render(
        block: Heading,
        styling: MarkdownStyling.Heading.HN,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderHeading(block, styling, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderHeading(
        block: Heading,
        styling: MarkdownStyling.Heading.HN,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        Column(modifier = modifier.padding(styling.padding)) {
            RenderBlockWithInlines(
                block,
                styling.inlinesStyling,
                enabled,
                onUrlClick,
                {},
                Modifier.focusProperties { this.canFocus = false },
            )

            if (styling.underlineWidth > 0.dp && styling.underlineColor.isSpecified) {
                Spacer(Modifier.height(styling.underlineGap))
                Divider(
                    orientation = Orientation.Horizontal,
                    modifier = Modifier.fillMaxWidth(),
                    color = styling.underlineColor,
                    thickness = styling.underlineWidth,
                )
            }
        }
    }

    @Composable
    override fun render(
        block: BlockQuote,
        styling: MarkdownStyling.BlockQuote,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderBlockQuote(block, styling, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderBlockQuote(
        block: BlockQuote,
        styling: MarkdownStyling.BlockQuote,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        Column(
            modifier
                .drawBehind {
                    val isLtr = layoutDirection == Ltr
                    val lineWidthPx = styling.lineWidth.toPx()
                    val x = if (isLtr) lineWidthPx / 2 else size.width - lineWidthPx / 2

                    drawLine(
                        styling.lineColor,
                        Offset(x, 0f),
                        Offset(x, size.height),
                        lineWidthPx,
                        styling.strokeCap,
                        styling.pathEffect,
                    )
                }
                .padding(styling.padding),
            verticalArrangement = Arrangement.spacedBy(rootStyling.blockVerticalSpacing),
        ) {
            CompositionLocalProvider(LocalContentColor provides styling.textColor) {
                RenderBlocks(block.children, enabled, onUrlClick, Modifier)
            }
        }
    }

    @Composable
    override fun render(
        block: ListBlock,
        styling: MarkdownStyling.List,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderList(block, styling, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderList(
        block: ListBlock,
        styling: MarkdownStyling.List,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        when (block) {
            is OrderedList -> RenderOrderedList(block, styling.ordered, enabled, onUrlClick, modifier)
            is UnorderedList -> RenderUnorderedList(block, styling.unordered, enabled, onUrlClick, modifier)
        }
    }

    @Composable
    override fun render(
        block: OrderedList,
        styling: MarkdownStyling.List.Ordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderOrderedList(block, styling, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderOrderedList(
        block: OrderedList,
        styling: MarkdownStyling.List.Ordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        val itemSpacing =
            if (block.isTight) {
                styling.itemVerticalSpacingTight
            } else {
                styling.itemVerticalSpacing
            }

        Column(modifier = modifier.padding(styling.padding), verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            for ((index, item) in block.children.withIndex()) {
                Row {
                    val number = block.startFrom + index
                    val numberFormat = styling.numberFormatStyles.formatFor(item.level)
                    val formattedNumber = numberFormat.formatNumber(number)

                    Text(
                        text = "$formattedNumber${block.delimiter}",
                        style = styling.numberStyle,
                        color = styling.numberStyle.color.takeOrElse { LocalContentColor.current },
                        modifier =
                            Modifier.focusProperties { canFocus = false }
                                .widthIn(min = styling.numberMinWidth)
                                .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                        textAlign = styling.numberTextAlign,
                    )

                    Spacer(Modifier.width(styling.numberContentGap))

                    RenderListItem(item, enabled, onUrlClick, Modifier)
                }
            }
        }
    }

    private fun NumberFormatStyles.formatFor(level: Int) =
        when (level) {
            0 -> firstLevel
            1 -> secondLevel
            else -> thirdLevel
        }

    @Composable
    override fun render(
        block: UnorderedList,
        styling: MarkdownStyling.List.Unordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderUnorderedList(block, styling, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderUnorderedList(
        block: UnorderedList,
        styling: MarkdownStyling.List.Unordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        val itemSpacing =
            if (block.isTight) {
                styling.itemVerticalSpacingTight
            } else {
                styling.itemVerticalSpacing
            }

        Column(modifier = modifier.padding(styling.padding), verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            for (item in block.children) {
                Row {
                    val charMarkerStyle = styling.bulletCharStyles?.formatFor(item.level) ?: styling.bullet

                    Text(
                        text = charMarkerStyle.toString(),
                        style = styling.bulletStyle,
                        color = styling.bulletStyle.color.takeOrElse { LocalContentColor.current },
                        modifier =
                            Modifier.focusProperties { canFocus = false }
                                .widthIn(min = styling.markerMinWidth)
                                .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.width(styling.bulletContentGap))

                    RenderListItem(item, enabled, onUrlClick, Modifier)
                }
            }
        }
    }

    private fun BulletCharStyles.formatFor(level: Int) =
        when (level) {
            0 -> firstLevel
            1 -> secondLevel
            else -> thirdLevel
        }

    @Composable
    override fun render(
        block: ListItem,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        RenderListItem(block, enabled, onUrlClick, modifier)
    }

    @Composable
    override fun RenderListItem(block: ListItem, enabled: Boolean, onUrlClick: (String) -> Unit, modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (childBlock in block.children) {
                RenderBlock(childBlock, enabled, onUrlClick, Modifier)
            }
        }
    }

    @Composable
    override fun render(block: CodeBlock, styling: MarkdownStyling.Code, enabled: Boolean, modifier: Modifier) {
        RenderCodeBlock(block, styling, enabled, modifier)
    }

    @Composable
    override fun RenderCodeBlock(
        block: CodeBlock,
        styling: MarkdownStyling.Code,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        when (block) {
            is FencedCodeBlock -> render(block, styling.fenced, enabled, modifier)
            is IndentedCodeBlock -> render(block, styling.indented, enabled, modifier)
        }
    }

    @Composable
    override fun render(
        block: IndentedCodeBlock,
        styling: MarkdownStyling.Code.Indented,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        RenderIndentedCodeBlock(block, styling, enabled, modifier)
    }

    @Composable
    override fun RenderIndentedCodeBlock(
        block: IndentedCodeBlock,
        styling: MarkdownStyling.Code.Indented,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        MaybeScrollingContainer(
            isScrollable = styling.scrollsHorizontally,
            modifier
                .background(styling.background, styling.shape)
                .border(styling.borderWidth, styling.borderColor, styling.shape)
                .thenIf(styling.fillWidth) { fillMaxWidth() }
                .thenIf(!enabled) { alpha(DISABLED_CODE_ALPHA) },
        ) {
            Text(
                text = block.content,
                style = styling.editorTextStyle,
                color = styling.editorTextStyle.color.takeOrElse { LocalContentColor.current },
                modifier =
                    Modifier.focusProperties { canFocus = false }
                        .padding(styling.padding)
                        .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                textAlign = LocalTextAlignment.current,
            )
        }
    }

    @Composable
    override fun render(
        block: FencedCodeBlock,
        styling: MarkdownStyling.Code.Fenced,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        RenderFencedCodeBlock(block, styling, enabled, modifier)
    }

    @Composable
    override fun RenderFencedCodeBlock(
        block: FencedCodeBlock,
        styling: MarkdownStyling.Code.Fenced,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        val language = block.language.orEmpty()
        MaybeScrollingContainer(
            isScrollable = styling.scrollsHorizontally,
            modifier
                .background(styling.background, styling.shape)
                .border(styling.borderWidth, styling.borderColor, styling.shape)
                .thenIf(styling.fillWidth) { fillMaxWidth() }
                .thenIf(!enabled) { alpha(DISABLED_CODE_ALPHA) },
        ) {
            Column(Modifier.padding(styling.padding)) {
                if (styling.infoPosition.verticalAlignment == Alignment.Top) {
                    FencedBlockInfo(
                        language,
                        styling.infoPosition.horizontalAlignment
                            ?: error("No horizontal alignment for position ${styling.infoPosition.name}"),
                        styling.infoTextStyle,
                        Modifier.fillMaxWidth().padding(styling.infoPadding),
                    )
                }

                if (MIME_TYPE_REGEX.matches(language)) {
                    val mimeType = MimeType.Known.fromMimeTypeString(language)
                    // Enabled is always true as the container handles the disabled alpha
                    // Passing the value down would duplicate the alpha, which would produce a 0.25f opacity
                    RenderCodeWithMimeType(block, mimeType, styling, true)
                } else {
                    RenderCodeWithLanguage(block, styling, enabled)
                }

                if (styling.infoPosition.verticalAlignment == Alignment.Bottom) {
                    FencedBlockInfo(
                        language,
                        styling.infoPosition.horizontalAlignment
                            ?: error("No horizontal alignment for position ${styling.infoPosition.name}"),
                        styling.infoTextStyle,
                        Modifier.fillMaxWidth().padding(styling.infoPadding),
                    )
                }
            }
        }
    }

    @Deprecated(
        message =
            "This class function is not scalable as it relies on a pre-resolved MimeType object. " +
                "This prevents automatic support for languages not explicitly defined in the MimeType system" +
                "(e.g., from TextMate bundles)."
    )
    @Composable
    internal open fun RenderCodeWithMimeType(
        block: FencedCodeBlock,
        mimeType: MimeType,
        styling: MarkdownStyling.Code.Fenced,
        enabled: Boolean,
    ) {
        val content = block.content
        val highlighter = LocalCodeHighlighter.current
        val highlightedCode by highlighter.highlight(content, mimeType).collectAsState(AnnotatedString(content))
        Text(
            text = highlightedCode,
            style = styling.editorTextStyle,
            modifier =
                Modifier.onFirstVisible {
                        this@DefaultMarkdownBlockRenderer.myLogger()
                            .warn("Rendering code block with using deprecated MimeType class.")
                    }
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                    .thenIf(!enabled) { alpha(.5f) },
        )
    }

    @Composable
    internal open fun RenderCodeWithLanguage(
        block: FencedCodeBlock,
        styling: MarkdownStyling.Code.Fenced,
        enabled: Boolean,
    ) {
        val content = block.content
        val highlighter = LocalCodeHighlighter.current
        val highlightedCode by
            highlighter.highlight(content, block.language.orEmpty()).collectAsState(AnnotatedString(content))
        Text(
            text = highlightedCode,
            style = styling.editorTextStyle,
            modifier =
                Modifier.focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
            textAlign = LocalTextAlignment.current,
        )
    }

    @Composable
    private fun FencedBlockInfo(
        infoText: String,
        alignment: Alignment.Horizontal,
        textStyle: TextStyle,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier, horizontalAlignment = alignment) {
            DisableSelection {
                Text(
                    text = infoText,
                    style = textStyle,
                    color = textStyle.color.takeOrElse { LocalContentColor.current },
                    modifier = Modifier.focusProperties { canFocus = false },
                    textAlign = LocalTextAlignment.current,
                )
            }
        }
    }

    @Composable
    override fun renderThematicBreak(styling: MarkdownStyling.ThematicBreak, enabled: Boolean, modifier: Modifier) {
        RenderThematicBreak(styling, enabled, modifier)
    }

    @Composable
    override fun RenderThematicBreak(styling: MarkdownStyling.ThematicBreak, enabled: Boolean, modifier: Modifier) {
        Divider(
            orientation = Orientation.Horizontal,
            modifier = modifier.padding(styling.padding).fillMaxWidth(),
            color = styling.lineColor,
            thickness = styling.lineWidth,
        )
    }

    @Composable
    override fun render(block: HtmlBlock, styling: MarkdownStyling.HtmlBlock, enabled: Boolean, modifier: Modifier) {
        RenderHtmlBlock(block, styling, enabled, modifier)
    }

    @Composable
    override fun RenderHtmlBlock(
        block: HtmlBlock,
        styling: MarkdownStyling.HtmlBlock,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        // HTML blocks are intentionally not rendered
    }

    @Composable
    private fun rememberRenderedContent(
        block: WithInlineMarkdown,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClick: ((String) -> Unit)? = null,
    ) =
        remember(block.inlineContent, styling, enabled) {
            inlineRenderer.renderAsAnnotatedString(block.inlineContent, styling, enabled, onUrlClick)
        }

    @Composable
    private fun renderedImages(blockInlineContent: WithInlineMarkdown): Map<String, InlineTextContent> {
        val map = remember(blockInlineContent) { mutableStateMapOf<String, InlineTextContent>() }

        val imagesRenderer = rendererExtensions.firstNotNullOfOrNull { it.imageRendererExtension }

        for (image in getImages(blockInlineContent)) {
            val renderedImage = imagesRenderer?.renderImageContent(image)
            if (renderedImage == null) {
                map.remove(image.source)
            } else {
                map[image.source] = renderedImage
            }
        }

        return map
    }

    @Composable
    protected fun MaybeScrollingContainer(
        isScrollable: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        // We use movableContent so changing the flag doesn't reset the content
        val movableContent = remember { movableContentOf { content() } }
        if (isScrollable) {
            HorizontallyScrollableContainer(modifier) { movableContent() }
        } else {
            movableContent()
        }
    }

    @Composable
    private fun RenderHtmlBlockWithAttributes(
        block: MarkdownBlock.HtmlBlockWithAttributes,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val textAlignment: TextAlign =
            when (block.attributes["align"]) {
                "left" -> TextAlign.Start
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> {
                    RenderBlock(block.mdBlock, enabled, onUrlClick, modifier)
                    return
                }
            }
        val contentAlignment =
            when (textAlignment) {
                TextAlign.Start -> Alignment.TopStart
                TextAlign.Center -> Alignment.TopCenter
                TextAlign.End -> Alignment.TopEnd
                else -> Alignment.TopStart
            }
        withCompositionLocal(LocalTextAlignment provides textAlignment) {
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = contentAlignment) {
                RenderBlock(block.mdBlock, enabled, onUrlClick, Modifier.align(contentAlignment))
            }
        }
    }

    /** Scales the inline content placeholders by the given scale factor. */
    private fun scaleInlineContent(
        content: Map<String, InlineTextContent>,
        availableWidth: Int,
        density: Density,
    ): Map<String, InlineTextContent> {
        return content.mapValues { (_, inlineContent) ->
            val width = with(density) { inlineContent.placeholder.width.roundToPx() }
            if (width == 0 || availableWidth >= width) {
                inlineContent
            } else {
                val scale = availableWidth.toFloat() / width
                InlineTextContent(
                    placeholder =
                        Placeholder(
                            width = inlineContent.placeholder.width * scale,
                            height = inlineContent.placeholder.height * scale,
                            placeholderVerticalAlign = inlineContent.placeholder.placeholderVerticalAlign,
                        ),
                    children = inlineContent.children,
                )
            }
        }
    }

    /**
     * A Text composable that automatically scales inline content when there's insufficient horizontal space. Uses
     * TextMeasurer to pre-measure and determine the appropriate scale factor.
     */
    @Composable
    private fun TextWithAdaptiveInlineContent(
        text: AnnotatedString,
        inlineContent: Map<String, InlineTextContent>,
        density: Density,
        modifier: Modifier = Modifier,
        overflow: TextOverflow = TextOverflow.Clip,
        softWrap: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
        onTextLayout: (TextLayoutResult) -> Unit = {},
        style: TextStyle = TextStyle.Default,
        textAlign: TextAlign = LocalTextAlignment.current,
    ) {
        val placeholderWidths = inlineContent.values.map { it.placeholder.width.value }

        if (placeholderWidths.sum() <= 0.01f) {
            Text(
                text = text,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                onTextLayout = onTextLayout,
                style = style,
                textAlign = textAlign,
            )
            return
        }

        var maxAvailableWidth by remember { mutableStateOf(Int.MAX_VALUE) }

        val scaledContent =
            remember(placeholderWidths, maxAvailableWidth) {
                scaleInlineContent(inlineContent, maxAvailableWidth, density)
            }

        // Measure text with unscaled inline content to get intrinsic width
        // (useful for grid-based containers to preserve proportions, i.e., tables)
        val textMeasurer = rememberTextMeasurer()

        val intrinsicWidth =
            remember(text, placeholderWidths, style, textMeasurer) {
                val mergedStyle = style.merge(TextStyle(textAlign = textAlign))

                val placeholders =
                    text.getStringAnnotations(0, text.length).mapNotNull { annotation ->
                        inlineContent[annotation.item]?.let { content ->
                            AnnotatedString.Range(content.placeholder, annotation.start, annotation.end)
                        }
                    }

                // Measure with unconstrained width
                val measured =
                    textMeasurer.measure(
                        text = text,
                        style = mergedStyle,
                        softWrap = false,
                        maxLines = 1,
                        placeholders = placeholders,
                    )

                measured.size.width
            }

        val measurePolicy =
            remember(intrinsicWidth) {
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult {
                        // Note that this causes recomposition. It's not recommended to use this trick deliberately,
                        // but I couldn't find a better way to update the available width dynamically.
                        // The text layout phase happens before measurement, so, to lay the text out properly,
                        // placeholders are designed to have a static, fixed (hardcoded, if you will) size,
                        // defined during composition.
                        // This leaves us with (seemingly) no other option but to scale the placeholder manually,
                        // and, to know the scale factor, we must pass the measured width back to composition.
                        if (constraints.maxWidth != maxAvailableWidth && constraints.maxWidth != Constraints.Infinity) {
                            maxAvailableWidth = constraints.maxWidth
                        }

                        val placeable = measurables.firstOrNull()?.measure(constraints)

                        return layout(
                            width = placeable?.width ?: constraints.minWidth,
                            height = placeable?.height ?: constraints.minHeight,
                        ) {
                            placeable?.place(0, 0)
                        }
                    }

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = intrinsicWidth
                }
            }

        Layout(
            modifier = modifier,
            measurePolicy = measurePolicy,
            content = {
                Text(
                    text = text,
                    overflow = overflow,
                    softWrap = softWrap,
                    maxLines = maxLines,
                    onTextLayout = onTextLayout,
                    inlineContent = scaledContent,
                    style = style,
                    textAlign = textAlign,
                )
            },
        )
    }

    public override fun createCopy(
        rootStyling: MarkdownStyling?,
        rendererExtensions: List<MarkdownRendererExtension>?,
        inlineRenderer: InlineMarkdownRenderer?,
    ): MarkdownBlockRenderer =
        DefaultMarkdownBlockRenderer(
            rootStyling ?: this.rootStyling,
            rendererExtensions ?: this.rendererExtensions,
            inlineRenderer ?: this.inlineRenderer,
        )

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    override operator fun plus(extension: MarkdownRendererExtension): MarkdownBlockRenderer =
        DefaultMarkdownBlockRenderer(rootStyling, rendererExtensions = rendererExtensions + extension, inlineRenderer)

    public companion object {
        @Suppress("VariableNaming")
        private val LocalTextAlignment: ProvidableCompositionLocal<TextAlign> = staticCompositionLocalOf {
            TextAlign.Start
        }
    }
}

private fun getImages(input: WithInlineMarkdown): List<InlineMarkdown.Image> = buildList {
    fun collectImagesRecursively(items: List<InlineMarkdown>) {
        for (item in items) {
            when (item) {
                is InlineMarkdown.Image -> {
                    if (item.source.isNotBlank()) add(item)
                }

                is WithInlineMarkdown -> {
                    collectImagesRecursively(item.inlineContent)
                }

                else -> {
                    // Ignored
                }
            }
        }
    }
    collectImagesRecursively(input.inlineContent)
}

@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
private fun MimeType.Known.fromMimeTypeString(mimeType: String): MimeType =
    when (mimeType) {
        "text/x-java-source",
        "application/x-java",
        "text/x-java" -> JAVA

        "application/kotlin-source",
        "text/x-kotlin",
        "text/x-kotlin-source" -> KOTLIN

        "application/xml" -> XML
        "application/json",
        "application/vnd.api+json",
        "application/hal+json",
        "application/ld+json" -> JSON

        "image/svg+xml" -> XML
        "text/x-python",
        "application/x-python-script" -> PYTHON

        "text/dart",
        "text/x-dart",
        "application/dart",
        "application/x-dart" -> DART

        "application/javascript",
        "application/x-javascript",
        "text/ecmascript",
        "application/ecmascript",
        "application/x-ecmascript" -> JAVASCRIPT

        "application/typescript",
        "application/x-typescript" -> TYPESCRIPT
        "text/x-rust",
        "application/x-rust" -> RUST

        "text/x-sksl" -> AGSL
        "application/yaml",
        "text/x-yaml",
        "application/x-yaml" -> YAML
        "application/x-patch" -> PATCH

        else -> UNKNOWN
    }

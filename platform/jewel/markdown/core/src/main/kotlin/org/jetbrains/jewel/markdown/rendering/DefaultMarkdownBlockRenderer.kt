package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.util.JewelLogger
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

/**
 * Default implementation of [MarkdownBlockRenderer] that uses the provided styling, extensions, and inline renderer to
 * render [MarkdownBlock]s into Compose UI elements.
 *
 * @see MarkdownBlockRenderer
 */
@Suppress("OVERRIDE_DEPRECATION")
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
        val renderedContent = rememberRenderedContent(block, styling.inlinesStyling, enabled, onUrlClick)
        val textColor =
            styling.inlinesStyling.textStyle.color
                .takeOrElse { LocalContentColor.current }
                .takeOrElse { styling.inlinesStyling.textStyle.color }
        val mergedStyle = styling.inlinesStyling.textStyle.merge(TextStyle(color = textColor))

        Text(modifier = modifier, text = renderedContent, style = mergedStyle, inlineContent = renderedImages(block))
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
        val renderedContent = rememberRenderedContent(block, styling.inlinesStyling, enabled, onUrlClick)
        Column(modifier = modifier.padding(styling.padding)) {
            val textColor =
                styling.inlinesStyling.textStyle.color.takeOrElse {
                    LocalContentColor.current.takeOrElse { styling.inlinesStyling.textStyle.color }
                }
            val mergedStyle = styling.inlinesStyling.textStyle.merge(TextStyle(color = textColor))
            Text(
                text = renderedContent,
                style = mergedStyle,
                modifier = Modifier.focusProperties { this.canFocus = false },
                inlineContent = this@DefaultMarkdownBlockRenderer.renderedImages(block),
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
        val mimeType = block.mimeType ?: MimeType.Known.UNKNOWN
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
                        mimeType.displayName(),
                        styling.infoPosition.horizontalAlignment
                            ?: error("No horizontal alignment for position ${styling.infoPosition.name}"),
                        styling.infoTextStyle,
                        Modifier.fillMaxWidth().padding(styling.infoPadding),
                    )
                }

                RenderCodeWithMimeType(block, mimeType, styling, enabled)

                if (styling.infoPosition.verticalAlignment == Alignment.Bottom) {
                    FencedBlockInfo(
                        mimeType.displayName(),
                        styling.infoPosition.horizontalAlignment
                            ?: error("No horizontal alignment for position ${styling.infoPosition.name}"),
                        styling.infoTextStyle,
                        Modifier.fillMaxWidth().padding(styling.infoPadding),
                    )
                }
            }
        }
    }

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
                Modifier.focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
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
    private fun renderedImages(blockInlineContent: WithInlineMarkdown): Map<String, InlineTextContent> =
        rendererExtensions
            .firstNotNullOfOrNull { it.imageRendererExtension }
            ?.let { imagesRenderer ->
                getImages(blockInlineContent).associate { image ->
                    image.source to imagesRenderer.renderImageContent(image)
                }
            }
            .orEmpty()

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

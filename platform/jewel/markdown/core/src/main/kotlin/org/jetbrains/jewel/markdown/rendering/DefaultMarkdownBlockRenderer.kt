package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.theme.LocalContentColor
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
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontallyScrollableContainer
import org.jetbrains.jewel.ui.component.Text

@ExperimentalJewelApi
public open class DefaultMarkdownBlockRenderer(
    private val rootStyling: MarkdownStyling,
    private val rendererExtensions: List<MarkdownRendererExtension> = emptyList(),
    private val inlineRenderer: InlineMarkdownRenderer = DefaultInlineMarkdownRenderer(rendererExtensions),
) : MarkdownBlockRenderer {
    @Composable
    override fun render(
        blocks: List<MarkdownBlock>,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(rootStyling.blockVerticalSpacing)) {
            for (block in blocks) {
                render(block, enabled, onUrlClick, onTextClick)
            }
        }
    }

    @Composable
    override fun render(block: MarkdownBlock, enabled: Boolean, onUrlClick: (String) -> Unit, onTextClick: () -> Unit) {
        when (block) {
            is BlockQuote -> render(block, rootStyling.blockQuote, enabled, onUrlClick, onTextClick)
            is FencedCodeBlock -> render(block, rootStyling.code.fenced)
            is IndentedCodeBlock -> render(block, rootStyling.code.indented)
            is Heading -> render(block, rootStyling.heading, enabled, onUrlClick, onTextClick)
            is HtmlBlock -> render(block, rootStyling.htmlBlock)
            is OrderedList -> render(block, rootStyling.list.ordered, enabled, onUrlClick, onTextClick)
            is UnorderedList -> render(block, rootStyling.list.unordered, enabled, onUrlClick, onTextClick)
            is ListItem -> render(block, enabled, onUrlClick, onTextClick)
            is Paragraph -> render(block, rootStyling.paragraph, enabled, onUrlClick, onTextClick)
            ThematicBreak -> renderThematicBreak(rootStyling.thematicBreak)
            is CustomBlock -> {
                rendererExtensions
                    .find { it.blockRenderer?.canRender(block) == true }
                    ?.blockRenderer
                    ?.render(block, blockRenderer = this, inlineRenderer, enabled, onUrlClick, onTextClick)
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
    ) {
        val renderedContent = rememberRenderedContent(block, styling.inlinesStyling, enabled, onUrlClick)
        val textColor =
            styling.inlinesStyling.textStyle.color
                .takeOrElse { LocalContentColor.current }
                .takeOrElse { styling.inlinesStyling.textStyle.color }
        val mergedStyle = styling.inlinesStyling.textStyle.merge(TextStyle(color = textColor))
        val interactionSource = remember { MutableInteractionSource() }

        Text(
            modifier =
                Modifier.focusProperties { canFocus = false }
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onTextClick),
            text = renderedContent,
            style = mergedStyle,
        )
    }

    @Composable
    override fun render(
        block: Heading,
        styling: MarkdownStyling.Heading,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        when (block.level) {
            1 -> render(block, styling.h1, enabled, onUrlClick, onTextClick)
            2 -> render(block, styling.h2, enabled, onUrlClick, onTextClick)
            3 -> render(block, styling.h3, enabled, onUrlClick, onTextClick)
            4 -> render(block, styling.h4, enabled, onUrlClick, onTextClick)
            5 -> render(block, styling.h5, enabled, onUrlClick, onTextClick)
            6 -> render(block, styling.h6, enabled, onUrlClick, onTextClick)
            else -> error("Heading level ${block.level} not supported:\n$block")
        }
    }

    @Composable
    override fun render(
        block: Heading,
        styling: MarkdownStyling.Heading.HN,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        val renderedContent = rememberRenderedContent(block, styling.inlinesStyling, enabled, onUrlClick)
        Heading(
            renderedContent,
            styling.inlinesStyling.textStyle,
            styling.padding,
            styling.underlineWidth,
            styling.underlineColor,
            styling.underlineGap,
        )
    }

    @Composable
    private fun Heading(
        renderedContent: AnnotatedString,
        textStyle: TextStyle,
        paddingValues: PaddingValues,
        underlineWidth: Dp,
        underlineColor: Color,
        underlineGap: Dp,
    ) {
        Column(modifier = Modifier.padding(paddingValues)) {
            val textColor = textStyle.color.takeOrElse { LocalContentColor.current.takeOrElse { textStyle.color } }
            val mergedStyle = textStyle.merge(TextStyle(color = textColor))
            Text(text = renderedContent, style = mergedStyle, modifier = Modifier.focusProperties { canFocus = false })

            if (underlineWidth > 0.dp && underlineColor.isSpecified) {
                Spacer(Modifier.height(underlineGap))
                Divider(
                    orientation = Orientation.Horizontal,
                    modifier = Modifier.fillMaxWidth(),
                    color = underlineColor,
                    thickness = underlineWidth,
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
    ) {
        Column(
            Modifier.drawBehind {
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
                render(block.children, enabled, onUrlClick, onTextClick)
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
    ) {
        when (block) {
            is OrderedList -> render(block, styling.ordered, enabled, onUrlClick, onTextClick)
            is UnorderedList -> render(block, styling.unordered, enabled, onUrlClick, onTextClick)
        }
    }

    @Composable
    override fun render(
        block: OrderedList,
        styling: MarkdownStyling.List.Ordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        val itemSpacing =
            if (block.isTight) {
                styling.itemVerticalSpacingTight
            } else {
                styling.itemVerticalSpacing
            }

        Column(modifier = Modifier.padding(styling.padding), verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            for ((index, item) in block.children.withIndex()) {
                Row {
                    val number = block.startFrom + index
                    Text(
                        text = "$number${block.delimiter}",
                        style = styling.numberStyle,
                        color = styling.numberStyle.color.takeOrElse { LocalContentColor.current },
                        modifier =
                            Modifier.focusProperties { canFocus = false }
                                .widthIn(min = styling.numberMinWidth)
                                .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                        textAlign = styling.numberTextAlign,
                    )

                    Spacer(Modifier.width(styling.numberContentGap))

                    render(item, enabled, onUrlClick, onTextClick)
                }
            }
        }
    }

    @Composable
    override fun render(
        block: UnorderedList,
        styling: MarkdownStyling.List.Unordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        val itemSpacing =
            if (block.isTight) {
                styling.itemVerticalSpacingTight
            } else {
                styling.itemVerticalSpacing
            }

        Column(modifier = Modifier.padding(styling.padding), verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            for (item in block.children) {
                Row {
                    Text(
                        text = styling.bullet.toString(),
                        style = styling.bulletStyle,
                        color = styling.bulletStyle.color.takeOrElse { LocalContentColor.current },
                        modifier =
                            Modifier.focusProperties { canFocus = false }
                                .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                    )

                    Spacer(Modifier.width(styling.bulletContentGap))

                    render(item, enabled, onUrlClick, onTextClick)
                }
            }
        }
    }

    @Composable
    override fun render(block: ListItem, enabled: Boolean, onUrlClick: (String) -> Unit, onTextClick: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(rootStyling.blockVerticalSpacing)) {
            render(block.children, enabled, onUrlClick, onTextClick)
        }
    }

    @Composable
    override fun render(block: CodeBlock, styling: MarkdownStyling.Code) {
        when (block) {
            is FencedCodeBlock -> render(block, styling.fenced)
            is IndentedCodeBlock -> render(block, styling.indented)
        }
    }

    @Composable
    override fun render(block: IndentedCodeBlock, styling: MarkdownStyling.Code.Indented) {
        MaybeScrollingContainer(
            isScrollable = styling.scrollsHorizontally,
            Modifier.background(styling.background, styling.shape)
                .border(styling.borderWidth, styling.borderColor, styling.shape)
                .then(if (styling.fillWidth) Modifier.fillMaxWidth() else Modifier),
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
    override fun render(block: FencedCodeBlock, styling: MarkdownStyling.Code.Fenced) {
        val mimeType = block.mimeType ?: MimeType.Known.UNKNOWN
        MaybeScrollingContainer(
            isScrollable = styling.scrollsHorizontally,
            Modifier.background(styling.background, styling.shape)
                .border(styling.borderWidth, styling.borderColor, styling.shape)
                .then(if (styling.fillWidth) Modifier.fillMaxWidth() else Modifier),
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

                render(block, mimeType, styling)

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
    public open fun render(block: FencedCodeBlock, mimeType: MimeType, styling: MarkdownStyling.Code.Fenced) {
        val content = block.content
        val highlightedCode by
            LocalCodeHighlighter.current.highlight(content, mimeType).collectAsState(AnnotatedString(content))
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
    override fun renderThematicBreak(styling: MarkdownStyling.ThematicBreak) {
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.padding(styling.padding).fillMaxWidth(),
            color = styling.lineColor,
            thickness = styling.lineWidth,
        )
    }

    @Composable
    override fun render(block: HtmlBlock, styling: MarkdownStyling.HtmlBlock) {
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
    protected fun MaybeScrollingContainer(
        isScrollable: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        if (isScrollable) {
            HorizontallyScrollableContainer(modifier) { content() }
        } else {
            content()
        }
    }
}

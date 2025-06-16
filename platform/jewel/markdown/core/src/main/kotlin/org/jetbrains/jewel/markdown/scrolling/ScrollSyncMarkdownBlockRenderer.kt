// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.FencedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.IndentedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.extensions.markdownMode
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.Text

@Suppress("unused") // used in intellij
@ExperimentalJewelApi
public open class ScrollSyncMarkdownBlockRenderer(
    rootStyling: MarkdownStyling,
    renderingExtensions: List<MarkdownRendererExtension>,
    inlineRenderer: InlineMarkdownRenderer,
) : DefaultMarkdownBlockRenderer(rootStyling, renderingExtensions, inlineRenderer) {
    @Composable
    override fun render(
        block: MarkdownBlock,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        if (block is ScrollingSynchronizer.LocatableMarkdownBlock) {
            // Little trick that allows delegating rendering of underlying blocks to the superclass,
            // but using the wrapper in overloaded functions here to pass to AutoScrollableBlock
            val blocks = remember { mutableStateOf(block) }
            blocks.value = block
            CompositionLocalProvider(localUniqueBlock provides blocks.value) {
                // Don't recompose unchanged blocks
                // val ogBlock = remember(block) { block.originalBlock }
                super.render(block.originalBlock, enabled, onUrlClick, onTextClick, modifier)
            }
        } else {
            super.render(block, enabled, onUrlClick, onTextClick, modifier)
        }
    }

    @Composable
    override fun render(
        block: MarkdownBlock.Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        val synchronizer =
            (JewelTheme.markdownMode as? MarkdownMode.EditorPreview)?.scrollingSynchronizer
                ?: run {
                    super.render(block, styling, enabled, onUrlClick, onTextClick, modifier)
                    return
                }
        val uniqueBlock = localUniqueBlock.current?.takeIf { it.originalBlock == block } ?: block
        AutoScrollableBlock(uniqueBlock, synchronizer) {
            super.render(block, styling, enabled, onUrlClick, onTextClick, modifier)
        }
    }

    @Composable
    override fun render(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading.HN,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    ) {
        val synchronizer =
            (JewelTheme.markdownMode as? MarkdownMode.EditorPreview)?.scrollingSynchronizer
                ?: run {
                    super.render(block, styling, enabled, onUrlClick, onTextClick, modifier)
                    return
                }
        val uniqueBlock = localUniqueBlock.current?.takeIf { it.originalBlock == block } ?: block
        AutoScrollableBlock(uniqueBlock, synchronizer) {
            super.render(block, styling, enabled, onUrlClick, onTextClick, modifier)
        }
    }

    @Composable
    override fun renderCodeWithMimeType(
        block: FencedCodeBlock,
        mimeType: MimeType,
        styling: MarkdownStyling.Code.Fenced,
        enabled: Boolean,
    ) {
        val synchronizer =
            (JewelTheme.markdownMode as? MarkdownMode.EditorPreview)?.scrollingSynchronizer
                ?: run {
                    super.renderCodeWithMimeType(block, mimeType, styling, enabled)
                    return
                }

        val content = block.content
        val highlightedCode by
            LocalCodeHighlighter.current.highlight(content, block.mimeType).collectAsState(AnnotatedString(content))
        val uniqueBlock = localUniqueBlock.current?.takeIf { it.originalBlock == block } ?: block
        val actualBlock by rememberUpdatedState(uniqueBlock)
        AutoScrollableBlock(actualBlock, synchronizer) {
            Text(
                text = highlightedCode,
                style = styling.editorTextStyle,
                modifier =
                    Modifier.focusProperties { canFocus = false }
                        .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                onTextLayout = { textLayoutResult -> synchronizer.acceptTextLayout(actualBlock, textLayoutResult) },
            )
        }
    }

    @Composable
    override fun render(
        block: IndentedCodeBlock,
        styling: MarkdownStyling.Code.Indented,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        val scrollingSynchronizer =
            (JewelTheme.markdownMode as? MarkdownMode.EditorPreview)?.scrollingSynchronizer
                ?: run {
                    super.render(block, styling, enabled, modifier)
                    return
                }
        MaybeScrollingContainer(
            isScrollable = styling.scrollsHorizontally,
            modifier
                .background(styling.background, styling.shape)
                .border(styling.borderWidth, styling.borderColor, styling.shape)
                .then(if (styling.fillWidth) Modifier.fillMaxWidth() else Modifier),
        ) {
            val uniqueBlock = localUniqueBlock.current?.takeIf { it.originalBlock == block } ?: block
            val actualBlock by rememberUpdatedState(uniqueBlock)
            AutoScrollableBlock(actualBlock, scrollingSynchronizer, Modifier.padding(styling.padding)) {
                Text(
                    text = block.content,
                    style = styling.editorTextStyle,
                    color = styling.editorTextStyle.color.takeOrElse { LocalContentColor.current },
                    modifier =
                        Modifier.focusProperties { canFocus = false }
                            .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                    onTextLayout = { textLayoutResult ->
                        scrollingSynchronizer.acceptTextLayout(actualBlock, textLayoutResult)
                    },
                )
            }
        }
    }

    private val localUniqueBlock: ProvidableCompositionLocal<ScrollingSynchronizer.LocatableMarkdownBlock?> =
        staticCompositionLocalOf {
            null
        }
}

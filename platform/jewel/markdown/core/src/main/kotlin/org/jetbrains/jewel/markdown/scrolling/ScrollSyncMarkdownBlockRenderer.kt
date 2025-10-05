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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.FencedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.IndentedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownMode.EditorPreview
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.extensions.markdownMode
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer.LocatableMarkdownBlock
import org.jetbrains.jewel.ui.component.Text

/**
 * A [DefaultMarkdownBlockRenderer] that emits [AutoScrollableBlock]s, which allows scroll-syncing the rendered content
 * with another panel, like an editor.
 *
 * This is only active when the [`JewelTheme.markdownMode`][markdownMode] is
 * [`EditorPreview`][org.jetbrains.jewel.markdown.MarkdownMode.EditorPreview].
 */
@Suppress("unused") // used in intellij
@ApiStatus.Experimental
@ExperimentalJewelApi
public open class ScrollSyncMarkdownBlockRenderer(
    rootStyling: MarkdownStyling,
    renderingExtensions: List<MarkdownRendererExtension>,
    inlineRenderer: InlineMarkdownRenderer,
) : DefaultMarkdownBlockRenderer(rootStyling, renderingExtensions, inlineRenderer) {
    @Composable
    override fun RenderBlock(block: MarkdownBlock, enabled: Boolean, onUrlClick: (String) -> Unit, modifier: Modifier) {
        if (block is LocatableMarkdownBlock) {
            // Little trick that allows delegating rendering of underlying blocks to the superclass,
            // but using the wrapper in overloaded functions here to pass to AutoScrollableBlock
            val blocks = remember { mutableStateOf(block) }
            blocks.value = block
            CompositionLocalProvider(LocalLocatableBlock provides blocks.value) {
                // Don't recompose unchanged blocks
                // val ogBlock = remember(block) { block.originalBlock }
                super.RenderBlock(block.originalBlock, enabled, onUrlClick, modifier)
            }
        } else {
            super.RenderBlock(block, enabled, onUrlClick, modifier)
        }
    }

    @Composable
    override fun RenderParagraph(
        block: MarkdownBlock.Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        val synchronizer =
            (JewelTheme.markdownMode as? EditorPreview)?.scrollingSynchronizer
                ?: run {
                    super.RenderParagraph(block, styling, enabled, onUrlClick, modifier)
                    return
                }
        val uniqueBlock = LocalLocatableBlock.current?.takeIf { it.originalBlock == block } ?: block
        AutoScrollableBlock(uniqueBlock, synchronizer) {
            super.RenderParagraph(block, styling, enabled, onUrlClick, modifier)
        }
    }

    @Composable
    override fun RenderHeading(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        modifier: Modifier,
    ) {
        val synchronizer =
            (JewelTheme.markdownMode as? EditorPreview)?.scrollingSynchronizer
                ?: run {
                    super.RenderHeading(block, styling, enabled, onUrlClick, modifier)
                    return
                }
        val uniqueBlock = LocalLocatableBlock.current?.takeIf { it.originalBlock == block } ?: block
        AutoScrollableBlock(uniqueBlock, synchronizer) {
            super.RenderHeading(block, styling, enabled, onUrlClick, modifier)
        }
    }

    @Composable
    override fun RenderCodeWithMimeType(
        block: FencedCodeBlock,
        mimeType: MimeType,
        styling: MarkdownStyling.Code.Fenced,
        enabled: Boolean,
    ) {
        val synchronizer =
            (JewelTheme.markdownMode as? EditorPreview)?.scrollingSynchronizer
                ?: run {
                    super.RenderCodeWithMimeType(block, mimeType, styling, enabled)
                    return
                }

        val content = block.content
        val highlightedCode by
            LocalCodeHighlighter.current.highlight(content, block.mimeType).collectAsState(AnnotatedString(content))
        val uniqueBlock = LocalLocatableBlock.current?.takeIf { it.originalBlock == block } ?: block
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
    override fun RenderIndentedCodeBlock(
        block: IndentedCodeBlock,
        styling: MarkdownStyling.Code.Indented,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        val scrollingSynchronizer = (JewelTheme.markdownMode as? EditorPreview)?.scrollingSynchronizer
        if (scrollingSynchronizer == null) {
            super.RenderIndentedCodeBlock(block, styling, enabled, modifier)
            return
        }
        MaybeScrollingContainer(
            isScrollable = styling.scrollsHorizontally,
            modifier
                .background(styling.background, styling.shape)
                .border(styling.borderWidth, styling.borderColor, styling.shape)
                .then(if (styling.fillWidth) Modifier.fillMaxWidth() else Modifier),
        ) {
            val uniqueBlock = LocalLocatableBlock.current?.takeIf { it.originalBlock == block } ?: block
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

    @Suppress("VariableNaming", "property-naming", "PrivatePropertyName")
    private val LocalLocatableBlock: ProvidableCompositionLocal<LocatableMarkdownBlock?> = staticCompositionLocalOf {
        null
    }
}

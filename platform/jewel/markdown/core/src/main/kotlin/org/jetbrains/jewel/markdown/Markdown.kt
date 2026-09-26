package org.jetbrains.jewel.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.extensions.markdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.markdownProcessor
import org.jetbrains.jewel.markdown.extensions.markdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

private const val STYLING_FROM_RENDERER_MESSAGE =
    "The block renderer now provides the styling to use, including the block spacing. Provide a renderer with the " +
        "desired styling instead, e.g. by using ProvideMarkdownStyling."

/**
 * A Composable that renders a Markdown string.
 *
 * For large amounts of Markdown, such as documents, you can consider using [LazyMarkdown] instead to get better
 * performance.
 *
 * Both the styling of the rendered blocks and the vertical spacing between them come from the [blockRenderer]'s
 * [rootStyling][MarkdownBlockRenderer.rootStyling].
 *
 * @param markdown The Markdown string to render.
 * @param modifier The modifier to apply to this layout node.
 * @param selectable Whether the text can be selected.
 * @param enabled Whether the rendered content is enabled.
 * @param processingDispatcher The dispatcher to use for processing the Markdown.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param processor The processor to use for parsing the Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks, and the source of the styling to use.
 * @see LazyMarkdown
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun Markdown(
    @Language("Markdown") markdown: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    enabled: Boolean = true,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    onUrlClick: (String) -> Unit = {},
    processor: MarkdownProcessor = JewelTheme.markdownProcessor,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    var markdownBlocks by remember { mutableStateOf(emptyList<MarkdownBlock>()) }
    LaunchedEffect(markdown, processor) {
        markdownBlocks = withContext(processingDispatcher) { processor.processMarkdownDocument(markdown) }
    }

    Markdown(
        markdownBlocks = markdownBlocks,
        markdown = markdown,
        modifier = modifier,
        selectable = selectable,
        enabled = enabled,
        onUrlClick = onUrlClick,
        blockRenderer = blockRenderer,
    )
}

/**
 * A Composable that renders a Markdown string.
 *
 * @param markdown The Markdown string to render.
 * @param modifier The modifier to apply to this layout node.
 * @param selectable Whether the text can be selected.
 * @param enabled Whether the rendered content is enabled.
 * @param processingDispatcher The dispatcher to use for processing the Markdown.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param markdownStyling The styling to apply to the [blockRenderer] before rendering. Note that renderer extensions
 *   keep the styling they were created with, so custom blocks are not affected by this.
 * @param processor The processor to use for parsing the Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 * @see Markdown
 */
@Deprecated(
    STYLING_FROM_RENDERER_MESSAGE,
    ReplaceWith(
        "Markdown(markdown, modifier, selectable, enabled, processingDispatcher, onUrlClick, processor, " +
            "blockRenderer.createCopy(rootStyling = markdownStyling))"
    ),
)
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun Markdown(
    @Language("Markdown") markdown: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    enabled: Boolean = true,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    onUrlClick: (String) -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    processor: MarkdownProcessor = JewelTheme.markdownProcessor,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    val styledRenderer = remember(blockRenderer, markdownStyling) { blockRenderer.withStyling(markdownStyling) }

    Markdown(
        markdown = markdown,
        modifier = modifier,
        selectable = selectable,
        enabled = enabled,
        processingDispatcher = processingDispatcher,
        onUrlClick = onUrlClick,
        processor = processor,
        blockRenderer = styledRenderer,
    )
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a column.
 *
 * For large amounts of Markdown, such as documents, you can consider using [LazyMarkdown] instead to get better
 * performance.
 *
 * Both the styling of the rendered blocks and the vertical spacing between them come from the [blockRenderer]'s
 * [rootStyling][MarkdownBlockRenderer.rootStyling].
 *
 * @param markdownBlocks The list of Markdown blocks to render.
 * @param markdown The original Markdown string.
 * @param modifier The modifier to apply to this layout node.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks, and the source of the styling to use.
 * @see LazyMarkdown
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun Markdown(
    markdownBlocks: List<MarkdownBlock>,
    markdown: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectable: Boolean = false,
    onUrlClick: (String) -> Unit = {},
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    // We keep the existing behavior in terms of where the rawMarkdown semantic is applied to
    MaybeSelectable(selectable, Modifier.thenIf(selectable) { semantics { rawMarkdown = markdown } }) {
        @Suppress("ModifierNotUsedAtRoot") // Intentional
        Column(
            modifier.thenIf(!selectable) { semantics { rawMarkdown = markdown } },
            verticalArrangement = Arrangement.spacedBy(blockRenderer.rootStyling.blockVerticalSpacing),
        ) {
            for (block in markdownBlocks) {
                blockRenderer.RenderBlock(block, enabled, onUrlClick, Modifier)
            }
        }
    }
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a column.
 *
 * @param markdownBlocks The list of Markdown blocks to render.
 * @param markdown The original Markdown string.
 * @param modifier The modifier to apply to this layout node.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param markdownStyling The styling to apply to the [blockRenderer] before rendering. Note that renderer extensions
 *   keep the styling they were created with, so custom blocks are not affected by this.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 * @see Markdown
 */
@Deprecated(
    STYLING_FROM_RENDERER_MESSAGE,
    ReplaceWith(
        "Markdown(markdownBlocks, markdown, modifier, enabled, selectable, onUrlClick, " +
            "blockRenderer.createCopy(rootStyling = markdownStyling))"
    ),
)
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun Markdown(
    markdownBlocks: List<MarkdownBlock>,
    markdown: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectable: Boolean = false,
    onUrlClick: (String) -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    val styledRenderer = remember(blockRenderer, markdownStyling) { blockRenderer.withStyling(markdownStyling) }

    Markdown(
        markdownBlocks = markdownBlocks,
        markdown = markdown,
        modifier = modifier,
        enabled = enabled,
        selectable = selectable,
        onUrlClick = onUrlClick,
        blockRenderer = styledRenderer,
    )
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a lazy-loading column.
 *
 * For small amounts of Markdown, such as UI text, you should consider using [Markdown] instead to get better
 * performance.
 *
 * Both the styling of the rendered blocks and the vertical spacing between them come from the [blockRenderer]'s
 * [rootStyling][MarkdownBlockRenderer.rootStyling].
 *
 * @param blocks The list of Markdown blocks to render.
 * @param modifier The modifier to apply to this layout node.
 * @param contentPadding The padding to apply to the content.
 * @param state The state of the lazy list.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks, and the source of the styling to use.
 * @see Markdown
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun LazyMarkdown(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    enabled: Boolean = true,
    selectable: Boolean = false,
    onUrlClick: (String) -> Unit = {},
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    val blockVerticalSpacing = blockRenderer.rootStyling.blockVerticalSpacing

    MaybeSelectable(selectable, modifier) {
        LazyColumn(state = state, contentPadding = contentPadding) {
            itemsIndexed(blocks) { index, block ->
                blockRenderer.RenderBlock(
                    block = block,
                    enabled = enabled,
                    onUrlClick = onUrlClick,
                    modifier =
                        Modifier.padding(
                            top =
                                if (index == 0) {
                                    0.dp
                                } else {
                                    (blockVerticalSpacing / 2)
                                },
                            bottom =
                                if (index == blocks.lastIndex) {
                                    0.dp
                                } else {
                                    (blockVerticalSpacing / 2)
                                },
                        ),
                )
            }
        }
    }
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a lazy-loading column.
 *
 * @param blocks The list of Markdown blocks to render.
 * @param modifier The modifier to apply to this layout node.
 * @param contentPadding The padding to apply to the content.
 * @param state The state of the lazy list.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param markdownStyling The styling to apply to the [blockRenderer] before rendering. Note that renderer extensions
 *   keep the styling they were created with, so custom blocks are not affected by this.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 * @see LazyMarkdown
 */
@Deprecated(
    STYLING_FROM_RENDERER_MESSAGE,
    ReplaceWith(
        "LazyMarkdown(blocks, modifier, contentPadding, state, enabled, selectable, onUrlClick, " +
            "blockRenderer.createCopy(rootStyling = markdownStyling))"
    ),
)
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun LazyMarkdown(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    enabled: Boolean = true,
    selectable: Boolean = false,
    onUrlClick: (String) -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    val styledRenderer = remember(blockRenderer, markdownStyling) { blockRenderer.withStyling(markdownStyling) }

    LazyMarkdown(
        blocks = blocks,
        modifier = modifier,
        contentPadding = contentPadding,
        state = state,
        enabled = enabled,
        selectable = selectable,
        onUrlClick = onUrlClick,
        blockRenderer = styledRenderer,
    )
}

/**
 * Returns a [MarkdownBlockRenderer] that uses [styling] as its [MarkdownBlockRenderer.rootStyling], reusing this
 * instance when it already does, to avoid pointless copies in the common case.
 *
 * Note that renderer extensions capture their own styling when they are created, so custom blocks keep rendering with
 * the styling their extension was built with.
 */
private fun MarkdownBlockRenderer.withStyling(styling: MarkdownStyling): MarkdownBlockRenderer =
    if (rootStyling == styling) this else createCopy(rootStyling = styling)

@Composable
private fun MaybeSelectable(selectable: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val movableContent = remember { movableContentOf(content) }
    if (selectable) {
        SelectionContainer(modifier) { movableContent() }
    } else {
        movableContent()
    }
}

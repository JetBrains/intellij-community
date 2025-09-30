package org.jetbrains.jewel.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * A Composable that renders a Markdown string.
 *
 * For large amounts of Markdown, such as documents, you can consider using [LazyMarkdown] instead to get better
 * performance.
 *
 * @param markdown The Markdown string to render.
 * @param modifier The modifier to apply to this layout node.
 * @param selectable Whether the text can be selected.
 * @param enabled Whether the rendered content is enabled.
 * @param renderingDispatcher The dispatcher to use for processing the Markdown.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param onTextClick This parameter is ignored and is only here for binary/source compat reasons.
 * @param markdownStyling The styling to use for the rendered Markdown.
 * @param processor The processor to use for parsing the Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 */
@Deprecated(
    "Use the Markdown overload without onTextClick instead.",
    ReplaceWith(
        "Markdown(markdown, modifier, selectable, enabled, renderingDispatcher, onUrlClick, " +
            "markdownStyling, processor, blockRenderer)"
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
    renderingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    onUrlClick: (String) -> Unit = {},
    @Suppress("UnusedParameter", "RedundantSuppression") onTextClick: () -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    processor: MarkdownProcessor = JewelTheme.markdownProcessor,
    blockRenderer: MarkdownBlockRenderer = DefaultMarkdownBlockRenderer(markdownStyling),
) {
    Markdown(
        markdown,
        modifier,
        selectable,
        enabled,
        renderingDispatcher,
        onUrlClick,
        markdownStyling,
        processor,
        blockRenderer,
    )
}

/**
 * A Composable that renders a Markdown string.
 *
 * For large amounts of Markdown, such as documents, you can consider using [LazyMarkdown] instead to get better
 * performance.
 *
 * @param markdown The Markdown string to render.
 * @param modifier The modifier to apply to this layout node.
 * @param selectable Whether the text can be selected.
 * @param enabled Whether the rendered content is enabled.
 * @param processingDispatcher The dispatcher to use for processing the Markdown.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param markdownStyling The styling to use for the rendered Markdown.
 * @param processor The processor to use for parsing the Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 * @see Markdown
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
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    processor: MarkdownProcessor = JewelTheme.markdownProcessor,
    blockRenderer: MarkdownBlockRenderer = DefaultMarkdownBlockRenderer(markdownStyling),
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
        markdownStyling = markdownStyling,
        blockRenderer = blockRenderer,
    )
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a column.
 *
 * For large amounts of Markdown, such as documents, you can consider using [LazyMarkdown] instead to get better
 * performance.
 *
 * Note: the [onTextClick] parameter is ignored in this overload.
 *
 * @param markdownBlocks The list of Markdown blocks to render.
 * @param markdown The original Markdown string.
 * @param modifier The modifier to apply to this layout node.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param onTextClick This parameter is ignored and is only here for binary/source compat reasons.
 * @param markdownStyling The styling to use for the rendered Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 * @see Markdown
 */
@Deprecated(
    "Use the Markdown overload without onTextClick instead.",
    ReplaceWith(
        "Markdown(markdownBlocks, markdown, modifier, enabled, selectable, onUrlClick, markdownStyling, blockRenderer)"
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
    @Suppress("UnusedParameter", "RedundantSuppression") onTextClick: () -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = DefaultMarkdownBlockRenderer(markdownStyling),
) {
    Markdown(markdownBlocks, markdown, modifier, enabled, selectable, onUrlClick, markdownStyling, blockRenderer)
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a column.
 *
 * For large amounts of Markdown, such as documents, you can consider using [LazyMarkdown] instead to get better
 * performance.
 *
 * @param markdownBlocks The list of Markdown blocks to render.
 * @param markdown The original Markdown string.
 * @param modifier The modifier to apply to this layout node.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param markdownStyling The styling to use for the rendered Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 * @see Markdown
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
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = DefaultMarkdownBlockRenderer(markdownStyling),
) {
    // We keep the existing behavior in terms of where the rawMarkdown semantic is applied to
    MaybeSelectable(selectable, Modifier.thenIf(selectable) { semantics { rawMarkdown = markdown } }) {
        @Suppress("ModifierNotUsedAtRoot") // Intentional
        Column(
            modifier.thenIf(!selectable) { semantics { rawMarkdown = markdown } },
            verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
        ) {
            for (block in markdownBlocks) {
                blockRenderer.RenderBlock(block, enabled, onUrlClick, Modifier)
            }
        }
    }
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a lazy-loading column.
 *
 * For small amounts of Markdown, such as UI text, you should consider using [Markdown] instead to get better
 * performance.
 *
 * @param markdownBlocks The list of Markdown blocks to render.
 * @param modifier The modifier to apply to this layout node.
 * @param contentPadding The padding to apply to the content.
 * @param state The state of the lazy list.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param onTextClick This parameter is ignored and is only here for binary/source compat reasons.
 * @param markdownStyling The styling to use for the rendered Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
 */
@Deprecated(
    "Use the LazyMarkdown overload without onTextClick instead.",
    ReplaceWith(
        "LazyMarkdown(markdownBlocks, modifier, contentPadding, state, enabled, selectable, onUrlClick, " +
            "markdownStyling, blockRenderer)"
    ),
)
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun LazyMarkdown(
    markdownBlocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    enabled: Boolean = true,
    selectable: Boolean = false,
    onUrlClick: (String) -> Unit = {},
    @Suppress("UnusedParameter", "RedundantSuppression", "RedundantSuppression") onTextClick: () -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    LazyMarkdown(
        markdownBlocks,
        modifier,
        contentPadding,
        state,
        enabled,
        selectable,
        onUrlClick,
        markdownStyling,
        blockRenderer,
    )
}

/**
 * A Composable that renders a list of [MarkdownBlock]s in a lazy-loading column.
 *
 * For small amounts of Markdown, such as UI text, you should consider using [Markdown] instead to get better
 * performance.
 *
 * @param blocks The list of Markdown blocks to render.
 * @param modifier The modifier to apply to this layout node.
 * @param contentPadding The padding to apply to the content.
 * @param state The state of the lazy list.
 * @param enabled Whether the rendered content is enabled.
 * @param selectable Whether the text can be selected.
 * @param onUrlClick The callback to be invoked when a URL is clicked.
 * @param markdownStyling The styling to use for the rendered Markdown.
 * @param blockRenderer The renderer to use for rendering the Markdown blocks.
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
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    MaybeSelectable(selectable, modifier) {
        LazyColumn(
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
        ) {
            items(blocks) { block -> blockRenderer.RenderBlock(block, enabled, onUrlClick, Modifier) }
        }
    }
}

@Composable
private fun MaybeSelectable(selectable: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val movableContent = remember { movableContentOf(content) }
    if (selectable) {
        SelectionContainer(modifier) { movableContent() }
    } else {
        movableContent()
    }
}

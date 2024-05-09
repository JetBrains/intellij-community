package org.jetbrains.jewel.markdown.extensions

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
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

public val LocalMarkdownStyling: ProvidableCompositionLocal<MarkdownStyling> = staticCompositionLocalOf {
    error("No MarkdownStyling defined, have you forgotten to provide it?")
}

public val JewelTheme.Companion.markdownStyling: MarkdownStyling
    @Composable get() = LocalMarkdownStyling.current

public val LocalMarkdownProcessor: ProvidableCompositionLocal<MarkdownProcessor> = staticCompositionLocalOf {
    error("No MarkdownProcessor defined, have you forgotten to provide it?")
}

public val JewelTheme.Companion.markdownProcessor: MarkdownProcessor
    @Composable get() = LocalMarkdownProcessor.current

public val LocalMarkdownBlockRenderer: ProvidableCompositionLocal<MarkdownBlockRenderer> = staticCompositionLocalOf {
    error("No MarkdownBlockRenderer defined, have you forgotten to provide it?")
}

public val JewelTheme.Companion.markdownBlockRenderer: MarkdownBlockRenderer
    @Composable get() = LocalMarkdownBlockRenderer.current

@ExperimentalJewelApi
@Composable
public fun Markdown(
    @Language("Markdown") markdown: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    enabled: Boolean = true,
    renderingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    onUrlClick: (String) -> Unit = {},
    onTextClick: () -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    processor: MarkdownProcessor = JewelTheme.markdownProcessor,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    var markdownBlocks by remember { mutableStateOf(emptyList<MarkdownBlock>()) }
    LaunchedEffect(markdown) {
        markdownBlocks =
            withContext(renderingDispatcher) { processor.processMarkdownDocument(markdown) }
    }

    Markdown(
        markdownBlocks = markdownBlocks,
        modifier = modifier,
        selectable = selectable,
        enabled = enabled,
        onUrlClick = onUrlClick,
        onTextClick = onTextClick,
        markdownStyling = markdownStyling,
        blockRenderer = blockRenderer,
    )
}

@ExperimentalJewelApi
@Composable
public fun Markdown(
    markdownBlocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectable: Boolean = false,
    onUrlClick: (String) -> Unit = {},
    onTextClick: () -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    if (selectable) {
        SelectionContainer(modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing)) {
                for (block in markdownBlocks) {
                    blockRenderer.render(block, enabled, onUrlClick, onTextClick)
                }
            }
        }
    } else {
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
        ) {
            for (block in markdownBlocks) {
                blockRenderer.render(block, enabled, onUrlClick, onTextClick)
            }
        }
    }
}

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
    onTextClick: () -> Unit = {},
    markdownStyling: MarkdownStyling = JewelTheme.markdownStyling,
    blockRenderer: MarkdownBlockRenderer = JewelTheme.markdownBlockRenderer,
) {
    if (selectable) {
        SelectionContainer(modifier) {
            LazyColumn(
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
            ) {
                items(markdownBlocks) { block ->
                    blockRenderer.render(block, enabled, onUrlClick, onTextClick)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
        ) {
            items(markdownBlocks) { block ->
                blockRenderer.render(block, enabled, onUrlClick, onTextClick)
            }
        }
    }
}

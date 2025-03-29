package org.jetbrains.jewel.samples.standalone.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.standalone.dark
import org.jetbrains.jewel.intui.markdown.standalone.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.markdown.LazyMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding

@Composable
public fun MarkdownPreview(modifier: Modifier = Modifier, rawMarkdown: CharSequence) {
    val isDark = JewelTheme.isDark

    val markdownStyling = remember(isDark) { if (isDark) MarkdownStyling.dark() else MarkdownStyling.light() }

    var markdownBlocks by remember { mutableStateOf(emptyList<MarkdownBlock>()) }

    // We are doing this here for the sake of simplicity.
    // In a real-world scenario you would be doing this outside your Composables,
    // potentially involving ViewModels, dependency injection, etc.
    val processor = remember {
        MarkdownProcessor(
            listOf(
                AutolinkProcessorExtension,
                GitHubAlertProcessorExtension,
                GitHubStrikethroughProcessorExtension(),
                GitHubTableProcessorExtension,
            )
        )
    }

    LaunchedEffect(rawMarkdown) {
        // TODO you may want to debounce or drop on backpressure, in real usages. You should also
        // not do this
        //  in the UI to begin with.
        @Suppress("InjectDispatcher") // This should never go in the composable IRL
        markdownBlocks = withContext(Dispatchers.Default) { processor.processMarkdownDocument(rawMarkdown.toString()) }
    }

    val blockRenderer =
        remember(markdownStyling) {
            if (isDark) {
                MarkdownBlockRenderer.dark(
                    styling = markdownStyling,
                    rendererExtensions =
                        listOf(
                            GitHubAlertRendererExtension(AlertStyling.dark(), markdownStyling),
                            GitHubStrikethroughRendererExtension,
                            GitHubTableRendererExtension(GfmTableStyling.dark(), markdownStyling),
                        ),
                )
            } else {
                MarkdownBlockRenderer.light(
                    styling = markdownStyling,
                    rendererExtensions =
                        listOf(
                            GitHubAlertRendererExtension(AlertStyling.light(), markdownStyling),
                            GitHubStrikethroughRendererExtension,
                            GitHubTableRendererExtension(GfmTableStyling.light(), markdownStyling),
                        ),
                )
            }
        }

    // Using the values from the GitHub rendering to ensure contrast
    val background = remember(isDark) { if (isDark) Color(0xff0d1117) else Color.White }

    ProvideMarkdownStyling(markdownStyling, blockRenderer, NoOpCodeHighlighter) {
        val lazyListState = rememberLazyListState()
        VerticallyScrollableContainer(lazyListState, modifier.background(background)) {
            LazyMarkdown(
                markdownBlocks = markdownBlocks,
                modifier = Modifier.background(background),
                contentPadding =
                    PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp + scrollbarContentSafePadding(), bottom = 8.dp),
                state = lazyListState,
                selectable = true,
                onUrlClick = onUrlClick(),
            )
        }
    }
}

private fun onUrlClick(): (String) -> Unit = { url -> Desktop.getDesktop().browse(URI.create(url)) }

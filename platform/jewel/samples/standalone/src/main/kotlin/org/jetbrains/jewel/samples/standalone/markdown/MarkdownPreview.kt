package org.jetbrains.jewel.samples.standalone.markdown

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import java.awt.Desktop.getDesktop
import java.net.URI.create
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.frontmatter.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.frontmatter.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.jetbrains.jewel.intui.standalone.code.highlighting.SyntaxHighlightColors
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.frontmatter.FrontMatterProcessorExtension
import org.jetbrains.jewel.markdown.extensions.frontmatter.FrontMatterRendererExtension
import org.jetbrains.jewel.markdown.extensions.frontmatter.FrontMatterStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.extensions.images.Coil3ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.markdownMode
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.create
import org.jetbrains.jewel.markdown.scrolling.ScrollSyncMarkdownBlockRenderer
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding

@Composable
internal fun MarkdownPreview(rawMarkdown: CharSequence, scrollState: ScrollState, modifier: Modifier = Modifier) {
    val isDark = JewelTheme.isDark
    val instanceUuid = JewelTheme.instanceUuid

    val markdownStyling = remember(instanceUuid) { if (isDark) MarkdownStyling.dark() else MarkdownStyling.light() }

    var markdownBlocks by remember { mutableStateOf(emptyList<MarkdownBlock>()) }

    // We are doing this here for the sake of simplicity.
    // In a real-world scenario you would be doing this outside your Composables,
    // potentially involving ViewModels, dependency injection, etc.
    // The mode is what makes the processor tag blocks with their source lines.
    val markdownMode = JewelTheme.markdownMode
    val processor =
        remember(markdownMode) {
            MarkdownProcessor(
                listOf(
                    AutolinkProcessorExtension,
                    FrontMatterProcessorExtension,
                    GitHubAlertProcessorExtension,
                    GitHubStrikethroughProcessorExtension(),
                    GitHubTableProcessorExtension,
                ),
                markdownMode,
            )
        }

    val coilContext = LocalPlatformContext.current
    val coil3ImageRendererExtension =
        remember(coilContext) { Coil3ImageRendererExtension.withDefaultLoader(coilContext) }

    // Cancelling this effect does not stop a parse that is already running, and the scrolling synchronizer tracks one
    // parse at a time, so parses must never overlap: one background thread, in order.
    @Suppress("InjectDispatcher") // This should never go in the composable IRL
    val parsingDispatcher = remember { Dispatchers.Default.limitedParallelism(1) }
    LaunchedEffect(rawMarkdown) {
        // TODO you may want to debounce or drop on backpressure, in real usages. You should also
        // not do this
        //  in the UI to begin with.
        markdownBlocks = withContext(parsingDispatcher) { processor.processMarkdownDocument(rawMarkdown.toString()) }
    }

    val blockRenderer =
        remember(markdownStyling) {
            val rendererExtensions =
                if (isDark) {
                    listOf(
                        coil3ImageRendererExtension,
                        FrontMatterRendererExtension(FrontMatterStyling.dark()),
                        GitHubAlertRendererExtension(AlertStyling.dark(), markdownStyling),
                        GitHubStrikethroughRendererExtension,
                        GitHubTableRendererExtension(GfmTableStyling.dark(), markdownStyling),
                    )
                } else {
                    listOf(
                        coil3ImageRendererExtension,
                        FrontMatterRendererExtension(FrontMatterStyling.light()),
                        GitHubAlertRendererExtension(AlertStyling.light(), markdownStyling),
                        GitHubStrikethroughRendererExtension,
                        GitHubTableRendererExtension(GfmTableStyling.light(), markdownStyling),
                    )
                }
            // Reports every block's position to the scrolling synchronizer
            ScrollSyncMarkdownBlockRenderer(
                markdownStyling,
                rendererExtensions,
                InlineMarkdownRenderer.create(rendererExtensions),
            )
        }

    // Using the values from the GitHub rendering to ensure contrast
    val background = remember(instanceUuid) { if (isDark) Color(0xff0d1117) else Color.White }
    val codeHighlighter =
        remember(isDark) {
            SimpleCodeHighlighter(if (isDark) SyntaxHighlightColors.dark() else SyntaxHighlightColors.light())
        }

    ProvideMarkdownStyling(markdownStyling, blockRenderer, codeHighlighter, markdownMode, processor) {
        VerticallyScrollableContainer(scrollState = scrollState, modifier = modifier.background(background)) {
            // Not LazyMarkdown: ScrollingSynchronizer.create() returns null for a LazyListState.
            Markdown(
                markdownBlocks = markdownBlocks,
                markdown = rawMarkdown.toString(),
                modifier =
                    Modifier.padding(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp + scrollbarContentSafePadding(),
                        bottom = 8.dp,
                    ),
                selectable = true,
                onUrlClick = { url: String -> getDesktop().browse(create(url)) },
            )
        }
    }
}

package org.jetbrains.jewel.intui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.styling.dark
import org.jetbrains.jewel.intui.markdown.styling.light
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

public fun MarkdownBlockRenderer.Companion.light(
    styling: MarkdownStyling = MarkdownStyling.light(),
    rendererExtensions: List<MarkdownRendererExtension> = emptyList(),
    inlineRenderer: InlineMarkdownRenderer = InlineMarkdownRenderer.default(),
): MarkdownBlockRenderer =
    DefaultMarkdownBlockRenderer(styling, rendererExtensions, inlineRenderer)

public fun MarkdownBlockRenderer.Companion.dark(
    styling: MarkdownStyling = MarkdownStyling.dark(),
    rendererExtensions: List<MarkdownRendererExtension> = emptyList(),
    inlineRenderer: InlineMarkdownRenderer = InlineMarkdownRenderer.default(),
): MarkdownBlockRenderer =
    DefaultMarkdownBlockRenderer(styling, rendererExtensions, inlineRenderer)

@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    isDark: Boolean = JewelTheme.isDark,
    markdownStyling: MarkdownStyling = remember(isDark) {
        if (isDark) {
            MarkdownStyling.dark()
        } else {
            MarkdownStyling.light()
        }
    },
    markdownProcessor: MarkdownProcessor = remember { MarkdownProcessor() },
    markdownBlockRenderer: MarkdownBlockRenderer = remember(markdownStyling) {
        if (isDark) {
            MarkdownBlockRenderer.dark(markdownStyling)
        } else {
            MarkdownBlockRenderer.light(markdownStyling)
        }
    },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMarkdownStyling provides markdownStyling,
        LocalMarkdownProcessor provides markdownProcessor,
        LocalMarkdownBlockRenderer provides markdownBlockRenderer,
    ) {
        content()
    }
}

@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    markdownStyling: MarkdownStyling,
    markdownBlockRenderer: MarkdownBlockRenderer,
    markdownProcessor: MarkdownProcessor = remember { MarkdownProcessor() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMarkdownStyling provides markdownStyling,
        LocalMarkdownProcessor provides markdownProcessor,
        LocalMarkdownBlockRenderer provides markdownBlockRenderer,
    ) {
        content()
    }
}

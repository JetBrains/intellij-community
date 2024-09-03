package org.jetbrains.jewel.intui.markdown.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    themeName: String = JewelTheme.name,
    markdownStyling: MarkdownStyling = remember(themeName) { MarkdownStyling.create() },
    markdownProcessor: MarkdownProcessor = remember { MarkdownProcessor() },
    markdownBlockRenderer: MarkdownBlockRenderer =
        remember(markdownStyling) { MarkdownBlockRenderer.create(markdownStyling) },
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

package org.jetbrains.jewel.samples.ideplugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.create
import org.jetbrains.jewel.intui.markdown.styling.create
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import java.awt.Desktop
import java.net.URI

@Composable
internal fun MarkdownPreview(@Language("Markdown") rawMarkdown: String, modifier: Modifier = Modifier) {
    val themeKey = JewelTheme.name
    val markdownStyling = remember(themeKey) { MarkdownStyling.create() }

    val processor = remember { MarkdownProcessor() }
    // TODO move this away from the composition!
    val markdownBlocks by remember { derivedStateOf { processor.processMarkdownDocument(rawMarkdown) } }

    val blockRenderer =
        remember(markdownStyling) {
            MarkdownBlockRenderer.create(
                styling = markdownStyling,
                inlineRenderer = InlineMarkdownRenderer.default(),
            ) { url ->
                Desktop.getDesktop().browse(URI.create(url))
            }
        }

    SelectionContainer(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
        ) {
            for (block in markdownBlocks) {
                blockRenderer.render(block)
            }
        }
    }
}

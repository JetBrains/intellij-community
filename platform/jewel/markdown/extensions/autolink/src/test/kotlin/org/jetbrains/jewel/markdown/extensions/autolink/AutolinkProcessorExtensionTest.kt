package org.jetbrains.jewel.markdown.extensions.autolink

import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Assert.assertTrue
import org.junit.Test

public class AutolinkProcessorExtensionTest {
    // testing a simple case to assure wiring up our AutolinkProcessorExtension works correctly
    @Test
    public fun `https text gets processed into a link`() {
        val processor = MarkdownProcessor(listOf(AutolinkProcessorExtension))
        val rawMarkDown = "https://commonmark.org"
        val processed = processor.processMarkdownDocument(rawMarkDown)
        val paragraph = processed.first() as MarkdownBlock.Paragraph

        assertTrue(paragraph.inlineContent.first() is InlineMarkdown.Link)
    }
}

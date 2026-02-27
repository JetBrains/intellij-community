package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies how front matter surfaces in CommonMark's plain-text rendering (text extraction / search paths).
 *
 * Output is a normalized `key: value` reconstruction, not the verbatim source — see
 * [FrontMatterTextContentNodeRenderer] for why the raw text (delimiters, comments, formatting) cannot be reproduced.
 */
@OptIn(ExperimentalJewelApi::class)
public class FrontMatterTextContentRenderingTest {
    private val parser = Parser.builder().extensions(listOf(FrontMatterProcessorExtension.parserExtension)).build()

    private val renderer =
        TextContentRenderer.builder().extensions(listOf(FrontMatterProcessorExtension.textRendererExtension)).build()

    private fun render(rawMarkdown: String): String = renderer.render(parser.parse(rawMarkdown)).trim()

    @Test
    public fun `scalar entries are rendered as key-value text`() {
        val text =
            render(
                """
                |---
                |title: Hello World
                |author: John Doe
                |---
                """
                    .trimMargin()
            )

        assertEquals("title: Hello World\nauthor: John Doe", text)
    }

    @Test
    public fun `list values are rendered as comma-separated text`() {
        val text =
            render(
                """
                |---
                |tags:
                |  - a
                |  - b
                |---
                """
                    .trimMargin()
            )

        assertEquals("tags: a, b", text)
    }

    @Test
    public fun `unterminated front matter produces no text`() {
        val text =
            render(
                """
                |---
                |title: orphan
                """
                    .trimMargin()
            )

        assertEquals("", text)
    }
}

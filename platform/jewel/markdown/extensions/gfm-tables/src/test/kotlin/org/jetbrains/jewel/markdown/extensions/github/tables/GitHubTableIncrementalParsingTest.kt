// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class GitHubTableIncrementalParsingTest {
    @Test
    public fun `paragraph after table header becomes table row when edited`() {
        val processor = MarkdownProcessor(listOf(GitHubTableProcessorExtension), MarkdownMode.EditorPreview(null))

        val initialMarkdown =
            """
            | A | B |
            |---|---|
            |
            """
                .trimIndent()

        val initialBlocks = processor.processMarkdownDocument(initialMarkdown)

        assertEquals("Initial state should have 2 blocks", 2, initialBlocks.size)
        assertTrue("First block should be a table", initialBlocks[0] is TableBlock)
        assertTrue("Second block should be a paragraph", initialBlocks[1] is MarkdownBlock.Paragraph)

        val initialTable = initialBlocks[0] as TableBlock
        assertEquals("Initial table should have no body rows", 0, initialTable.rows.size)

        val updatedMarkdown =
            """
            | A | B |
            |---|---|
            | X | Y |
            """
                .trimIndent()

        val updatedBlocks = processor.processMarkdownDocument(updatedMarkdown)

        assertEquals("Updated state should have 1 block", 1, updatedBlocks.size)
        assertTrue("Block should be a table", updatedBlocks[0] is TableBlock)

        val updatedTable = updatedBlocks[0] as TableBlock
        assertEquals("Updated table should have 1 body row", 1, updatedTable.rows.size)
    }

    @Test
    public fun `typing after pipe character completes table row`() {
        val processor = MarkdownProcessor(listOf(GitHubTableProcessorExtension), MarkdownMode.EditorPreview(null))

        val step1 =
            """
            | Col1 | Col2 |
            |------|------|
            |
            """
                .trimIndent()

        processor.processMarkdownDocument(step1)

        val step2 =
            """
            | Col1 | Col2 |
            |------|------|
            | A
            """
                .trimIndent()

        val step2Blocks = processor.processMarkdownDocument(step2)
        assertEquals("After typing 'A', should have 1 block (table)", 1, step2Blocks.size)
        assertTrue("Block should be a table", step2Blocks[0] is TableBlock)

        val step3 =
            """
            | Col1 | Col2 |
            |------|------|
            | A | B |
            """
                .trimIndent()

        val step3Blocks = processor.processMarkdownDocument(step3)
        assertEquals("After completing row, should have 1 block (table)", 1, step3Blocks.size)

        val table = step3Blocks[0] as TableBlock
        assertEquals("Table should have 1 body row", 1, table.rows.size)
    }

    @Test
    public fun `editing paragraph after complete table does not break parsing`() {
        val processor = MarkdownProcessor(listOf(GitHubTableProcessorExtension), MarkdownMode.EditorPreview(null))

        val initialMarkdown =
            """
            | A | B |
            |---|---|
            | 1 | 2 |
            
            Some paragraph text
            """
                .trimIndent()

        val initialBlocks = processor.processMarkdownDocument(initialMarkdown)
        assertEquals("Should have 2 blocks", 2, initialBlocks.size)
        assertTrue("First block should be a table", initialBlocks[0] is TableBlock)
        assertTrue("Second block should be a paragraph", initialBlocks[1] is MarkdownBlock.Paragraph)

        val updatedMarkdown =
            """
            | A | B |
            |---|---|
            | 1 | 2 |
            
            Some edited paragraph text
            """
                .trimIndent()

        val updatedBlocks = processor.processMarkdownDocument(updatedMarkdown)
        assertEquals("Should still have 2 blocks", 2, updatedBlocks.size)
        assertTrue("First block should still be a table", updatedBlocks[0] is TableBlock)
        assertTrue("Second block should still be a paragraph", updatedBlocks[1] is MarkdownBlock.Paragraph)
    }
}

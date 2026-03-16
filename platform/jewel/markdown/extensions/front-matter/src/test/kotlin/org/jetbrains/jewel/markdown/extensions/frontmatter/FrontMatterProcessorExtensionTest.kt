package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.github.tables.TableBlock
import org.jetbrains.jewel.markdown.extensions.github.tables.TableCell
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)
public class FrontMatterProcessorExtensionTest {
    private val processor = MarkdownProcessor(listOf(FrontMatterProcessorExtension))

    @Test
    public fun `simple key-value front matter is parsed as two-row table`() {
        val rawMarkdown =
            """
            |---
            |title: Hello World
            |author: John Doe
            |---
            |
            |Some content.
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertEquals(1, table.rowCount - 1)

        // Verify header contains keys
        assertCellText("title", table.header.asNotNull().cells[0])
        assertCellText("author", table.header.asNotNull().cells[1])

        // Verify single data row contains values
        assertCellText("Hello World", table.rows[0].cells[0])
        assertCellText("John Doe", table.rows[0].cells[1])
    }

    @Test
    public fun `single key front matter is parsed as two-row table`() {
        val rawMarkdown =
            """
            |---
            |title: My Document
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(1, table.columnCount)
        assertEquals(1, table.rowCount - 1)
        assertCellText("title", table.header.asNotNull().cells[0])
        assertCellText("My Document", table.rows[0].cells[0])
    }

    @Test
    public fun `empty front matter produces no block`() {
        val rawMarkdown =
            """
            |---
            |---
            |
            |Some content.
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        // Empty front matter should be skipped, only the paragraph should remain
        assertTrue(blocks.none { it is TableBlock })
    }

    @Test
    public fun `list values are rendered as single-row nested table`() {
        val rawMarkdown =
            """
            |---
            |tags:
            |  - kotlin
            |  - compose
            |  - jewel
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(1, table.columnCount)
        assertEquals(1, table.rowCount - 1)

        // Header should contain the key
        assertCellText("tags", table.header.asNotNull().cells[0])

        // Value cell should contain a nested table
        val valueCell = table.rows[0].cells[0]
        val nestedTable = valueCell.content.assertIs<TableBlock>()
        assertEquals(3, nestedTable.columnCount)
        assertNull(nestedTable.header)
        assertEquals(1, nestedTable.rowCount)
        assertCellText("kotlin", nestedTable.rows[0].cells[0])
        assertCellText("compose", nestedTable.rows[0].cells[1])
        assertCellText("jewel", nestedTable.rows[0].cells[2])
    }

    @Test
    public fun `mixed scalar and list values`() {
        val rawMarkdown =
            """
            |---
            |title: My Post
            |tags:
            |  - one
            |  - two
            |author: Someone
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(3, table.columnCount)
        assertEquals(1, table.rowCount - 1)

        // Header contains all keys
        assertCellText("title", table.header.asNotNull().cells[0])
        assertCellText("tags", table.header.asNotNull().cells[1])
        assertCellText("author", table.header.asNotNull().cells[2])

        // Scalar value — inline content
        assertCellText("My Post", table.rows[0].cells[0])
        table.rows[0].cells[0].content.assertIs<MarkdownBlock.Paragraph>()

        // List value — block content with nested single-row table
        val nestedTable = table.rows[0].cells[1].content.assertIs<TableBlock>()
        assertEquals(2, nestedTable.columnCount)
        assertNull(nestedTable.header)
        assertCellText("one", nestedTable.rows[0].cells[0])
        assertCellText("two", nestedTable.rows[0].cells[1])

        // Scalar value after list
        assertCellText("Someone", table.rows[0].cells[2])
    }

    @Test
    public fun `front matter parsing stops on non-front-matter syntax`() {
        val rawMarkdown =
            """
            |---
            |title: Test
            |# Heading that should not be part of front matter
            |
            |Paragraph text.
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks[0].assertIs<TableBlock>()
        assertEquals(1, table.columnCount)
        assertCellText("title", table.header.asNotNull().cells[0])
        assertCellText("Test", table.rows[0].cells[0])

        blocks[1].assertIs<MarkdownBlock.Heading>()
        blocks[2].assertIs<MarkdownBlock.Paragraph>()
    }

    @Test
    public fun `content after front matter is preserved`() {
        val rawMarkdown =
            """
            |---
            |title: Test
            |---
            |
            |# Heading
            |
            |Paragraph text.
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        blocks[0].assertIs<TableBlock>()
        blocks[1].assertIs<MarkdownBlock.Heading>()
        blocks[2].assertIs<MarkdownBlock.Paragraph>()
    }

    @Test
    public fun `front matter not at document start is not parsed`() {
        val rawMarkdown =
            """
            |Some content first.
            |
            |---
            |title: Test
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        // When front matter is not at the start, the --- are treated as thematic breaks
        assertTrue(blocks.none { it is TableBlock })
    }

    @Test
    public fun `front matter with many keys produces a table`() {
        val rawMarkdown =
            """
            |---
            |key1: value1
            |key2: value2
            |key3:
            |  - item1
            |  - item2
            |  - item3
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(3, table.columnCount)
        assertEquals(1, table.rowCount - 1)

        // Header row: keys
        assertCellText("key1", table.header.asNotNull().cells[0])
        assertCellText("key2", table.header.asNotNull().cells[1])
        assertCellText("key3", table.header.asNotNull().cells[2])

        // Data row: values
        assertCellText("value1", table.rows[0].cells[0])
        assertCellText("value2", table.rows[0].cells[1])

        // List value: single-row nested table
        val listCell = table.rows[0].cells[2]
        val nestedTable = listCell.content.assertIs<TableBlock>()
        assertEquals(3, nestedTable.columnCount)
        assertNull(nestedTable.header)
        assertEquals(1, nestedTable.rowCount)
        assertCellText("item1", nestedTable.rows[0].cells[0])
        assertCellText("item2", nestedTable.rows[0].cells[1])
        assertCellText("item3", nestedTable.rows[0].cells[2])
    }

    @Test
    public fun `list items with colon are treated as plain text values`() {
        val rawMarkdown =
            """
            |---
            |tags:
            |  - team: platform
            |  - owner: jewel
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(1, table.columnCount)
        assertCellText("tags", table.header.asNotNull().cells[0])

        val valueCell = table.rows[0].cells[0]
        val nestedTable = valueCell.content.assertIs<TableBlock>()
        assertEquals(2, nestedTable.columnCount)
        assertNull(nestedTable.header)
        assertEquals(1, nestedTable.rowCount)
        assertCellText("team: platform", nestedTable.rows[0].cells[0])
        assertCellText("owner: jewel", nestedTable.rows[0].cells[1])
    }

    @Test
    public fun `literal block scalar preserves newlines`() {
        val rawMarkdown =
            """
            |---
            |description: |
            |  Line one
            |  Line two
            |  Line three
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("description", table.header.asNotNull().cells[0])
        assertCellText("Line one\nLine two\nLine three\n", table.rows[0].cells[0])
    }

    @Test
    public fun `literal block scalar preserves leading blank lines`() {
        val rawMarkdown =
            """
            |---
            |description: |
            |
            |  First line
            |  Second line
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("description", table.header.asNotNull().cells[0])
        assertCellText("\nFirst line\nSecond line\n", table.rows[0].cells[0])
    }

    @Test
    public fun `literal block scalar with strip chomping`() {
        val rawMarkdown =
            """
            |---
            |description: |-
            |  Line one
            |  Line two
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("Line one\nLine two", table.rows[0].cells[0])
    }

    @Test
    public fun `literal block scalar with keep chomping`() {
        val rawMarkdown =
            """
            |---
            |description: |+
            |  Line one
            |  Line two
            |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("Line one\nLine two\n\n", table.rows[0].cells[0])
    }

    @Test
    public fun `folded block scalar joins consecutive lines`() {
        val rawMarkdown =
            """
            |---
            |description: >
            |  This is a long
            |  description that
            |  spans multiple lines
            |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("This is a long description that spans multiple lines\n", table.rows[0].cells[0])
    }

    @Test
    public fun `folded block scalar with strip chomping`() {
        val rawMarkdown =
            """
            |---
            |description: >-
            |  This is a long
            |  description that
            |  spans multiple lines
            |  
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("This is a long description that spans multiple lines", table.rows[0].cells[0])
    }

    @Test
    public fun `folded block scalar with keep chomping`() {
        val rawMarkdown =
            """
            |---
            |description: >+
            |  Line one
            |  Line two
            |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("Line one Line two\n\n", table.rows[0].cells[0])
    }

    @Test
    public fun `folded block scalar with blank lines creates paragraph breaks`() {
        val rawMarkdown =
            """
            |---
            |description: >
            |  Paragraph one
            |  continued.
            |
            |  Paragraph two.
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("Paragraph one continued.\n\nParagraph two.\n", table.rows[0].cells[0])
    }

    @Test
    public fun `empty block scalar followed by end marker`() {
        val rawMarkdown =
            """
            |---
            |title: Test
            |description: |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertCellText("title", table.header.asNotNull().cells[0])
        assertCellText("description", table.header.asNotNull().cells[1])
        assertCellText("Test", table.rows[0].cells[0])
        assertCellText("", table.rows[0].cells[1])
    }

    @Test
    public fun `blank folded strip scalar followed by key starts a new key-value pair`() {
        val rawMarkdown =
            """
            |---
            |description: >-
            |
            |name: Hello World!
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertCellText("description", table.header.asNotNull().cells[0])
        assertCellText("name", table.header.asNotNull().cells[1])
        assertCellText("", table.rows[0].cells[0])
        assertCellText("Hello World!", table.rows[0].cells[1])
    }

    @Test
    public fun `block scalar followed by another key`() {
        val rawMarkdown =
            """
            |---
            |description: |
            |  Some text
            |  More text
            |author: Jane
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertCellText("description", table.header.asNotNull().cells[0])
        assertCellText("author", table.header.asNotNull().cells[1])
        assertCellText("Some text\nMore text\n", table.rows[0].cells[0])
        assertCellText("Jane", table.rows[0].cells[1])
    }

    @Test
    public fun `quoted values are unquoted`() {
        val rawMarkdown =
            """
            |---
            |title: "Hello World"
            |subtitle: 'Single Quoted'
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertCellText("Hello World", table.rows[0].cells[0])
        assertCellText("Single Quoted", table.rows[0].cells[1])
    }

    private fun assertCellText(expected: String, cell: TableCell) {
        val paragraph = cell.content.assertIs<MarkdownBlock.Paragraph>()
        val textContent = paragraph.inlineContent.filterIsInstance<InlineMarkdown.Text>()
        assertTrue(textContent.isNotEmpty())
        assertEquals(expected, textContent.first().content)
    }

    private fun <T> T?.asNotNull(): T {
        assertNotNull(this)
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    private inline fun <reified T : Any> Any.assertIs(): T {
        assertTrue(
            "An instance of ${this::class.qualifiedName} cannot be cast to ${T::class.qualifiedName}: $this",
            this is T,
        )
        return this as T
    }
}

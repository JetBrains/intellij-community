package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.UnorderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListItem
import org.jetbrains.jewel.markdown.extensions.github.tables.TableBlock
import org.jetbrains.jewel.markdown.extensions.github.tables.TableCell
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalJewelApi::class)
public class FrontMatterProcessorExtensionTest {
    private val processor = MarkdownProcessor(listOf(FrontMatterProcessorExtension))

    @Test
    public fun `simple key-value block is parsed as headerless two-column table`() {
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
        assertNull(table.header)
        assertEquals(2, table.rowCount)
        assertScalarRow(table, rowIndex = 0, key = "title", value = "Hello World")
        assertScalarRow(table, rowIndex = 1, key = "author", value = "John Doe")
    }

    @Test
    public fun `single key block is parsed as single table row`() {
        val rawMarkdown =
            """
            |---
            |title: Bye World
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertEquals(1, table.rowCount)
        assertScalarRow(table, rowIndex = 0, key = "title", value = "Bye World")
    }

    @Test
    public fun `empty block produces nothing`() {
        val rawMarkdown =
            """
            |---
            |---
            |
            |Some content.
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        // Empty block should be skipped, only the paragraph should remain
        assertTrue(blocks.none { it is TableBlock })
    }

    @Test
    public fun `lists are rendered as unordered list`() {
        val rawMarkdown =
            """
            |---
            |numbers:
            |  - unus
            |  - duo
            |  - tres
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertEquals(1, table.rowCount)
        assertListRow(table, rowIndex = 0, key = "numbers", values = listOf("unus", "duo", "tres"))
    }

    @Test
    public fun `single-item lists are rendered as unordered list`() {
        val rawMarkdown =
            """
            |---
            |the number:
            |  - nil
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertEquals(1, table.rowCount)
        assertListRow(table, rowIndex = 0, key = "the number", values = listOf("nil"))
    }

    @Test
    public fun `mixed scalar and list values`() {
        val rawMarkdown =
            """
            |---
            |title: Nothingness
            |tags:
            |  - suspense
            |  - unsettling
            |author: You don't want to know
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertEquals(3, table.rowCount)
        assertScalarRow(table, rowIndex = 0, key = "title", value = "Nothingness")
        assertListRow(table, rowIndex = 1, key = "tags", values = listOf("suspense", "unsettling"))
        assertScalarRow(table, rowIndex = 2, key = "author", value = "You don't want to know")
    }

    @Test
    public fun `block parsing stops on non-front-matter syntax`() {
        val rawMarkdown =
            """
            |---
            |reveal: The Secret to Live Forever Is
            |# Oh no, a Markdown heading stands in the way! The FrontMatter block skedaddles.
            |
            |Still not a FrontMatter block.
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks[0].assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertScalarRow(table, rowIndex = 0, key = "reveal", value = "The Secret to Live Forever Is")

        blocks[1].assertIs<MarkdownBlock.Heading>()
        blocks[2].assertIs<MarkdownBlock.Paragraph>()
    }

    @Test
    public fun `content after block is preserved`() {
        val rawMarkdown =
            """
            |---
            |Value: key
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
    public fun `block not at document start is not parsed`() {
        val rawMarkdown =
            """
            |Some content first.
            |
            |---
            |i: am not, in fact, frontmatter
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        // When block is not at the start, the --- are treated as thematic breaks
        assertTrue(blocks.none { it is TableBlock })
    }

    @Test
    public fun `list items with colon are treated as plain text values`() {
        val rawMarkdown =
            """
            |---
            |WANTED:
            |  - name: me =)
            |  - bounty: heavenly delight
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertListRow(table, rowIndex = 0, key = "WANTED", values = listOf("name: me =)", "bounty: heavenly delight"))
    }

    @Test
    public fun `literal block scalar preserves newlines`() {
        val rawMarkdown =
            """
            |---
            |lines: |
            |  Line five
            |  Line seventy-three
            |  Line minus one
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "lines", value = "Line five\nLine seventy-three\nLine minus one\n")
    }

    @Test
    public fun `literal block scalar preserves leading blank lines`() {
        val rawMarkdown =
            """
            |---
            |more: |
            |
            |  Third line
            |  Second line
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "more", value = "\nThird line\nSecond line\n")
    }

    @Test
    public fun `literal block scalar with strip chomping`() {
        val rawMarkdown =
            """
            |---
            |more: |-
            |  Line A
            |  Line GMaj7
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "more", value = "Line A\nLine GMaj7")
    }

    @Test
    public fun `literal block scalar with keep chomping`() {
        val rawMarkdown =
            """
            |---
            |twolines: |+
            |  Line
            |  Line too
            |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "twolines", value = "Line\nLine too\n\n")
    }

    @Test
    public fun `folded block scalar joins consecutive lines`() {
        val rawMarkdown =
            """
            |---
            |alphabet: >
            |  ABCDEFG
            |  HIJKLMNOP
            |  QRS
            |  TUV
            |  WXYZ
            |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "alphabet", value = "ABCDEFG HIJKLMNOP QRS TUV WXYZ\n")
    }

    @Test
    public fun `folded block scalar with strip chomping`() {
        val rawMarkdown =
            """
            |---
            |alphabet: >-
            |  ABCDEFG
            |  HIJKLMNOP
            |  QRS
            |  TUV
            |  WXYZ
            |  
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "alphabet", value = "ABCDEFG HIJKLMNOP QRS TUV WXYZ")
    }

    @Test
    public fun `folded block scalar with keep chomping`() {
        val rawMarkdown =
            """
            |---
            |alphabet: >+
            |  ABCDEFG
            |  HIJKLMNOP
            |  QRS
            |  TUV
            |  WXYZ
            |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "alphabet", value = "ABCDEFG HIJKLMNOP QRS TUV WXYZ\n\n")
    }

    @Test
    public fun `folded block scalar with blank lines creates paragraph breaks`() {
        val rawMarkdown =
            """
            |---
            |alphabet: >
            |  1234
            |  5.
            |
            |  67!
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "alphabet", value = "1234 5.\n\n67!\n")
    }

    @Test
    public fun `empty block scalar followed by end marker`() {
        val rawMarkdown =
            """
            |---
            |title: No
            |why: |
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertEquals(2, table.rowCount)
        assertScalarRow(table, rowIndex = 0, key = "title", value = "No")
        assertScalarRow(table, rowIndex = 1, key = "why", value = "")
    }

    @Test
    public fun `blank folded strip scalar followed by key starts a new key-value pair`() {
        val rawMarkdown =
            """
            |---
            |sneaky: >-
            |
            |ok: not sneaky
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertEquals(2, table.rowCount)
        assertScalarRow(table, rowIndex = 0, key = "sneaky", value = "")
        assertScalarRow(table, rowIndex = 1, key = "ok", value = "not sneaky")
    }

    @Test
    public fun `block scalar followed by another key`() {
        val rawMarkdown =
            """
            |---
            |yes: |
            |  no
            |  maybe
            |no: yes
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertEquals(2, table.columnCount)
        assertNull(table.header)
        assertEquals(2, table.rowCount)
        assertScalarRow(table, rowIndex = 0, key = "yes", value = "no\nmaybe\n")
        assertScalarRow(table, rowIndex = 1, key = "no", value = "yes")
    }

    @Test
    public fun `quoted values are unquoted`() {
        val rawMarkdown =
            """
            |---
            |line1: "line1: "
            |lineE: 'lineE: '
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val table = blocks.first().assertIs<TableBlock>()
        assertScalarRow(table, rowIndex = 0, key = "line1", value = "line1: ")
        assertScalarRow(table, rowIndex = 1, key = "lineE", value = "lineE: ")
    }

    private fun assertScalarRow(table: TableBlock, rowIndex: Int, key: String, value: String) {
        val row = table.rows[rowIndex]
        assertCellText(key, row.cells[0])
        assertCellText(value, row.cells[1])
    }

    private fun assertListRow(table: TableBlock, rowIndex: Int, key: String, values: List<String>) {
        val row = table.rows[rowIndex]
        assertCellText(key, row.cells[0])

        val list = row.cells[1].content.assertIs<UnorderedList>()
        assertTrue(list.isTight)
        assertEquals("-", list.marker)
        assertEquals(values.size, list.children.size)
        list.children.forEachIndexed { index, item -> assertListItemText(values[index], item) }
    }

    private fun assertListItemText(expected: String, item: ListItem) {
        assertEquals(0, item.level)
        assertEquals(1, item.children.size)
        assertCellText(expected, item.children.single().assertIs<MarkdownBlock.Paragraph>())
    }

    private fun assertCellText(expected: String, cell: TableCell) {
        assertCellText(expected, cell.content.assertIs<MarkdownBlock.Paragraph>())
    }

    private fun assertCellText(expected: String, paragraph: MarkdownBlock.Paragraph) {
        val textContent = paragraph.inlineContent.filterIsInstance<InlineMarkdown.Text>()
        assertTrue(textContent.isNotEmpty())
        assertEquals(expected, textContent.first().content)
    }

    private inline fun <reified T : Any> Any.assertIs(): T {
        assertTrue(
            "An instance of ${this::class.qualifiedName} cannot be cast to ${T::class.qualifiedName}: $this",
            this is T,
        )
        return this as T
    }
}

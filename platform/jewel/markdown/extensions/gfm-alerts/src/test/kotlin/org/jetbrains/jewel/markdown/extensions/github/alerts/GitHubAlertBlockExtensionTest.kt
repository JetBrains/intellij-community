package org.jetbrains.jewel.markdown.extensions.github.alerts

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Document
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertBlock.Caution
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertBlock.Important
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertBlock.Note
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertBlock.Tip
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertBlock.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class GitHubAlertBlockExtensionTest {
    private val parser = Parser.builder().extensions(listOf(GitHubAlertProcessorExtension.parserExtension)).build()

    private val renderer =
        TextContentRenderer.builder().extensions(listOf(GitHubAlertProcessorExtension.textRendererExtension)).build()

    @Test
    public fun `should parse note alert`() {
        val rawMarkdown =
            """
            |> [!NOTE]
            |> Highlights information that users should take into account, even when skimming.
            """
                .trimMargin()
        val parsed = parser.parse(rawMarkdown)

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Note node, but was ${firstChild.javaClass.name}", firstChild is Note)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        (firstContent as Paragraph)
        assertEquals(
            "Content text should be parsed correctly\n",
            "Highlights information that users should take into account, even when skimming.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse tip alert`() {
        val parsed =
            parser.parse(
                """
            |> [!TIP]
            |> Optional information to help a user be more successful.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Tip node, but was ${firstChild.javaClass.name}", firstChild is Tip)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Optional information to help a user be more successful.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse important alert`() {
        val parsed =
            parser.parse(
                """
            |> [!IMPORTANT]
            |> Crucial information necessary for users to succeed.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue(
            "Parsed node should be a Important node, but was ${firstChild.javaClass.name}",
            firstChild is Important,
        )

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Crucial information necessary for users to succeed.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse warning alert`() {
        val parsed =
            parser.parse(
                """
            |> [!WARNING]
            |> Critical content demanding immediate user attention due to potential risks.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Warning node, but was ${firstChild.javaClass.name}", firstChild is Warning)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Critical content demanding immediate user attention due to potential risks.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse caution alert`() {
        val parsed =
            parser.parse(
                """
            |> [!CAUTION]
            |> Negative potential consequences of an action.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Caution node, but was ${firstChild.javaClass.name}", firstChild is Caution)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Negative potential consequences of an action.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse lowercase note alert`() {
        val parsed =
            parser.parse(
                """
            |> [!note]
            |> Highlights information that users should take into account, even when skimming.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Note node, but was ${firstChild.javaClass.name}", firstChild is Note)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly\n",
            "Highlights information that users should take into account, even when skimming.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse lowercase tip alert`() {
        val parsed =
            parser.parse(
                """
            |> [!tip]
            |> Optional information to help a user be more successful.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Tip node, but was ${firstChild.javaClass.name}", firstChild is Tip)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Optional information to help a user be more successful.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse lowercase important alert`() {
        val parsed =
            parser.parse(
                """
            |> [!important]
            |> Crucial information necessary for users to succeed.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue(
            "Parsed node should be a Important node, but was ${firstChild.javaClass.name}",
            firstChild is Important,
        )

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Crucial information necessary for users to succeed.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse lowercase warning alert`() {
        val parsed =
            parser.parse(
                """
            |> [!warning]
            |> Critical content demanding immediate user attention due to potential risks.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Warning node, but was ${firstChild.javaClass.name}", firstChild is Warning)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Critical content demanding immediate user attention due to potential risks.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse lowercase caution alert`() {
        val parsed =
            parser.parse(
                """
            |> [!caution]
            |> Negative potential consequences of an action.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Caution node, but was ${firstChild.javaClass.name}", firstChild is Caution)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Negative potential consequences of an action.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should trim trailing and leading empty lines`() {
        val parsed =
            parser.parse(
                """
            |> [!CAUTION]
            |>
            |> Negative potential consequences of an action.
            |>
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue("Parsed node should be a Caution node, but was ${firstChild.javaClass.name}", firstChild is Caution)

        val contents = firstChild.children
        assertEquals("Parsed node should only have one direct child", 1, contents.size)

        val firstContent = contents.first()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals(
            "Content text should be parsed correctly.\n",
            "Negative potential consequences of an action.",
            renderer.render(firstContent),
        )
    }

    @Test
    public fun `should parse malformed entry as blockquote - space after exclamation mark`() {
        val parsed =
            parser.parse(
                """
            |> [! CAUTION]
            |> Negative potential consequences of an action.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue(
            "Parsed node should be a BlockQuote node, but was ${firstChild.javaClass.name}",
            firstChild is BlockQuote,
        )
    }

    @Test
    public fun `should parse malformed entry as blockquote - type not on first line`() {
        val parsed =
            parser.parse(
                """
            |>
            |> [! CAUTION]
            |> Negative potential consequences of an action.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue(
            "Parsed node should be a BlockQuote node, but was ${firstChild.javaClass.name}",
            firstChild is BlockQuote,
        )
    }

    @Test
    public fun `should parse malformed entry as blockquote - space before exclamation mark`() {
        val parsed =
            parser.parse(
                """
            |> [ !CAUTION]
            |> Negative potential consequences of an action.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue(
            "Parsed node should be a BlockQuote node, but was ${firstChild.javaClass.name}",
            firstChild is BlockQuote,
        )
    }

    @Test
    public fun `should parse malformed entry as blockquote - space after type`() {
        val parsed =
            parser.parse(
                """
            |> [!CAUTION ]
            |> Negative potential consequences of an action.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue(
            "Parsed node should be a BlockQuote node, but was ${firstChild.javaClass.name}",
            firstChild is BlockQuote,
        )
    }

    @Test
    public fun `should parse malformed entry as blockquote - missing newline`() {
        val parsed =
            parser.parse(
                """
            |> [!CAUTION] Negative potential consequences of an action.
            """
                    .trimMargin()
            )

        assertTrue("Parse result is not a document", parsed is Document)

        val children = parsed.children
        assertEquals("Parsed document should only have one direct child", 1, children.size)

        val firstChild = children.first()
        assertTrue(
            "Parsed node should be a BlockQuote node, but was ${firstChild.javaClass.name}",
            firstChild is BlockQuote,
        )
    }

    @Test
    public fun `should not fail on container markers followed by tab and non-letter character`() {
        val bulletInputs = listOf("+\t}", "-\t}", "*\t}")
        for (input in bulletInputs) {
            val parsed = parser.parse(input)
            val firstChild = parsed.children.single()
            assertTrue(
                "Expected BulletList for '$input', but was ${firstChild.javaClass.name}",
                firstChild is BulletList,
            )
            val listItem = firstChild.children.single()
            assertTrue("Expected ListItem, but was ${listItem.javaClass.name}", listItem is ListItem)
            val paragraph = listItem.children.single()
            assertEquals("}", renderer.render(paragraph))
        }

        val orderedParsed = parser.parse("1.\t}")
        val orderedList = orderedParsed.children.single()
        assertTrue("Expected OrderedList, but was ${orderedList.javaClass.name}", orderedList is OrderedList)
        assertEquals("}", renderer.render(orderedList.children.single().children.single()))

        val quoteParsed = parser.parse(">\t}")
        val blockQuote = quoteParsed.children.single()
        assertTrue("Expected BlockQuote, but was ${blockQuote.javaClass.name}", blockQuote is BlockQuote)
        assertEquals("}", renderer.render(blockQuote.children.single()))
    }

    @Test
    public fun `should parse alert indented with up to three spaces`() {
        val parsed =
            parser.parse(
                """
            |   > [!NOTE]
            |   > Indented alert content.
            """
                    .trimMargin()
            )

        val firstChild = parsed.children.single()
        assertTrue("Parsed node should be a Note node, but was ${firstChild.javaClass.name}", firstChild is Note)

        val firstContent = firstChild.children.single()
        assertTrue(
            "Parsed content should be a Paragraph, but was ${firstContent.javaClass.name}",
            firstContent is Paragraph,
        )
        assertEquals("Indented alert content.", renderer.render(firstContent))
    }

    @Test
    public fun `should parse alert indented with four spaces as code block`() {
        val parsed =
            parser.parse(
                """
            |    > [!NOTE]
            |    > Indented code block.
            """
                    .trimMargin()
            )

        val firstChild = parsed.children.single()
        assertTrue(
            "Parsed node should be an IndentedCodeBlock, but was ${firstChild.javaClass.name}",
            firstChild is IndentedCodeBlock,
        )
    }

    @Test
    public fun `should parse alert inside tab-indented list item`() {
        val parsed = parser.parse("-\t> [!NOTE]\n \t> Alert inside list item.")

        val bulletList = parsed.children.single()
        assertTrue("Expected BulletList, but was ${bulletList.javaClass.name}", bulletList is BulletList)

        val listItem = bulletList.children.single()
        assertTrue("Expected ListItem, but was ${listItem.javaClass.name}", listItem is ListItem)

        val alert = listItem.children.single()
        assertTrue("Expected Note, but was ${alert.javaClass.name}", alert is Note)

        val paragraph = alert.children.single()
        assertEquals("Alert inside list item.", renderer.render(paragraph))
    }
}

private val Node.children: List<Node>
    get() = buildList {
        var nextChild = firstChild
        while (nextChild != null) {
            add(nextChild)
            nextChild = nextChild.next
        }
    }

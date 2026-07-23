package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalJewelApi::class)
public class FrontMatterProcessorExtensionTest {
    private val processor = MarkdownProcessor(listOf(FrontMatterProcessorExtension))

    @Test
    public fun `simple key-value block is parsed as front matter entries`() {
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry("title", FrontMatter.Value.Scalar("Hello World")),
                    FrontMatter.Entry("author", FrontMatter.Value.Scalar("John Doe")),
                )
            ),
            frontMatter,
        )
    }

    @Test
    public fun `single key block is parsed as a single entry`() {
        val rawMarkdown =
            """
            |---
            |title: Bye World
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("title", FrontMatter.Value.Scalar("Bye World")))),
            frontMatter,
        )
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
        assertTrue(blocks.none { it is FrontMatter })
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(FrontMatter.Entry("numbers", FrontMatter.Value.ListValue(listOf("unus", "duo", "tres"))))
            ),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("the number", FrontMatter.Value.ListValue(listOf("nil"))))),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(3, frontMatter.entries.size)
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry("title", FrontMatter.Value.Scalar("Nothingness")),
                    FrontMatter.Entry("tags", FrontMatter.Value.ListValue(listOf("suspense", "unsettling"))),
                    FrontMatter.Entry("author", FrontMatter.Value.Scalar("You don't want to know")),
                )
            ),
            frontMatter,
        )
    }

    @Test
    public fun `block without closing delimiter is not treated as front matter`() {
        val rawMarkdown =
            """
            |---
            |reveal: The Secret to Live Forever Is
            |# Oh no, a comment stands in the way! The FrontMatter block skedaddles.
            |
            |Still not a FrontMatter block.
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        // No closing "---", so nothing is rendered as front matter; the rest is regular Markdown.
        assertTrue(blocks.none { it is FrontMatter })
        assertEquals(1, blocks.size)
        blocks[0].assertIs<MarkdownBlock.Paragraph>()
    }

    @Test
    public fun `block reaching end of document without closing delimiter is not treated as front matter`() {
        val rawMarkdown =
            """
            |---
            |title: This was meant to be normal markdown
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        assertTrue(blocks.none { it is FrontMatter })
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

        blocks[0].assertIs<FrontMatter>()
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
        assertTrue(blocks.none { it is FrontMatter })
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry(
                        "WANTED",
                        FrontMatter.Value.ListValue(listOf("name: me =)", "bounty: heavenly delight")),
                    )
                )
            ),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry(
                        "lines",
                        FrontMatter.Value.Scalar("Line five\nLine seventy-three\nLine minus one\n"),
                    )
                )
            ),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("more", FrontMatter.Value.Scalar("\nThird line\nSecond line\n")))),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("more", FrontMatter.Value.Scalar("Line A\nLine GMaj7")))),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("twolines", FrontMatter.Value.Scalar("Line\nLine too\n\n")))),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(FrontMatter.Entry("alphabet", FrontMatter.Value.Scalar("ABCDEFG HIJKLMNOP QRS TUV WXYZ\n")))
            ),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(FrontMatter.Entry("alphabet", FrontMatter.Value.Scalar("ABCDEFG HIJKLMNOP QRS TUV WXYZ")))
            ),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(FrontMatter.Entry("alphabet", FrontMatter.Value.Scalar("ABCDEFG HIJKLMNOP QRS TUV WXYZ\n\n")))
            ),
            frontMatter,
        )
    }

    @Test
    public fun `folded block scalar with blank lines creates paragraph breaks`() {
        val rawMarkdown =
            """
            |---
            |weird numbers: >
            |  1234
            |  5.
            |
            |  67!
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("weird numbers", FrontMatter.Value.Scalar("1234 5.\n\n67!\n")))),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry("title", FrontMatter.Value.Scalar("No")),
                    FrontMatter.Entry("why", FrontMatter.Value.Scalar("")),
                )
            ),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry("sneaky", FrontMatter.Value.Scalar("")),
                    FrontMatter.Entry("ok", FrontMatter.Value.Scalar("not sneaky")),
                )
            ),
            frontMatter,
        )
    }

    @Test
    public fun `block scalar followed by another key`() {
        val rawMarkdown =
            """
            |---
            |yes: |
            |  no
            |  maybe
            |no: hmm yes yes
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry("yes", FrontMatter.Value.Scalar("no\nmaybe\n")),
                    FrontMatter.Entry("no", FrontMatter.Value.Scalar("hmm yes yes")),
                )
            ),
            frontMatter,
        )
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

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry("line1", FrontMatter.Value.Scalar("line1: ")),
                    FrontMatter.Entry("lineE", FrontMatter.Value.Scalar("lineE: ")),
                )
            ),
            frontMatter,
        )
    }

    @Test
    public fun `full-line comments are ignored`() {
        val rawMarkdown =
            """
            |---
            |a: b
            |# this should be ignored at ALL COST!
            |c: d
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(
                listOf(
                    FrontMatter.Entry("a", FrontMatter.Value.Scalar("b")),
                    FrontMatter.Entry("c", FrontMatter.Value.Scalar("d")),
                )
            ),
            frontMatter,
        )
    }

    @Test
    public fun `comment in value is not ignored`() {
        val rawMarkdown =
            """
            |---
            |a: b # is this a comment?
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("a", FrontMatter.Value.Scalar("b # is this a comment?")))),
            frontMatter,
        )
    }

    @Test
    public fun `flow-style sequences should be processed as list`() {
        val rawMarkdown =
            """
            |---
            |a: [b, c, d]
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("a", FrontMatter.Value.ListValue(listOf("b", "c", "d"))))),
            frontMatter,
        )
    }

    @Test
    public fun `empty flow-style sequence should be processed a an empty list`() {
        val rawMarkdown =
            """
            |---
            |a: []
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(FrontMatter(listOf(FrontMatter.Entry("a", FrontMatter.Value.ListValue(emptyList())))), frontMatter)
    }

    @Test
    public fun `flow-style sequence with quoted items should be processed corectly`() {
        val rawMarkdown =
            """
            |---
            |a: ["a", "b, c", "d"]
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("a", FrontMatter.Value.ListValue(listOf("a", "b, c", "d"))))),
            frontMatter,
        )
    }

    @Test
    public fun `flow-style sequence with only one item should be processed as a list value`() {
        val rawMarkdown =
            """
            |---
            |a: [DRAFT]
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(
            FrontMatter(listOf(FrontMatter.Entry("a", FrontMatter.Value.ListValue(listOf("DRAFT"))))),
            frontMatter,
        )
    }

    @Test
    public fun `flow-style sequence wrapped in quotes should be processed as a scalar value`() {
        val rawMarkdown =
            """
            |---
            |a: "[DRAFT]"
            |---
            """
                .trimMargin()
        val blocks = processor.processMarkdownDocument(rawMarkdown)

        val frontMatter = blocks.first().assertIs<FrontMatter>()
        assertEquals(FrontMatter(listOf(FrontMatter.Entry("a", FrontMatter.Value.Scalar("[DRAFT]")))), frontMatter)
    }

    private inline fun <reified T : Any> Any.assertIs(): T {
        assertTrue(
            "An instance of ${this::class.qualifiedName} cannot be cast to ${T::class.qualifiedName}: $this",
            this is T,
        )
        return this as T
    }
}

package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.markdown.InlineMarkdown.Emphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.Link
import org.jetbrains.jewel.markdown.InlineMarkdown.StrongEmphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.Text
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Test

public class MarkdownProcessorDocumentParsingExtraTest {
    private val processor = MarkdownProcessor()

    @Test
    public fun `should parse spec sample 22b correctly (Backslash escapes)`() {
        val parsed = processor.processMarkdownDocument("[](/bar\\* \"ti\\*tle\")")

        /*
         * Expected HTML:
         * <p><a href="/bar*" title="ti*tle">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link("/bar*", "ti*tle", emptyList())))
    }

    @Test
    public fun `should parse spec sample 461b correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*_foo *bar*_*")

        /*
         * Expected HTML:
         * <p><em><em>foo <em>bar</em></em></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Emphasis("_", Text("foo "), Emphasis("*", Text("bar"))))))
    }

    @Test
    public fun `should parse spec sample 461c correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo *bar***")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo "), Emphasis("*", Text("bar")))))
    }

    @Test
    public fun `should parse spec sample 461d correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*_foo *bar* a_*")

        /*
         * Expected HTML:
         * <p><em><em>foo <em>bar</em> a</em></em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("*", Emphasis("_", Text("foo "), Emphasis("*", Text("bar")), Text(" a"))))
        )
    }

    @Test
    public fun `should parse spec sample 461e correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo *bar* a**")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em> a</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo "), Emphasis("*", Text("bar")), Text(" a"))))
    }

    @Test
    public fun `should parse spec sample 461f correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*_*foo *bar* a*_*")

        /*
         * Expected HTML:
         * <p><em><em><em>foo <em>bar</em> a</em></em></em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("*", Emphasis("_", Emphasis("*", Text("foo "), Emphasis("*", Text("bar")), Text(" a")))))
        )
    }

    @Test // Covers JEWEL-495's parsing side
    public fun `should combine inline styles correctly`() {
        val parsed =
            processor.processMarkdownDocument(
                "___One___, ***two***, _**three**_, **_four_**. [**Bold URL**](https://github.com/JetBrains/jewel). " +
                    "[_Italic URL_](https://github.com/JetBrains/jewel). [***Bold and italic URL***](https://github.com/JetBrains/jewel)"
            )

        /*
         * Expected HTML:
         * <em><strong>One</strong></em>, <em><strong>two</strong></em>, <em><strong>three</strong></em>,
         * <strong><em>four</em></strong>. <a href="https://github.com/JetBrains/jewel"><strong>Bold URL</strong></a>.
         * <a href="https://github.com/JetBrains/jewel"><em>Italic URL</em></a>.
         * <a href="https://github.com/JetBrains/jewel"><em><strong>Bold and italic URL</strong></em></a>
         */
        parsed.assertEquals(
            Paragraph(
                Emphasis("_", StrongEmphasis("__", Text("One"))),
                Text(", "),
                Emphasis("*", StrongEmphasis("**", Text("two"))),
                Text(", "),
                Emphasis("_", StrongEmphasis("**", Text("three"))),
                Text(", "),
                StrongEmphasis("**", Emphasis("_", Text("four"))),
                Text(". "),
                Link("https://github.com/JetBrains/jewel", title = null, StrongEmphasis("**", Text("Bold URL"))),
                Text(". "),
                Link("https://github.com/JetBrains/jewel", title = null, Emphasis("_", Text("Italic URL"))),
                Text(". "),
                Link(
                    "https://github.com/JetBrains/jewel",
                    title = null,
                    Emphasis("*", StrongEmphasis("**", Text("Bold and italic URL"))),
                ),
            )
        )
    }
}

package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.markdown.InlineMarkdown.Emphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.Link
import org.jetbrains.jewel.markdown.InlineMarkdown.StrongEmphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.Text
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Test

class MarkdownProcessorDocumentParsingExtraTest {
    private val processor = MarkdownProcessor()

    @Test
    fun `should parse spec sample 22b correctly (Backslash escapes)`() {
        val parsed = processor.processMarkdownDocument("[](/bar\\* \"ti\\*tle\")")

        /*
         * Expected HTML:
         * <p><a href="/bar*" title="ti*tle">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link("/bar*", "ti*tle", emptyList())))
    }

    @Test
    fun `should parse spec sample 461b correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*_foo *bar*_*")

        /*
         * Expected HTML:
         * <p><em><em>foo <em>bar</em></em></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Emphasis("_", Text("foo "), Emphasis("*", Text("bar"))))))
    }

    @Test
    fun `should parse spec sample 461c correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo *bar***")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo "), Emphasis("*", Text("bar")))))
    }

    @Test
    fun `should parse spec sample 461d correctly {Emphasis and strong emphasis}`() {
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
    fun `should parse spec sample 461e correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo *bar* a**")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em> a</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo "), Emphasis("*", Text("bar")), Text(" a"))))
    }

    @Test
    fun `should parse spec sample 461f correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*_*foo *bar* a*_*")

        /*
         * Expected HTML:
         * <p><em><em><em>foo <em>bar</em> a</em></em></em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("*", Emphasis("_", Emphasis("*", Text("foo "), Emphasis("*", Text("bar")), Text(" a")))))
        )
    }
}

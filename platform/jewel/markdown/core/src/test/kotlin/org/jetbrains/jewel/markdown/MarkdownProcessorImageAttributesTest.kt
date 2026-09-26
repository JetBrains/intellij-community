package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.markdown.InlineMarkdown.Image
import org.jetbrains.jewel.markdown.InlineMarkdown.Text
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Test

@Suppress("MarkdownUnresolvedFileReference")
public class MarkdownProcessorImageAttributesTest {
    private val processor = MarkdownProcessor()

    @Test
    public fun `parses image with numeric width and height`() {
        val parsed = processor.processMarkdownDocument("""![foo](image.jpg){width=100 height=50}""")

        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = DimensionSize.Pixels(100),
                    height = DimensionSize.Pixels(50),
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test
    public fun `parses image with only width specified`() {
        val parsed = processor.processMarkdownDocument("""![foo](image.jpg){width=200}""")

        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = DimensionSize.Pixels(200),
                    height = null,
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test
    public fun `parses image with only height specified`() {
        val parsed = processor.processMarkdownDocument("""![foo](image.jpg){height=150}""")

        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = null,
                    height = DimensionSize.Pixels(150),
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test
    public fun `parses image with unfinished attributes`() {
        val parsed = processor.processMarkdownDocument("""![foo](image.jpg){width=100px he}""")

        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = DimensionSize.Pixels(100),
                    height = null,
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test
    public fun `ignores invalid size values inside a valid image attribute block`() {
        val parsed = processor.processMarkdownDocument("![foo](image.jpg){width=??? height=200px}")

        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = null,
                    height = DimensionSize.Pixels(200),
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test
    public fun `ignores negative width inside a valid image attribute block`() {
        val parsed = processor.processMarkdownDocument("![foo](image.jpg){width=-100% height=200px}")

        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = null,
                    height = DimensionSize.Pixels(200),
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test
    public fun `ignores negative height inside a valid image attribute block`() {
        val parsed = processor.processMarkdownDocument("![foo](image.jpg){width=200 height=-100}")

        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = DimensionSize.Pixels(200),
                    height = null,
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test
    public fun `does not treat spaced image attributes as image attributes`() {
        val parsed = processor.processMarkdownDocument("![foo](image.jpg) {width=100}")

        parsed.assertEquals(
            Paragraph(
                Image(source = "image.jpg", alt = "foo", title = null, inlineContent = listOf(Text("foo"))),
                Text(" {width=100}"),
            )
        )
    }

    @Test
    public fun `does not consume attribute block when no valid attributes are found`() {
        val parsed = processor.processMarkdownDocument("![foo](image.jpg){wdth=50%} test")
        parsed.assertEquals(
            Paragraph(
                Image(source = "image.jpg", alt = "foo", title = null, inlineContent = listOf(Text("foo"))),
                Text("{wdth=50%} test"),
            )
        )
    }

    @Test
    public fun `parses image with px unit followed by semicolon`() {
        val parsed = processor.processMarkdownDocument("![foo](image.jpg){width=100px;}")
        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "image.jpg",
                    alt = "foo",
                    title = null,
                    width = DimensionSize.Pixels(100),
                    inlineContent = listOf(Text("foo")),
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    public fun `DimensionSize Pixel negative value should throw`() {
        DimensionSize.Pixels(-1)
    }
}

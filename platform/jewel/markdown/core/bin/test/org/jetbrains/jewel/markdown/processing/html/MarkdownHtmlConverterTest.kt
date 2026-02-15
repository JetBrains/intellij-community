// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.processing.html

import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.assertEquals
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Test

public class MarkdownHtmlConverterTest {
    private val processor = MarkdownProcessor(parseEmbeddedHtml = true)

    @Test
    public fun `parses paragraphs -- p`() {
        val parsed = processor.processMarkdownDocument("<p>Hello, world!</p>")
        parsed.assertEquals(MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("Hello, world!"))))
    }

    @Test
    public fun `parses ordered lists -- ol`() {
        val parsed = processor.processMarkdownDocument("<ol><li>Item 1</li><li>Item 2</li><li>Item 3</li></ol>")
        parsed.assertEquals(
            MarkdownBlock.ListBlock.OrderedList(
                listOf(
                    MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Item 1"))),
                    MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Item 2"))),
                    MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Item 3"))),
                ),
                isTight = true,
                startFrom = 1,
                delimiter = ".",
            )
        )
    }

    @Test
    public fun `parses unordered lists -- ul`() {
        val parsed = processor.processMarkdownDocument("<ul><li>Item 1</li><li>Item 2</li><li>Item 3</li></ul>")
        parsed.assertEquals(
            MarkdownBlock.ListBlock.UnorderedList(
                listOf(
                    MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Item 1"))),
                    MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Item 2"))),
                    MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Item 3"))),
                ),
                isTight = true,
                marker = ".",
            )
        )
    }

    @Test
    public fun `parses nested lists`() {
        val parsed = processor.processMarkdownDocument("<ul><li>Items:<ol><li>Just one item!</li></ol></li></ul>")
        parsed.assertEquals(
            MarkdownBlock.ListBlock.UnorderedList(
                listOf(
                    MarkdownBlock.ListItem(
                        MarkdownBlock.Paragraph(InlineMarkdown.Text("Items:")),
                        MarkdownBlock.ListBlock.OrderedList(
                            listOf(
                                MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Just one item!")))
                            ),
                            isTight = true,
                            startFrom = 1,
                            delimiter = ".",
                        ),
                    )
                ),
                isTight = true,
                marker = ".",
            )
        )
    }

    @Test
    public fun `parses multiline code -- code`() {
        val parsed =
            processor.processMarkdownDocument(
                """
            <code>
            fun main() {
                println("Hello, world!")
            }
            </code>
        """
                    .trimIndent()
            )
        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Code(
                        """
                    fun main() {
                        println("Hello, world!")
                    }
                """
                            .trimIndent()
                    )
                )
            )
        )
    }

    @Test
    public fun `parses multiline code -- pre`() {
        val parsed =
            processor.processMarkdownDocument(
                """
            <pre>
                fun main() {
                    println("Hello, pre!")
                }
            </pre>
            """
                    .trimIndent()
            )
        parsed.assertEquals(
            MarkdownBlock.CodeBlock.FencedCodeBlock(
                """
                    fun main() {
                        println("Hello, pre!")
                    }
                """
                    .trimIndent(),
                null,
            )
        )
    }

    @Test
    public fun `parses emphasis -- i`() {
        val parsed = processor.processMarkdownDocument("Look <i>here</i>!")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Look "),
                    InlineMarkdown.Emphasis("_", listOf(InlineMarkdown.Text("here"))),
                    InlineMarkdown.Text("!"),
                )
            )
        )
    }

    @Test
    public fun `parses emphasis -- em`() {
        val parsed = processor.processMarkdownDocument("Look <em>here</em>!")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Look "),
                    InlineMarkdown.Emphasis("_", listOf(InlineMarkdown.Text("here"))),
                    InlineMarkdown.Text("!"),
                )
            )
        )
    }

    @Test
    public fun `parses strong emphasis -- b`() {
        val parsed = processor.processMarkdownDocument("Look <b>here</b>!")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Look "),
                    InlineMarkdown.StrongEmphasis("**", listOf(InlineMarkdown.Text("here"))),
                    InlineMarkdown.Text("!"),
                )
            )
        )
    }

    @Test
    public fun `parses strong emphasis -- strong`() {
        val parsed = processor.processMarkdownDocument("Look <strong>here</strong>!")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Look "),
                    InlineMarkdown.StrongEmphasis("**", listOf(InlineMarkdown.Text("here"))),
                    InlineMarkdown.Text("!"),
                )
            )
        )
    }

    @Test
    public fun `parses links -- a`() {
        val parsed =
            processor.processMarkdownDocument("Look at <a href=\"https://example.com\" title=\"What\">this</a>!")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Look at "),
                    InlineMarkdown.Link(
                        destination = "https://example.com",
                        title = "What",
                        InlineMarkdown.Text("this"),
                    ),
                    InlineMarkdown.Text("!"),
                )
            )
        )
    }

    @Test
    public fun `parses code -- code`() {
        val parsed = processor.processMarkdownDocument("Paste this: <code>println(\"<i>sneaky...</i>\")</code>")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(InlineMarkdown.Text("Paste this: "), InlineMarkdown.Code("println(\"<i>sneaky...</i>\")"))
            )
        )
    }

    @Test
    public fun `parses code -- pre`() {
        val parsed = processor.processMarkdownDocument("Paste this: <pre>println(\"<i>sneaky...</i>\")</pre>")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(InlineMarkdown.Text("Paste this: "), InlineMarkdown.Code("println(\"<i>sneaky...</i>\")"))
            )
        )
    }

    @Test
    public fun `parses images -- img`() {
        val parsed =
            processor.processMarkdownDocument(
                "Look at <img alt=\"Jewel logo\" src=\"art/jewel-logo.svg\" width=\"20%\"/>!"
            )

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Look at "),
                    InlineMarkdown.Image(source = "art/jewel-logo.svg", alt = "Jewel logo", title = null),
                    InlineMarkdown.Text("!"),
                )
            )
        )
    }

    @Test
    public fun `parses single image in a paragraph`() {
        val parsed =
            processor.processMarkdownDocument("<img alt=\"Jewel logo\" src=\"art/jewel-logo.svg\" width=\"20%\"/>")

        parsed.assertEquals(
            MarkdownBlock.HtmlBlockWithAttributes(
                attributes = mapOf("width" to "20%", "src" to "art/jewel-logo.svg", "alt" to "Jewel logo"),
                mdBlock =
                    MarkdownBlock.Paragraph(
                        listOf(InlineMarkdown.Image(source = "art/jewel-logo.svg", alt = "Jewel logo", title = null))
                    ),
            )
        )
    }

    @Test
    public fun `parses line breaks -- br`() {
        val parsed = processor.processMarkdownDocument("Look at <br/> this!")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(InlineMarkdown.Text("Look at "), InlineMarkdown.HardLineBreak, InlineMarkdown.Text(" this!"))
            )
        )
    }

    @Test
    public fun `parses nested tags -- emphasis`() {
        val parsed = processor.processMarkdownDocument("Look at <i><b>this</b></i>!")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Look at "),
                    InlineMarkdown.Emphasis(
                        "_",
                        listOf(InlineMarkdown.StrongEmphasis("**", InlineMarkdown.Text("this"))),
                    ),
                    InlineMarkdown.Text("!"),
                )
            )
        )
    }

    @Test
    public fun `parses nested tags -- clickable image`() {
        val parsed =
            processor.processMarkdownDocument(
                """
            Please press <a href="https://example.com"><img alt="Jewel logo" src="art/jewel-logo.svg" width="20%"/></a>
        """
                    .trimIndent()
            )

        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Please press "),
                    InlineMarkdown.Link(
                        destination = "https://example.com",
                        title = null,
                        InlineMarkdown.Image(source = "art/jewel-logo.svg", alt = "Jewel logo", title = null),
                    ),
                )
            )
        )
    }

    @Test
    public fun `parses paragraph made of inline tag -- i`() {
        val parsed = processor.processMarkdownDocument("<i>Hello</i>")

        parsed.assertEquals(
            MarkdownBlock.Paragraph(listOf(InlineMarkdown.Emphasis("_", listOf(InlineMarkdown.Text("Hello")))))
        )
    }

    @Test
    public fun `parses inline tags inside HTML blocks -- list item text formatting`() {
        val parsed = processor.processMarkdownDocument("<ul><li>Item <b>1</b> and <i>two</i></li></ul>")

        parsed.assertEquals(
            MarkdownBlock.ListBlock.UnorderedList(
                listOf(
                    MarkdownBlock.ListItem(
                        MarkdownBlock.Paragraph(
                            InlineMarkdown.Text("Item "),
                            InlineMarkdown.StrongEmphasis("**", InlineMarkdown.Text("1")),
                            InlineMarkdown.Text(" and "),
                            InlineMarkdown.Emphasis("_", InlineMarkdown.Text("two")),
                        )
                    )
                ),
                isTight = true,
                marker = ".",
            )
        )
    }

    @Test
    public fun `parses inline tags inside HTML blocks -- mixed inlines and nested list`() {
        val parsed =
            processor.processMarkdownDocument(
                "<ul><li>Pay attention to <b>this</b>:<ol><li>Just one item!</li></ol></li></ul>"
            )

        parsed.assertEquals(
            MarkdownBlock.ListBlock.UnorderedList(
                listOf(
                    MarkdownBlock.ListItem(
                        MarkdownBlock.Paragraph(
                            InlineMarkdown.Text("Pay attention to "),
                            InlineMarkdown.StrongEmphasis("**", InlineMarkdown.Text("this")),
                            InlineMarkdown.Text(":"),
                        ),
                        MarkdownBlock.ListBlock.OrderedList(
                            listOf(
                                MarkdownBlock.ListItem(MarkdownBlock.Paragraph(InlineMarkdown.Text("Just one item!")))
                            ),
                            isTight = true,
                            startFrom = 1,
                            delimiter = ".",
                        ),
                    )
                ),
                isTight = true,
                marker = ".",
            )
        )
    }

    @Test
    public fun `parses inline tags inside HTML blocks with -- leading and trailing spaces`() {
        val parsed = processor.processMarkdownDocument("<p>Can I keep my beloved<b> spaces </b>???</p>")
        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Can I keep my beloved"),
                    InlineMarkdown.StrongEmphasis("**", listOf(InlineMarkdown.Text(" spaces "))),
                    InlineMarkdown.Text("???"),
                )
            )
        )
    }

    @Test
    public fun `parses headers -- h1`() {
        val parsed = processor.processMarkdownDocument("<h1>Hello</h1>")
        parsed.assertEquals(MarkdownBlock.Heading(inlineContent = listOf(InlineMarkdown.Text("Hello")), level = 1))
    }

    @Test
    public fun `parses headers -- h6`() {
        val parsed = processor.processMarkdownDocument("<h6>Very small</h6>")
        parsed.assertEquals(MarkdownBlock.Heading(inlineContent = listOf(InlineMarkdown.Text("Very small")), level = 6))
    }

    @Test
    public fun `parses headers with inline formatting -- h3`() {
        val parsed = processor.processMarkdownDocument("<h3>Look <i>here</i></h3>")
        parsed.assertEquals(
            MarkdownBlock.Heading(
                inlineContent =
                    listOf(
                        InlineMarkdown.Text("Look "),
                        InlineMarkdown.Emphasis("_", listOf(InlineMarkdown.Text("here"))),
                    ),
                level = 3,
            )
        )
    }

    @Test
    public fun `parses multiline code with embedded html -- code`() {
        val parsed =
            processor.processMarkdownDocument(
                """
            <code>
            fun main() {
                println("<i>Hello</i> & <b>tags</b> inside!")
            }
            </code>
        """
                    .trimIndent()
            )
        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Code(
                        """
                    fun main() {
                        println("<i>Hello</i> & <b>tags</b> inside!")
                    }
                """
                            .trimIndent()
                    )
                )
            )
        )
    }

    @Test
    public fun `parses multiline code with embedded html -- pre`() {
        val parsed =
            processor.processMarkdownDocument(
                """
            <pre>
                fun main() {
                    println("<i>Hello</i> & <b>tags</b> inside pre!")
                }
            </pre>
            """
                    .trimIndent()
            )
        parsed.assertEquals(
            MarkdownBlock.CodeBlock.FencedCodeBlock(
                """
                    fun main() {
                        println("<i>Hello</i> & <b>tags</b> inside pre!")
                    }
                """
                    .trimIndent(),
                null,
            )
        )
    }

    @Test
    public fun `inline code preserves literal HTML -- complex`() {
        val parsed =
            processor.processMarkdownDocument(
                "Check: <code>2 < 3 && <b>true</b> <a href=\"https://ex\">x</a> <img alt=\"z\" src=\"s.png\"/></code> done"
            )
        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Check: "),
                    InlineMarkdown.Code(
                        "2 < 3 && <b>true</b> <a href=\"https://ex\">x</a> <img alt=\"z\" src=\"s.png\"/>"
                    ),
                    InlineMarkdown.Text(" done"),
                )
            )
        )
    }

    @Test
    public fun `inline pre preserves literal HTML`() {
        val parsed = processor.processMarkdownDocument("Also: <pre>1 < 2 & <i>x</i></pre>!")
        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(InlineMarkdown.Text("Also: "), InlineMarkdown.Code("1 < 2 & <i>x</i>"), InlineMarkdown.Text("!"))
            )
        )
    }

    @Test
    public fun `inline code keeps non-tags with angle brackets`() {
        val parsed = processor.processMarkdownDocument("Edge: <code>2 < 3 && <not-a-tag> + <a-tag></code> ok")
        parsed.assertEquals(
            MarkdownBlock.Paragraph(
                listOf(
                    InlineMarkdown.Text("Edge: "),
                    InlineMarkdown.Code("2 < 3 && <not-a-tag> + <a-tag>"),
                    InlineMarkdown.Text(" ok"),
                )
            )
        )
    }

    @Test
    public fun `two HTML blocks back-to-back without blank line -- p then p`() {
        val parsed = processor.processMarkdownDocument("<p>Hee-</p><p>Ho!</p>")
        parsed.assertEquals(
            MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("Hee-"))),
            MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("Ho!"))),
        )
    }

    @Test
    public fun `HTML block followed by Markdown paragraph without blank line -- h3 then md`() {
        val parsed = processor.processMarkdownDocument("<h3>Head</h3>Tail")
        parsed.assertEquals(
            MarkdownBlock.Heading(inlineContent = listOf(InlineMarkdown.Text("Head")), level = 3),
            MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("Tail"))),
        )
    }

    @Test
    public fun `wraps blocks with HtmlBlockWithAttributes -- p with attributes`() {
        val parsed = processor.processMarkdownDocument("<p class=\"greeting\" align='center'>Hello</p>")

        parsed.assertEquals(
            MarkdownBlock.HtmlBlockWithAttributes(
                attributes = mapOf("class" to "greeting", "align" to "center"),
                mdBlock = MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("Hello"))),
            )
        )
    }

    @Test
    public fun `wraps blocks with HtmlBlockWithAttributes -- h3 with attributes`() {
        val parsed = processor.processMarkdownDocument("<h3 id=\"title\" align='center'>Head</h3>")

        parsed.assertEquals(
            MarkdownBlock.HtmlBlockWithAttributes(
                attributes = mapOf("id" to "title", "align" to "center"),
                mdBlock = MarkdownBlock.Heading(inlineContent = listOf(InlineMarkdown.Text("Head")), level = 3),
            )
        )
    }
}

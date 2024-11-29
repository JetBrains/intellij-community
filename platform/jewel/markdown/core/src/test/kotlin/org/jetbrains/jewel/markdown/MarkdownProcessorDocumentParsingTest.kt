package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.markdown.InlineMarkdown.Code
import org.jetbrains.jewel.markdown.InlineMarkdown.Emphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.HardLineBreak
import org.jetbrains.jewel.markdown.InlineMarkdown.HtmlInline
import org.jetbrains.jewel.markdown.InlineMarkdown.Image
import org.jetbrains.jewel.markdown.InlineMarkdown.Link
import org.jetbrains.jewel.markdown.InlineMarkdown.SoftLineBreak
import org.jetbrains.jewel.markdown.InlineMarkdown.StrongEmphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.Text
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.junit.Test

/**
 * This class tests that all the snippets in the CommonMark 0.31.2 specs are rendered correctly into MarkdownBlocks,
 * matching what the CommonMark 0.20 HTML renderer tests also validate. Test cases are extracted from [here](
 * https://spec.commonmark.org/0.31.2/spec.json).
 *
 * Note that the reference HTML output is only there as information; our parsing logic performs various transformations
 * that CommonMark wouldn't. For more info, refer to [MarkdownProcessor.processMarkdownDocument].
 */
@Suppress(
    "HtmlDeprecatedAttribute",
    "HtmlRequiredAltAttribute",
    "HtmlUnknownAttribute",
    "HtmlUnknownTarget",
    "MarkdownLinkDestinationWithSpaces",
    "MarkdownUnresolvedFileReference",
    "MarkdownUnresolvedLinkLabel",
    "MarkdownUnresolvedHeaderReference",
    "MarkdownIncorrectlyNumberedListItem",
    "LargeClass", // Detekt hates huge test suites I guess
) // All used in purposefully odd Markdown
class MarkdownProcessorDocumentParsingTest {
    private val processor = MarkdownProcessor()

    @Test
    fun `should parse spec sample 1 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("\tfoo\tbaz\t\tbim")

        /*
         * Expected HTML:
         * <pre><code>foo→baz→→bim
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("foo\tbaz\t\tbim"))
    }

    @Test
    fun `should parse spec sample 2 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("  \tfoo\tbaz\t\tbim")

        /*
         * Expected HTML:
         * <pre><code>foo→baz→\tbim
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("foo\tbaz\t\tbim"))
    }

    @Test
    fun `should parse spec sample 3 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("    a\ta\n    ὐ\ta")

        /*
         * Expected HTML:
         * <pre><code>a→a
         * ὐ→a
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("a\ta\nὐ\ta"))
    }

    @Test
    fun `should parse spec sample 4 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("  - foo\n\n\tbar")

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <p>bar</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"), paragraph("bar")), isTight = false))
    }

    @Test
    fun `should parse spec sample 5 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("- foo\n\n\t\tbar")

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <pre><code>  bar
         * </code></pre>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"), indentedCodeBlock("  bar")), isTight = false))
    }

    @Test
    fun `should parse spec sample 6 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument(">\t\tfoo")

        /*
         * Expected HTML:
         * <blockquote>
         * <pre><code>  foo
         * </code></pre>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(indentedCodeBlock("  foo")))
    }

    @Test
    fun `should parse spec sample 7 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("-\t\tfoo")

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <pre><code>  foo
         * </code></pre>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(indentedCodeBlock("  foo"))))
    }

    @Test
    fun `should parse spec sample 8 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("    foo\n\tbar")

        /*
         * Expected HTML:
         * <pre><code>foo
         * bar
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("foo\nbar"))
    }

    @Test
    fun `should parse spec sample 9 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument(" - foo\n   - bar\n\t - baz")

        /*
         * Expected HTML:
         * <ul>
         * <li>foo
         * <ul>
         * <li>bar
         * <ul>
         * <li>baz</li>
         * </ul>
         * </li>
         * </ul>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(
                    paragraph("foo"),
                    unorderedList(listItem(paragraph("bar"), unorderedList(listItem(paragraph("baz"))))),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 10 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("#\tFoo")

        /*
         * Expected HTML:
         * <h1>Foo</h1>
         */
        parsed.assertEquals(heading(level = 1, Text("Foo")))
    }

    @Test
    fun `should parse spec sample 11 correctly {Tabs}`() {
        val parsed = processor.processMarkdownDocument("*\t*\t*\t")

        /*
         * Expected HTML:
         * <hr />
         */
        parsed.assertEquals(thematicBreak())
    }

    @Test
    fun `should parse spec sample 12 correctly {Backslash escapes}`() {
        val parsed =
            processor.processMarkdownDocument(
                "\\!\\\"\\#\\$\\%\\&\\'\\(\\)\\*\\+\\,\\-\\.\\/\\:\\;\\<\\=\\>\\?\\@\\[\\\\\\]\\^\\_\\`\\{\\|\\}\\~\n"
            )

        /*
         * Expected HTML:
         * <p>!&quot;#$%&amp;'()*+,-./:;&lt;=&gt;?@[\]^_`{|}~</p>
         */
        parsed.assertEquals(paragraph("!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"))
    }

    @Test
    fun `should parse spec sample 13 correctly {Backslash escapes}`() {
        val parsed = processor.processMarkdownDocument("\\\t\\A\\a\\ \\3\\φ\\«")

        /*
         * Expected HTML:
         * <p>\→\A\a\ \3\φ\«</p>
         */
        parsed.assertEquals(paragraph("\\\t\\A\\a\\ \\3\\φ\\«"))
    }

    @Test
    fun `should parse spec sample 14 correctly {Backslash escapes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |\*not emphasized*
                |\<br/> not a tag
                |\[not a link](/foo)
                |\`not code`
                |1\. not a list
                |\* not a list
                |\# not a heading
                |\[foo]: /url "not a reference"
                |\&ouml; not a character entity
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>*not emphasized*
         * &lt;br/&gt; not a tag
         * [not a link](/foo)
         * `not code`
         * 1. not a list
         * * not a list
         * # not a heading
         * [foo]: /url &quot;not a reference&quot;
         * &amp;ouml; not a character entity</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("*not emphasized*"),
                SoftLineBreak,
                Text("<br/> not a tag"),
                SoftLineBreak,
                Text("[not a link](/foo)"),
                SoftLineBreak,
                Text("`not code`"),
                SoftLineBreak,
                Text("1. not a list"),
                SoftLineBreak,
                Text("* not a list"),
                SoftLineBreak,
                Text("# not a heading"),
                SoftLineBreak,
                Text("[foo]: /url \"not a reference\""),
                SoftLineBreak,
                Text("&ouml; not a character entity"),
            )
        )
    }

    @Test
    fun `should parse spec sample 15 correctly {Backslash escapes}`() {
        val parsed = processor.processMarkdownDocument("\\\\*emphasis*")

        /*
         * Expected HTML:
         * <p>\<em>emphasis</em></p>
         */
        parsed.assertEquals(Paragraph(Text("\\"), Emphasis("*", Text("emphasis"))))
    }

    @Test
    fun `should parse spec sample 16 correctly {Backslash escapes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo\
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo<br />
         * bar</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), HardLineBreak, Text("bar")))
    }

    @Test
    fun `should parse spec sample 17 correctly {Backslash escapes}`() {
        val parsed = processor.processMarkdownDocument("`` \\[\\` ``")

        /*
         * Expected HTML:
         * <p><code>\[\`</code></p>
         */
        parsed.assertEquals(Paragraph(Code("\\[\\`")))
    }

    @Test
    fun `should parse spec sample 18 correctly {Backslash escapes}`() {
        val parsed = processor.processMarkdownDocument("    \\[\\]")

        /*
         * Expected HTML:
         * <pre><code>\[\]
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("\\[\\]"))
    }

    @Test
    fun `should parse spec sample 19 correctly {Backslash escapes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |~~~
                |\[\]
                |~~~
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>\[\]
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("\\[\\]"))
    }

    @Test
    fun `should parse spec sample 20 correctly {Backslash escapes}`() {
        val parsed = processor.processMarkdownDocument("<https://example.com?find=\\*>")

        /*
         * Expected HTML:
         * <p><a href="https://example.com?find=%5C*">https://example.com?find=\*</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link("https://example.com?find=\\*", title = null, Text("https://example.com?find=\\*")))
        )
    }

    @Test
    fun `should parse spec sample 21 correctly {Backslash escapes}`() {
        val parsed = processor.processMarkdownDocument("<a href=\"/bar\\/)\">")

        /*
         * Expected HTML:
         * <a href="/bar\/)">
         */
        parsed.assertEquals(htmlBlock("<a href=\"/bar\\/)\">"))
    }

    @Test
    fun `should parse spec sample 22 correctly {Backslash escapes}`() {
        val parsed = processor.processMarkdownDocument("[foo](/bar\\* \"ti\\*tle\")")

        /*
         * Expected HTML:
         * <p><a href="/bar*" title="ti*tle">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link("/bar*", "ti*tle", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 23 correctly {Backslash escapes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]
                |
                |[foo]: /bar\* "ti\*tle"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/bar*" title="ti*tle">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/bar*", title = "ti*tle", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 24 correctly {Backslash escapes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |``` foo\+bar
                |foo
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code class="language-foo+bar">foo
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("foo", MimeType.Known.fromMarkdownLanguageName("foo\\+bar")))
    }

    @Test
    fun `should parse spec sample 25 correctly {Entity and numeric character references}`() {
        @Suppress("CheckDtdRefs") // Malformed on purpose
        val parsed =
            processor.processMarkdownDocument(
                """
                |&nbsp; &amp; &copy; &AElig; &Dcaron;
                |&frac34; &HilbertSpace; &DifferentialD;
                |&ClockwiseContourIntegral; &ngE;
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>  &amp; © Æ Ď
         * ¾ ℋ ⅆ
         * ∲ ≧̸</p>
         */
        parsed.assertEquals(Paragraph(Text("  & © Æ Ď"), SoftLineBreak, Text("¾ ℋ ⅆ"), SoftLineBreak, Text("∲ ≧̸")))
    }

    @Test
    fun `should parse spec sample 26 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("&#35; &#1234; &#992; &#0;")

        /*
         * Expected HTML:
         * <p># Ӓ Ϡ �</p>
         */
        parsed.assertEquals(paragraph("# Ӓ Ϡ �"))
    }

    @Test
    fun `should parse spec sample 27 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("&#X22; &#XD06; &#xcab;")

        /*
         * Expected HTML:
         * <p>&quot; ആ ಫ</p>
         */
        parsed.assertEquals(paragraph("\" ആ ಫ"))
    }

    @Suppress("CheckDtdRefs")
    @Test
    fun `should parse spec sample 28 correctly {Entity and numeric character references}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |&nbsp &x; &#; &#x;
                |&#87654321;
                |&#abcdef0;
                |&ThisIsNotDefined; &hi?;
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>&amp;nbsp &amp;x; &amp;#; &amp;#x;
         * &amp;#87654321;
         * &amp;#abcdef0;
         * &amp;ThisIsNotDefined; &amp;hi?;</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("&nbsp &x; &#; &#x;"),
                SoftLineBreak,
                Text("&#87654321;"),
                SoftLineBreak,
                Text("&#abcdef0;"),
                SoftLineBreak,
                Text("&ThisIsNotDefined; &hi?;"),
            )
        )
    }

    @Test
    fun `should parse spec sample 29 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("&copy")

        /*
         * Expected HTML:
         * <p>&amp;copy</p>
         */
        parsed.assertEquals(paragraph("&copy"))
    }

    @Suppress("CheckDtdRefs") // Malformed on purpose
    @Test
    fun `should parse spec sample 30 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("&MadeUpEntity;")

        /*
         * Expected HTML:
         * <p>&amp;MadeUpEntity;</p>
         */
        parsed.assertEquals(paragraph("&MadeUpEntity;"))
    }

    @Test
    fun `should parse spec sample 31 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("<a href=\"&ouml;&ouml;.html\">")

        /*
         * Expected HTML:
         * <a href="&ouml;&ouml;.html">
         */
        parsed.assertEquals(htmlBlock("<a href=\"&ouml;&ouml;.html\">"))
    }

    @Test
    fun `should parse spec sample 32 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("[foo](/f&ouml;&ouml; \"f&ouml;&ouml;\")")

        /*
         * Expected HTML:
         * <p><a href="/f%C3%B6%C3%B6" title="föö">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/föö", title = "föö", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 33 correctly {Entity and numeric character references}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]
                |
                |[foo]: /f&ouml;&ouml; "f&ouml;&ouml;"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/f%C3%B6%C3%B6" title="föö">foo</a></p>
         */

        parsed.assertEquals(Paragraph(Link(destination = "/föö", title = "föö", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 34 correctly {Entity and numeric character references}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |``` f&ouml;&ouml;
                |foo
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code class="language-föö">foo
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("foo", mimeType = MimeType.Known.fromMarkdownLanguageName("föö")))
    }

    @Test
    fun `should parse spec sample 35 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("`f&ouml;&ouml;`")

        /*
         * Expected HTML:
         * <p><code>f&amp;ouml;&amp;ouml;</code></p>
         */
        parsed.assertEquals(Paragraph(Code("f&ouml;&ouml;")))
    }

    @Test
    fun `should parse spec sample 36 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("    f&ouml;f&ouml;")

        /*
         * Expected HTML:
         * <pre><code>f&amp;ouml;f&amp;ouml;
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("f&ouml;f&ouml;"))
    }

    @Test
    fun `should parse spec sample 37 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("&#42;foo&#42;\n*foo*")

        /*
         * Expected HTML:
         * <p>*foo*
         * <em>foo</em></p>
         */
        parsed.assertEquals(Paragraph(Text("*foo*"), SoftLineBreak, Emphasis("*", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 38 correctly {Entity and numeric character references}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |&#42; foo
                |
                |* foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>* foo</p>
         * <ul>
         * <li>foo</li>
         * </ul>
         */
        parsed.assertEquals(paragraph("* foo"), unorderedList(listItem(paragraph("foo")), marker = "*"))
    }

    @Test
    fun `should parse spec sample 39 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("foo&#10;&#10;bar")

        /*
         * Expected HTML:
         * <p>foo
         *
         * bar</p>
         */
        parsed.assertEquals(paragraph("foo\n\nbar"))
    }

    @Test
    fun `should parse spec sample 40 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("&#9;foo")

        /*
         * Expected HTML:
         * <p>\tfoo</p>
         */
        parsed.assertEquals(paragraph("\tfoo"))
    }

    @Test
    fun `should parse spec sample 41 correctly {Entity and numeric character references}`() {
        val parsed = processor.processMarkdownDocument("[a](url &quot;tit&quot;)")

        /*
         * Expected HTML:
         * <p>[a](url &quot;tit&quot;)</p>
         */
        parsed.assertEquals(paragraph("[a](url \"tit\")"))
    }

    @Test
    fun `should parse spec sample 42 correctly {Precedence}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- `one
                |- two`
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>`one</li>
         * <li>two`</li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("`one")), listItem(paragraph("two`"))))
    }

    @Test
    fun `should parse spec sample 43 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |***
                |---
                |___
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <hr />
         * <hr />
         * <hr />
         */
        parsed.assertEquals(thematicBreak(), thematicBreak(), thematicBreak())
    }

    @Test
    fun `should parse spec sample 44 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument("+++")

        /*
         * Expected HTML:
         * <p>+++</p>
         */
        parsed.assertEquals(paragraph("+++"))
    }

    @Test
    fun `should parse spec sample 45 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument("===")

        /*
         * Expected HTML:
         * <p>===</p>
         */
        parsed.assertEquals(paragraph("==="))
    }

    @Test
    fun `should parse spec sample 46 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |--
                |**
                |__
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>--
         * **
         * __</p>
         */
        parsed.assertEquals(Paragraph(Text("--"), SoftLineBreak, Text("**"), SoftLineBreak, Text("__")))
    }

    @Test
    fun `should parse spec sample 47 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                | ***
                |  ***
                |   ***
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <hr />
         * <hr />
         * <hr />
         */
        parsed.assertEquals(thematicBreak(), thematicBreak(), thematicBreak())
    }

    @Test
    fun `should parse spec sample 48 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument("    ***")

        /*
         * Expected HTML:
         * <pre><code>***
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("***"))
    }

    @Test
    fun `should parse spec sample 49 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |    ***
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * ***</p>
         */
        parsed.assertEquals(Paragraph(Text("Foo"), SoftLineBreak, Text("***")))
    }

    @Test
    fun `should parse spec sample 50 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument("_____________________________________")

        /*
         * Expected HTML:
         * <hr />
         */
        parsed.assertEquals(thematicBreak())
    }

    @Test
    fun `should parse spec sample 51 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument(" - - -")

        /*
         * Expected HTML:
         * <hr />
         */
        parsed.assertEquals(thematicBreak())
    }

    @Test
    fun `should parse spec sample 52 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument(" **  * ** * ** * **")

        /*
         * Expected HTML:
         * <hr />
         */
        parsed.assertEquals(thematicBreak())
    }

    @Test
    fun `should parse spec sample 53 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument("-     -      -      -")

        /*
         * Expected HTML:
         * <hr />
         */
        parsed.assertEquals(thematicBreak())
    }

    @Test
    fun `should parse spec sample 54 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument("- - - -    ")

        /*
         * Expected HTML:
         * <hr />
         */
        parsed.assertEquals(thematicBreak())
    }

    @Test
    fun `should parse spec sample 55 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |_ _ _ _ a
                |
                |a------
                |
                |---a---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>_ _ _ _ a</p>
         * <p>a------</p>
         * <p>---a---</p>
         */
        parsed.assertEquals(paragraph("_ _ _ _ a"), paragraph("a------"), paragraph("---a---"))
    }

    @Test
    fun `should parse spec sample 56 correctly {Thematic breaks}`() {
        val parsed = processor.processMarkdownDocument(" *-*")

        /*
         * Expected HTML:
         * <p><em>-</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("-"))))
    }

    @Test
    fun `should parse spec sample 57 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |***
                |- bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * </ul>
         * <hr />
         * <ul>
         * <li>bar</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("foo"))),
            thematicBreak(),
            unorderedList(listItem(paragraph("bar"))),
        )
    }

    @Test
    fun `should parse spec sample 58 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |***
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo</p>
         * <hr />
         * <p>bar</p>
         */
        parsed.assertEquals(paragraph("Foo"), thematicBreak(), paragraph("bar"))
    }

    @Test
    fun `should parse spec sample 59 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |---
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>Foo</h2>
         * <p>bar</p>
         */
        parsed.assertEquals(heading(2, Text("Foo")), paragraph("bar"))
    }

    @Test
    fun `should parse spec sample 60 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |* Foo
                |* * *
                |* Bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>Foo</li>
         * </ul>
         * <hr />
         * <ul>
         * <li>Bar</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("Foo")), marker = "*"),
            thematicBreak(),
            unorderedList(listItem(paragraph("Bar")), marker = "*"),
        )
    }

    @Test
    fun `should parse spec sample 61 correctly {Thematic breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- Foo
                |- * * *
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>Foo</li>
         * <li>
         * <hr />
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("Foo")), listItem(thematicBreak())))
    }

    @Test
    fun `should parse spec sample 62 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |# foo
                |## foo
                |### foo
                |#### foo
                |##### foo
                |###### foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h1>foo</h1>
         * <h2>foo</h2>
         * <h3>foo</h3>
         * <h4>foo</h4>
         * <h5>foo</h5>
         * <h6>foo</h6>
         */
        parsed.assertEquals(
            heading(1, Text("foo")),
            heading(2, Text("foo")),
            heading(3, Text("foo")),
            heading(4, Text("foo")),
            heading(5, Text("foo")),
            heading(6, Text("foo")),
        )
    }

    @Test
    fun `should parse spec sample 63 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("####### foo")

        /*
         * Expected HTML:
         * <p>####### foo</p>
         */
        parsed.assertEquals(paragraph("####### foo"))
    }

    @Test
    fun `should parse spec sample 64 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |#5 bolt
                |
                |#hashtag
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>#5 bolt</p>
         * <p>#hashtag</p>
         */
        parsed.assertEquals(paragraph("#5 bolt"), paragraph("#hashtag"))
    }

    @Test
    fun `should parse spec sample 65 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("\\## foo")

        /*
         * Expected HTML:
         * <p>## foo</p>
         */
        parsed.assertEquals(paragraph("## foo"))
    }

    @Test
    fun `should parse spec sample 66 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("# foo *bar* \\*baz\\*")

        /*
         * Expected HTML:
         * <h1>foo <em>bar</em> *baz*</h1>
         */
        parsed.assertEquals(heading(level = 1, Text("foo "), Emphasis("*", Text("bar")), Text(" *baz*")))
    }

    @Test
    fun `should parse spec sample 67 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("#                  foo                     ")

        /*
         * Expected HTML:
         * <h1>foo</h1>
         */
        parsed.assertEquals(heading(level = 1, Text("foo")))
    }

    @Test
    fun `should parse spec sample 68 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                | ### foo
                |  ## foo
                |   # foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h3>foo</h3>
         * <h2>foo</h2>
         * <h1>foo</h1>
         */
        parsed.assertEquals(
            heading(level = 3, Text("foo")),
            heading(level = 2, Text("foo")),
            heading(level = 1, Text("foo")),
        )
    }

    @Test
    fun `should parse spec sample 69 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("    # foo")

        /*
         * Expected HTML:
         * <pre><code># foo
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("# foo"))
    }

    @Test
    fun `should parse spec sample 70 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo
                |    # bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo
         * # bar</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), SoftLineBreak, Text("# bar")))
    }

    @Test
    fun `should parse spec sample 71 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |## foo ##
                |  ###   bar    ###
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>foo</h2>
         * <h3>bar</h3>
         */
        parsed.assertEquals(heading(level = 2, Text("foo")), heading(level = 3, Text("bar")))
    }

    @Test
    fun `should parse spec sample 72 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |# foo ##################################
                |##### foo ##
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h1>foo</h1>
         * <h5>foo</h5>
         */
        parsed.assertEquals(heading(level = 1, Text("foo")), heading(level = 5, Text("foo")))
    }

    @Test
    fun `should parse spec sample 73 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("### foo ###     ")

        /*
         * Expected HTML:
         * <h3>foo</h3>
         */
        parsed.assertEquals(heading(level = 3, Text("foo")))
    }

    @Test
    fun `should parse spec sample 74 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("### foo ### b")

        /*
         * Expected HTML:
         * <h3>foo ### b</h3>
         */
        parsed.assertEquals(heading(level = 3, Text("foo ### b")))
    }

    @Test
    fun `should parse spec sample 75 correctly {ATX headings}`() {
        val parsed = processor.processMarkdownDocument("# foo#")

        /*
         * Expected HTML:
         * <h1>foo#</h1>
         */
        parsed.assertEquals(heading(level = 1, Text("foo#")))
    }

    @Test
    fun `should parse spec sample 76 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |### foo \###
                |## foo #\##
                |# foo \#
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h3>foo ###</h3>
         * <h2>foo ###</h2>
         * <h1>foo #</h1>
         */
        parsed.assertEquals(
            heading(level = 3, Text("foo ###")),
            heading(level = 2, Text("foo ###")),
            heading(level = 1, Text("foo #")),
        )
    }

    @Test
    fun `should parse spec sample 77 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |****
                |## foo
                |****
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <hr />
         * <h2>foo</h2>
         * <hr />
         */
        parsed.assertEquals(thematicBreak(), heading(level = 2, Text("foo")), thematicBreak())
    }

    @Test
    fun `should parse spec sample 78 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo bar
                |# baz
                |Bar foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo bar</p>
         * <h1>baz</h1>
         * <p>Bar foo</p>
         */
        parsed.assertEquals(paragraph("Foo bar"), heading(level = 1, Text("baz")), paragraph("Bar foo"))
    }

    @Test
    fun `should parse spec sample 79 correctly {ATX headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |## 
                |#
                |### ###
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2></h2>
         * <h1></h1>
         * <h3></h3>
         */
        parsed.assertEquals(
            Heading(emptyList(), level = 2),
            Heading(emptyList(), level = 1),
            Heading(emptyList(), level = 3),
        )
    }

    @Test
    fun `should parse spec sample 80 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo *bar*
                |=========
                |
                |Foo *bar*
                |---------
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h1>Foo <em>bar</em></h1>
         * <h2>Foo <em>bar</em></h2>
         */

        parsed.assertEquals(
            heading(level = 1, Text("Foo "), Emphasis("*", Text("bar"))),
            heading(level = 2, Text("Foo "), Emphasis("*", Text("bar"))),
        )
    }

    @Test
    fun `should parse spec sample 81 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo *bar
                |baz*
                |====
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h1>Foo <em>bar
         * baz</em></h1>
         */
        parsed.assertEquals(heading(level = 1, Text("Foo "), Emphasis("*", Text("bar"), SoftLineBreak, Text("baz"))))
    }

    @Test
    fun `should parse spec sample 82 correctly {Setext headings}`() {
        val parsed = processor.processMarkdownDocument("  Foo *bar\nbaz*\t\n====")

        /*
         * Expected HTML:
         * <h1>Foo <em>bar
         * baz</em></h1>
         */
        parsed.assertEquals(
            heading(level = 1, Text("Foo "), Emphasis("*", Text("bar"), SoftLineBreak, Text("baz")), Text(""))
        )
    }

    @Test
    fun `should parse spec sample 83 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |-------------------------
                |
                |Foo
                |=
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>Foo</h2>
         * <h1>Foo</h1>
         */
        parsed.assertEquals(heading(level = 2, Text("Foo")), heading(level = 1, Text("Foo")))
    }

    @Test
    fun `should parse spec sample 84 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   Foo
                |---
                |
                |  Foo
                |-----
                |
                |  Foo
                |  ===
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>Foo</h2>
         * <h2>Foo</h2>
         * <h1>Foo</h1>
         */
        parsed.assertEquals(
            heading(level = 2, Text("Foo")),
            heading(level = 2, Text("Foo")),
            heading(level = 1, Text("Foo")),
        )
    }

    @Test
    fun `should parse spec sample 85 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    Foo
                |    ---
                |
                |    Foo
                |---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>Foo
         * ---
         *
         * Foo
         * </code></pre>
         * <hr />
         */
        parsed.assertEquals(indentedCodeBlock("Foo\n---\n\nFoo"), thematicBreak())
    }

    @Test
    fun `should parse spec sample 86 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |   ----      
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>Foo</h2>
         */
        parsed.assertEquals(heading(level = 2, Text("Foo")))
    }

    @Test
    fun `should parse spec sample 87 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |    ---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * ---</p>
         */
        parsed.assertEquals(Paragraph(Text("Foo"), SoftLineBreak, Text("---")))
    }

    @Test
    fun `should parse spec sample 88 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |= =
                |
                |Foo
                |--- -
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * = =</p>
         * <p>Foo</p>
         * <hr />
         */
        parsed.assertEquals(Paragraph(Text("Foo"), SoftLineBreak, Text("= =")), paragraph("Foo"), thematicBreak())
    }

    @Test
    fun `should parse spec sample 89 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo  
                |-----
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>Foo</h2>
         */
        parsed.assertEquals(heading(level = 2, Text("Foo")))
    }

    @Test
    fun `should parse spec sample 90 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo\
                |----
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>Foo\</h2>
         */
        parsed.assertEquals(heading(level = 2, Text("Foo\\")))
    }

    @Test
    fun `should parse spec sample 91 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |`Foo
                |----
                |`
                |
                |<a title="a lot
                |---
                |of dashes"/>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>`Foo</h2>
         * <p>`</p>
         * <h2>&lt;a title=&quot;a lot</h2>
         * <p>of dashes&quot;/&gt;</p>
         */
        parsed.assertEquals(
            heading(level = 2, Text("`Foo")),
            paragraph("`"),
            heading(level = 2, Text("<a title=\"a lot")),
            paragraph("of dashes\"/>"),
        )
    }

    @Test
    fun `should parse spec sample 92 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> Foo
                |---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>Foo</p>
         * </blockquote>
         * <hr />
         */
        parsed.assertEquals(blockQuote(paragraph("Foo")), thematicBreak())
    }

    @Test
    fun `should parse spec sample 93 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> foo
                |bar
                |===
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo
         * bar
         * ===</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(Paragraph(Text("foo"), SoftLineBreak, Text("bar"), SoftLineBreak, Text("==="))))
    }

    @Test
    fun `should parse spec sample 94 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- Foo
                |---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>Foo</li>
         * </ul>
         * <hr />
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("Foo"))), thematicBreak())
    }

    @Test
    fun `should parse spec sample 95 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |Bar
                |---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>Foo
         * Bar</h2>
         */
        parsed.assertEquals(heading(level = 2, Text("Foo"), SoftLineBreak, Text("Bar")))
    }

    @Test
    fun `should parse spec sample 96 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |---
                |Foo
                |---
                |Bar
                |---
                |Baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <hr />
         * <h2>Foo</h2>
         * <h2>Bar</h2>
         * <p>Baz</p>
         */
        parsed.assertEquals(
            thematicBreak(),
            heading(level = 2, Text("Foo")),
            heading(level = 2, Text("Bar")),
            paragraph("Baz"),
        )
    }

    @Test
    fun `should parse spec sample 97 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |
                |====
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>====</p>
         */
        parsed.assertEquals(paragraph("===="))
    }

    @Test
    fun `should parse spec sample 98 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |---
                |---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <hr />
         * <hr />
         */
        parsed.assertEquals(thematicBreak(), thematicBreak())
    }

    @Test
    fun `should parse spec sample 99 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |-----
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * </ul>
         * <hr />
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"))), thematicBreak())
    }

    @Test
    fun `should parse spec sample 100 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    foo
                |---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>foo
         * </code></pre>
         * <hr />
         */
        parsed.assertEquals(indentedCodeBlock("foo"), thematicBreak())
    }

    @Test
    fun `should parse spec sample 101 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> foo
                |-----
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo</p>
         * </blockquote>
         * <hr />
         */
        parsed.assertEquals(blockQuote(paragraph("foo")), thematicBreak())
    }

    @Test
    fun `should parse spec sample 102 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |\> foo
                |------
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>&gt; foo</h2>
         */
        parsed.assertEquals(heading(level = 2, Text("> foo")))
    }

    @Test
    fun `should parse spec sample 103 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |
                |bar
                |---
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo</p>
         * <h2>bar</h2>
         * <p>baz</p>
         */
        parsed.assertEquals(paragraph("Foo"), heading(level = 2, Text("bar")), paragraph("baz"))
    }

    @Test
    fun `should parse spec sample 104 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |bar
                |
                |---
                |
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * bar</p>
         * <hr />
         * <p>baz</p>
         */
        parsed.assertEquals(Paragraph(Text("Foo"), SoftLineBreak, Text("bar")), thematicBreak(), paragraph("baz"))
    }

    @Test
    fun `should parse spec sample 105 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |bar
                |* * *
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * bar</p>
         * <hr />
         * <p>baz</p>
         */
        parsed.assertEquals(Paragraph(Text("Foo"), SoftLineBreak, Text("bar")), thematicBreak(), paragraph("baz"))
    }

    @Test
    fun `should parse spec sample 106 correctly {Setext headings}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |bar
                |\---
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * bar
         * ---
         * baz</p>
         */
        parsed.assertEquals(
            Paragraph(Text("Foo"), SoftLineBreak, Text("bar"), SoftLineBreak, Text("---"), SoftLineBreak, Text("baz"))
        )
    }

    @Test
    fun `should parse spec sample 107 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    a simple
                |      indented code block
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>a simple
         *   indented code block
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("a simple\n  indented code block"))
    }

    @Test
    fun `should parse spec sample 108 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  - foo
                |
                |    bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <p>bar</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"), paragraph("bar")), isTight = false))
    }

    @Test
    fun `should parse spec sample 109 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1.  foo
                |
                |    - bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>foo</p>
         * <ul>
         * <li>bar</li>
         * </ul>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(listItem(paragraph("foo"), unorderedList(listItem(paragraph("bar")))), isTight = false)
        )
    }

    @Test
    fun `should parse spec sample 110 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    <a/>
                |    *hi*
                |
                |    - one
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>&lt;a/&gt;
         * *hi*
         *
         * - one
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("<a/>\n*hi*\n\n- one"))
    }

    @Test
    fun `should parse spec sample 111 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    chunk1
                |
                |    chunk2
                |  
                | 
                | 
                |    chunk3
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>chunk1
         *
         * chunk2
         *
         *
         *
         * chunk3
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("chunk1\n\nchunk2\n\n\n\nchunk3"))
    }

    @Test
    fun `should parse spec sample 112 correctly {Indented code blocks}`() {
        val parsed = processor.processMarkdownDocument("    chunk1\n      \n      chunk2")

        /*
         * Expected HTML:
         * <pre><code>chunk1
         * ••
         *   chunk2
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("chunk1\n  \n  chunk2"))
    }

    @Test
    fun `should parse spec sample 113 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |    bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * bar</p>
         */
        parsed.assertEquals(Paragraph(Text("Foo"), SoftLineBreak, Text("bar")))
    }

    @Test
    fun `should parse spec sample 114 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    foo
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>foo
         * </code></pre>
         * <p>bar</p>
         */
        parsed.assertEquals(indentedCodeBlock("foo"), paragraph("bar"))
    }

    @Test
    fun `should parse spec sample 115 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |# Heading
                |    foo
                |Heading
                |------
                |    foo
                |----
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h1>Heading</h1>
         * <pre><code>foo
         * </code></pre>
         * <h2>Heading</h2>
         * <pre><code>foo
         * </code></pre>
         * <hr />
         */
        parsed.assertEquals(
            heading(level = 1, Text("Heading")),
            indentedCodeBlock("foo"),
            heading(level = 2, Text("Heading")),
            indentedCodeBlock("foo"),
            thematicBreak(),
        )
    }

    @Test
    fun `should parse spec sample 116 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |        foo
                |    bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>    foo
         * bar
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("    foo\nbar"))
    }

    @Test
    fun `should parse spec sample 117 correctly {Indented code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |
                |    
                |    foo
                |    
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>foo
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("foo"))
    }

    @Test
    fun `should parse spec sample 118 correctly {Indented code blocks}`() {
        val parsed = processor.processMarkdownDocument("    foo  ")

        /*
         * Expected HTML:
         * <pre><code>foo••
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("foo  "))
    }

    @Test
    fun `should parse spec sample 119 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |<
                | >
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>&lt;
         *  &gt;
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("<\n >"))
    }

    @Test
    fun `should parse spec sample 120 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |~~~
                |<
                | >
                |~~~
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>&lt;
         *  &gt;
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("<\n >"))
    }

    @Test
    fun `should parse spec sample 121 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |``
                |foo
                |``
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code>foo</code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo")))
    }

    @Test
    fun `should parse spec sample 122 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |aaa
                |~~~
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * ~~~
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\n~~~"))
    }

    @Test
    fun `should parse spec sample 123 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |~~~
                |aaa
                |```
                |~~~
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * ```
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\n```"))
    }

    @Test
    fun `should parse spec sample 124 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |````
                |aaa
                |```
                |``````
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * ```
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\n```"))
    }

    @Test
    fun `should parse spec sample 125 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |~~~~
                |aaa
                |~~~
                |~~~~
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * ~~~
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\n~~~"))
    }

    @Test
    fun `should parse spec sample 126 correctly {Fenced code blocks}`() {
        val parsed = processor.processMarkdownDocument("```")

        /*
         * Expected HTML:
         * <pre><code></code></pre>
         */
        parsed.assertEquals(fencedCodeBlock(""))
    }

    @Test
    fun `should parse spec sample 127 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |`````
                |
                |```
                |aaa
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>
         * ```
         * aaa
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("\n```\naaa"))
    }

    @Test
    fun `should parse spec sample 128 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> ```
                |> aaa
                |
                |bbb
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <pre><code>aaa
         * </code></pre>
         * </blockquote>
         * <p>bbb</p>
         */
        parsed.assertEquals(blockQuote(fencedCodeBlock("aaa")), paragraph("bbb"))
    }

    @Test
    fun `should parse spec sample 129 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |
                |  
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>
         * ••
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("\n  "))
    }

    @Test
    fun `should parse spec sample 130 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code></code></pre>
         */
        parsed.assertEquals(fencedCodeBlock(""))
    }

    @Test
    fun `should parse spec sample 131 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                | ```
                | aaa
                |aaa
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * aaa
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\naaa"))
    }

    @Test
    fun `should parse spec sample 132 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  ```
                |aaa
                |  aaa
                |aaa
                |  ```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * aaa
         * aaa
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\naaa\naaa"))
    }

    @Test
    fun `should parse spec sample 133 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   ```
                |   aaa
                |    aaa
                |  aaa
                |   ```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         *  aaa
         * aaa
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\n aaa\naaa"))
    }

    @Test
    fun `should parse spec sample 134 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    ```
                |    aaa
                |    ```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>```
         * aaa
         * ```
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("```\naaa\n```"))
    }

    @Test
    fun `should parse spec sample 135 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |aaa
                |  ```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa"))
    }

    @Test
    fun `should parse spec sample 136 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   ```
                |aaa
                |  ```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa"))
    }

    @Test
    fun `should parse spec sample 137 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |aaa
                |    ```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         *     ```
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\n    ```"))
    }

    @Test
    fun `should parse spec sample 138 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |``` ```
                |aaa
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code> </code>
         * aaa</p>
         */
        parsed.assertEquals(Paragraph(Code(" "), SoftLineBreak, Text("aaa")))
    }

    @Test
    fun `should parse spec sample 139 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |~~~~~~
                |aaa
                |~~~ ~~
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * ~~~ ~~
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("aaa\n~~~ ~~"))
    }

    @Test
    fun `should parse spec sample 140 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo
                |```
                |bar
                |```
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo</p>
         * <pre><code>bar
         * </code></pre>
         * <p>baz</p>
         */
        parsed.assertEquals(paragraph("foo"), fencedCodeBlock("bar"), paragraph("baz"))
    }

    @Test
    fun `should parse spec sample 141 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo
                |---
                |~~~
                |bar
                |~~~
                |# baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h2>foo</h2>
         * <pre><code>bar
         * </code></pre>
         * <h1>baz</h1>
         */
        parsed.assertEquals(heading(level = 2, Text("foo")), fencedCodeBlock("bar"), heading(level = 1, Text("baz")))
    }

    @Test
    fun `should parse spec sample 142 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```ruby
                |def foo(x)
                |  return 3
                |end
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code class="language-ruby">def foo(x)
         *   return 3
         * end
         * </code></pre>
         */
        parsed.assertEquals(
            fencedCodeBlock("def foo(x)\n  return 3\nend", mimeType = MimeType.Known.fromMarkdownLanguageName("ruby"))
        )
    }

    @Test
    fun `should parse spec sample 143 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |~~~~    ruby startline=3 $%@#$
                |def foo(x)
                |  return 3
                |end
                |~~~~~~~
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code class="language-ruby">def foo(x)
         *   return 3
         * end
         * </code></pre>
         */
        parsed.assertEquals(
            fencedCodeBlock("def foo(x)\n  return 3\nend", mimeType = MimeType.Known.fromMarkdownLanguageName("ruby"))
        )
    }

    @Test
    fun `should parse spec sample 144 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |````;
                |````
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code class="language-;"></code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("", mimeType = MimeType.Known.fromMarkdownLanguageName(";")))
    }

    @Test
    fun `should parse spec sample 145 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |``` aa ```
                |foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code>aa</code>
         * foo</p>
         */
        parsed.assertEquals(Paragraph(Code("aa"), SoftLineBreak, Text("foo")))
    }

    @Test
    fun `should parse spec sample 146 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |~~~ aa ``` ~~~
                |foo
                |~~~
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code class="language-aa">foo
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("foo", mimeType = MimeType.Known.fromMarkdownLanguageName("aa")))
    }

    @Test
    fun `should parse spec sample 147 correctly {Fenced code blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |``` aaa
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>``` aaa
         * </code></pre>
         */
        parsed.assertEquals(fencedCodeBlock("``` aaa"))
    }

    @Test
    fun `should parse spec sample 148 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<table><tr><td>
                |<pre>
                |**Hello**,
                |
                |_world_.
                |</pre>
                |</td></tr></table>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <table><tr><td>
         * <pre>
         * **Hello**,
         * <p><em>world</em>.
         * </pre></p>
         * </td></tr></table>
         */
        parsed.assertEquals(
            htmlBlock("<table><tr><td>\n<pre>\n**Hello**,"),
            Paragraph(Emphasis("_", Text("world")), Text("."), SoftLineBreak, HtmlInline("</pre>")),
            htmlBlock("</td></tr></table>"),
        )
    }

    @Test
    fun `should parse spec sample 149 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<table>
                |  <tr>
                |    <td>
                |           hi
                |    </td>
                |  </tr>
                |</table>
                |
                |okay.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <table>
         *   <tr>
         *     <td>
         *            hi
         *     </td>
         *   </tr>
         * </table>
         * <p>okay.</p>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<table>
                |  <tr>
                |    <td>
                |           hi
                |    </td>
                |  </tr>
                |</table>
                """
                    .trimMargin()
            ),
            paragraph("okay."),
        )
    }

    @Test
    fun `should parse spec sample 150 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                | <div>
                |  *hello*
                |         <foo><a>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         *  <div>
         *   *hello*
         *          <foo><a>
         */
        parsed.assertEquals(htmlBlock(" <div>\n  *hello*\n         <foo><a>"))
    }

    @Test
    fun `should parse spec sample 151 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |</div>
                |*foo*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * </div>
         * *foo*
         */
        parsed.assertEquals(htmlBlock("</div>\n*foo*"))
    }

    @Test
    fun `should parse spec sample 152 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<DIV CLASS="foo">
                |
                |*Markdown*
                |
                |</DIV>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <DIV CLASS="foo">
         * <p><em>Markdown</em></p>
         * </DIV>
         */
        parsed.assertEquals(
            htmlBlock("<DIV CLASS=\"foo\">"),
            Paragraph(Emphasis("*", Text("Markdown"))),
            htmlBlock("</DIV>"),
        )
    }

    @Test
    fun `should parse spec sample 153 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div id="foo"
                |  class="bar">
                |</div>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div id="foo"
         *   class="bar">
         * </div>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<div id="foo"
                |  class="bar">
                |</div>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 154 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div id="foo" class="bar
                |  baz">
                |</div>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div id="foo" class="bar
         *   baz">
         * </div>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<div id="foo" class="bar
                |  baz">
                |</div>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 155 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div>
                |*foo*
                |
                |*bar*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div>
         * *foo*
         * <p><em>bar</em></p>
         */
        parsed.assertEquals(htmlBlock("<div>\n*foo*"), Paragraph(Emphasis("*", Text("bar"))))
    }

    @Test
    fun `should parse spec sample 156 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div id="foo"
                |*hi*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div id="foo"
         * *hi*
         */
        parsed.assertEquals(htmlBlock("<div id=\"foo\"\n*hi*"))
    }

    @Test
    fun `should parse spec sample 157 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div class
                |foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div class
         * foo
         */
        parsed.assertEquals(htmlBlock("<div class\nfoo"))
    }

    @Test
    fun `should parse spec sample 158 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div *???-&&&-<---
                |*foo*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div *???-&&&-<---
         * *foo*
         */
        parsed.assertEquals(htmlBlock("<div *???-&&&-<---\n*foo*"))
    }

    @Test
    fun `should parse spec sample 159 correctly {HTML blocks}`() {
        val parsed = processor.processMarkdownDocument("<div><a href=\"bar\">*foo*</a></div>")

        /*
         * Expected HTML:
         * <div><a href="bar">*foo*</a></div>
         */
        parsed.assertEquals(htmlBlock("<div><a href=\"bar\">*foo*</a></div>"))
    }

    @Test
    fun `should parse spec sample 160 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<table><tr><td>
                |foo
                |</td></tr></table>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <table><tr><td>
         * foo
         * </td></tr></table>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<table><tr><td>
                |foo
                |</td></tr></table>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 161 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div></div>
                |``` c
                |int x = 33;
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div></div>
         * ``` c
         * int x = 33;
         * ```
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<div></div>
                |``` c
                |int x = 33;
                |```
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 162 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<a href="foo">
                |*bar*
                |</a>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <a href="foo">
         * *bar*
         * </a>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<a href="foo">
                |*bar*
                |</a>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 163 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<Warning>
                |*bar*
                |</Warning>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <Warning>
         * *bar*
         * </Warning>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<Warning>
                |*bar*
                |</Warning>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 164 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<i class="foo">
                |*bar*
                |</i>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <i class="foo">
         * *bar*
         * </i>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<i class="foo">
                |*bar*
                |</i>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 165 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |</ins>
                |*bar*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * </ins>
         * *bar*
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |</ins>
                |*bar*
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 166 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<del>
                |*foo*
                |</del>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <del>
         * *foo*
         * </del>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<del>
                |*foo*
                |</del>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 167 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<del>
                |
                |*foo*
                |
                |</del>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <del>
         * <p><em>foo</em></p>
         * </del>
         */
        parsed.assertEquals(htmlBlock("<del>"), Paragraph(Emphasis("*", Text("foo"))), htmlBlock("</del>"))
    }

    @Test
    fun `should parse spec sample 168 correctly {HTML blocks}`() {
        val parsed = processor.processMarkdownDocument("<del>*foo*</del>")

        /*
         * Expected HTML:
         * <p><del><em>foo</em></del></p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("<del>"), Emphasis("*", Text("foo")), HtmlInline("</del>")))
    }

    @Test
    fun `should parse spec sample 169 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<pre language="haskell"><code>
                |import Text.HTML.TagSoup
                |
                |main :: IO ()
                |main = print $ parseTags tags
                |</code></pre>
                |okay
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre language="haskell"><code>
         * import Text.HTML.TagSoup
         *
         * main :: IO ()
         * main = print $ parseTags tags
         * </code></pre>
         * <p>okay</p>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<pre language="haskell"><code>
                |import Text.HTML.TagSoup
                |
                |main :: IO ()
                |main = print $ parseTags tags
                |</code></pre>
                """
                    .trimMargin()
            ),
            paragraph("okay"),
        )
    }

    @Test
    fun `should parse spec sample 170 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<script type="text/javascript">
                |// JavaScript example
                |
                |document.getElementById("demo").innerHTML = "Hello JavaScript!";
                |</script>
                |okay
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <script type="text/javascript">
         * // JavaScript example
         *
         * document.getElementById("demo").innerHTML = "Hello JavaScript!";
         * </script>
         * <p>okay</p>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<script type="text/javascript">
                |// JavaScript example
                |
                |document.getElementById("demo").innerHTML = "Hello JavaScript!";
                |</script>
                """
                    .trimMargin()
            ),
            paragraph("okay"),
        )
    }

    @Test
    fun `should parse spec sample 171 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<textarea>
                |
                |*foo*
                |
                |_bar_
                |
                |</textarea>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <textarea>
         *
         * *foo*
         *
         * _bar_
         *
         * </textarea>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<textarea>
                |
                |*foo*
                |
                |_bar_
                |
                |</textarea>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 172 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<style
                |  type="text/css">
                |h1 {color:red;}
                |
                |p {color:blue;}
                |</style>
                |okay
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <style
         *   type="text/css">
         * h1 {color:red;}
         *
         * p {color:blue;}
         * </style>
         * <p>okay</p>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<style
                |  type="text/css">
                |h1 {color:red;}
                |
                |p {color:blue;}
                |</style>
                """
                    .trimMargin()
            ),
            paragraph("okay"),
        )
    }

    @Test
    fun `should parse spec sample 173 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<style
                |  type="text/css">
                |
                |foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <style
         *   type="text/css">
         *
         * foo
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<style
                |  type="text/css">
                |
                |foo
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 174 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> <div>
                |> foo
                |
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <div>
         * foo
         * </blockquote>
         * <p>bar</p>
         */
        parsed.assertEquals(blockQuote(htmlBlock("<div>\nfoo")), paragraph("bar"))
    }

    @Test
    fun `should parse spec sample 175 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- <div>
                |- foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <div>
         * </li>
         * <li>foo</li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(htmlBlock("<div>")), listItem(paragraph("foo"))))
    }

    @Test
    fun `should parse spec sample 176 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<style>p{color:red;}</style>
                |*foo*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <style>p{color:red;}</style>
         * <p><em>foo</em></p>
         */
        parsed.assertEquals(htmlBlock("<style>p{color:red;}</style>"), Paragraph(Emphasis("*", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 177 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<!-- foo -->*bar*
                |*baz*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <!-- foo -->*bar*
         * <p><em>baz</em></p>
         */
        parsed.assertEquals(htmlBlock("<!-- foo -->*bar*"), Paragraph(Emphasis("*", Text("baz"))))
    }

    @Test
    fun `should parse spec sample 178 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<script>
                |foo
                |</script>1. *bar*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <script>
         * foo
         * </script>1. *bar*
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<script>
                |foo
                |</script>1. *bar*
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 179 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<!-- Foo
                |
                |bar
                |   baz -->
                |okay
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <!-- Foo
         *
         * bar
         *    baz -->
         * <p>okay</p>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<!-- Foo
                |
                |bar
                |   baz -->
                """
                    .trimMargin()
            ),
            paragraph("okay"),
        )
    }

    @Test
    fun `should parse spec sample 180 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<?php
                |
                |  echo '>';
                |
                |?>
                |okay
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <?php
         *
         *   echo '>';
         *
         * ?>
         * <p>okay</p>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<?php
                |
                |  echo '>';
                |
                |?>
                """
                    .trimMargin()
            ),
            paragraph("okay"),
        )
    }

    @Test
    fun `should parse spec sample 181 correctly {HTML blocks}`() {
        val parsed = processor.processMarkdownDocument("<!DOCTYPE html>")

        /*
         * Expected HTML:
         * <!DOCTYPE html>
         */
        parsed.assertEquals(htmlBlock("<!DOCTYPE html>"))
    }

    @Test
    fun `should parse spec sample 182 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<![CDATA[
                |function matchwo(a,b)
                |{
                |  if (a < b && a < 0) then {
                |    return 1;
                |
                |  } else {
                |
                |    return 0;
                |  }
                |}
                |]]>
                |okay
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <![CDATA[
         * function matchwo(a,b)
         * {
         *   if (a < b && a < 0) then {
         *     return 1;
         *
         *   } else {
         *
         *     return 0;
         *   }
         * }
         * ]]>
         * <p>okay</p>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<![CDATA[
                |function matchwo(a,b)
                |{
                |  if (a < b && a < 0) then {
                |    return 1;
                |
                |  } else {
                |
                |    return 0;
                |  }
                |}
                |]]>
                """
                    .trimMargin()
            ),
            paragraph("okay"),
        )
    }

    @Test
    fun `should parse spec sample 183 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  <!-- foo -->
                |
                |    <!-- foo -->
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         *   <!-- foo -->
         * <pre><code>&lt;!-- foo --&gt;
         * </code></pre>
         */
        parsed.assertEquals(htmlBlock("  <!-- foo -->"), indentedCodeBlock("<!-- foo -->"))
    }

    @Test
    fun `should parse spec sample 184 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  <div>
                |
                |    <div>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         *   <div>
         * <pre><code>&lt;div&gt;
         * </code></pre>
         */
        parsed.assertEquals(htmlBlock("  <div>"), indentedCodeBlock("<div>"))
    }

    @Test
    fun `should parse spec sample 185 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |<div>
                |bar
                |</div>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo</p>
         * <div>
         * bar
         * </div>
         */
        parsed.assertEquals(
            paragraph("Foo"),
            htmlBlock(
                """
                |<div>
                |bar
                |</div>
                """
                    .trimMargin()
            ),
        )
    }

    @Test
    fun `should parse spec sample 186 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div>
                |bar
                |</div>
                |*foo*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div>
         * bar
         * </div>
         * *foo*
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<div>
                |bar
                |</div>
                |*foo*
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 187 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |<a href="bar">
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * <a href="bar">
         * baz</p>
         */
        parsed.assertEquals(
            Paragraph(Text("Foo"), SoftLineBreak, HtmlInline("<a href=\"bar\">"), SoftLineBreak, Text("baz"))
        )
    }

    @Test
    fun `should parse spec sample 188 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div>
                |
                |*Emphasized* text.
                |
                |</div>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div>
         * <p><em>Emphasized</em> text.</p>
         * </div>
         */
        parsed.assertEquals(
            htmlBlock("<div>"),
            Paragraph(Emphasis("*", Text("Emphasized")), Text(" text.")),
            htmlBlock("</div>"),
        )
    }

    @Test
    fun `should parse spec sample 189 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<div>
                |*Emphasized* text.
                |</div>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <div>
         * *Emphasized* text.
         * </div>
         */
        parsed.assertEquals(
            htmlBlock(
                """
                |<div>
                |*Emphasized* text.
                |</div>
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 190 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<table>
                |
                |<tr>
                |
                |<td>
                |Hi
                |</td>
                |
                |</tr>
                |
                |</table>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <table>
         * <tr>
         * <td>
         * Hi
         * </td>
         * </tr>
         * </table>
         */
        parsed.assertEquals(
            htmlBlock("<table>"),
            htmlBlock("<tr>"),
            htmlBlock("<td>\nHi\n</td>"),
            htmlBlock("</tr>"),
            htmlBlock("</table>"),
        )
    }

    @Test
    fun `should parse spec sample 191 correctly {HTML blocks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<table>
                |
                |  <tr>
                |
                |    <td>
                |      Hi
                |    </td>
                |
                |  </tr>
                |
                |</table>
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <table>
         *   <tr>
         * <pre><code>&lt;td&gt;
         *   Hi
         * &lt;/td&gt;
         * </code></pre>
         *   </tr>
         * </table>
         */
        parsed.assertEquals(
            htmlBlock("<table>"),
            htmlBlock("  <tr>"),
            indentedCodeBlock("<td>\n  Hi\n</td>"),
            htmlBlock("  </tr>"),
            htmlBlock("</table>"),
        )
    }

    @Test
    fun `should parse spec sample 192 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url "title"
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 193 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   [foo]: 
                |      /url  
                |           'the title'  
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="the title">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "the title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 194 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[Foo*bar\]]:my_(url) 'title (with parens)'
                |
                |[Foo*bar\]]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="my_(url)" title="title (with parens)">Foo*bar]</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "my_(url)", title = "title (with parens)", Text("Foo*bar]"))))
    }

    @Test
    fun `should parse spec sample 195 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[Foo bar]:
                |<my url>
                |'title'
                |
                |[Foo bar]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="my%20url" title="title">Foo bar</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "my url", title = "title", Text("Foo bar"))))
    }

    @Test
    fun `should parse spec sample 196 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url '
                |title
                |line1
                |line2
                |'
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="
         * title
         * line1
         * line2
         * ">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "\ntitle\nline1\nline2\n", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 197 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url 'title
                |
                |with blank line'
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo]: /url 'title</p>
         * <p>with blank line'</p>
         * <p>[foo]</p>
         */
        parsed.assertEquals(paragraph("[foo]: /url 'title"), paragraph("with blank line'"), paragraph("[foo]"))
    }

    @Test
    fun `should parse spec sample 198 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]:
                |/url
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 199 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]:
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo]:</p>
         * <p>[foo]</p>
         */
        parsed.assertEquals(paragraph("[foo]:"), paragraph("[foo]"))
    }

    @Test
    fun `should parse spec sample 200 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: <>
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 201 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: <bar>(baz)
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo]: <bar>(baz)</p>
         * <p>[foo]</p>
         */
        parsed.assertEquals(Paragraph(Text("[foo]: "), HtmlInline("<bar>"), Text("(baz)")), paragraph("[foo]"))
    }

    @Test
    fun `should parse spec sample 202 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url\bar\*baz "foo\"bar\baz"
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url%5Cbar*baz" title="foo&quot;bar\baz">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url\\bar*baz", title = "foo\"bar\\baz", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 203 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]
                |
                |[foo]: url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="url">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "url", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 204 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]
                |
                |[foo]: first
                |[foo]: second
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="first">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "first", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 205 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[FOO]: /url
                |
                |[Foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url">Foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = null, Text("Foo"))))
    }

    @Test
    fun `should parse spec sample 206 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[ΑΓΩ]: /φου
                |
                |[αγω]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/%CF%86%CE%BF%CF%85">αγω</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/φου", title = null, Text("αγω"))))
    }

    @Test
    fun `should parse spec sample 207 correctly {Link reference definitions}`() {
        val parsed = processor.processMarkdownDocument("[foo]: /url")

        /*
         * Expected HTML:
         * [intentionally blank]
         */
        parsed.assertEquals()
    }

    @Test
    fun `should parse spec sample 208 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[
                |foo
                |]: /url
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>bar</p>
         */
        parsed.assertEquals(paragraph("bar"))
    }

    @Test
    fun `should parse spec sample 209 correctly {Link reference definitions}`() {
        val parsed = processor.processMarkdownDocument("[foo]: /url \"title\" ok")

        /*
         * Expected HTML:
         * <p>[foo]: /url &quot;title&quot; ok</p>
         */
        parsed.assertEquals(paragraph("[foo]: /url \"title\" ok"))
    }

    @Test
    fun `should parse spec sample 210 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url
                |"title" ok
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>&quot;title&quot; ok</p>
         */
        parsed.assertEquals(paragraph("\"title\" ok"))
    }

    @Test
    fun `should parse spec sample 211 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    [foo]: /url "title"
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>[foo]: /url &quot;title&quot;
         * </code></pre>
         * <p>[foo]</p>
         */
        parsed.assertEquals(indentedCodeBlock("[foo]: /url \"title\""), paragraph("[foo]"))
    }

    @Test
    fun `should parse spec sample 212 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |```
                |[foo]: /url
                |```
                |
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>[foo]: /url
         * </code></pre>
         * <p>[foo]</p>
         */
        parsed.assertEquals(fencedCodeBlock("[foo]: /url"), paragraph("[foo]"))
    }

    @Test
    fun `should parse spec sample 213 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |[bar]: /baz
                |
                |[bar]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo
         * [bar]: /baz</p>
         * <p>[bar]</p>
         */
        parsed.assertEquals(Paragraph(Text("Foo"), SoftLineBreak, Text("[bar]: /baz")), paragraph("[bar]"))
    }

    @Test
    fun `should parse spec sample 214 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |# [Foo]
                |[foo]: /url
                |> bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h1><a href="/url">Foo</a></h1>
         * <blockquote>
         * <p>bar</p>
         * </blockquote>
         */
        parsed.assertEquals(
            heading(level = 1, Link(destination = "/url", title = null, Text("Foo"))),
            blockQuote(paragraph("bar")),
        )
    }

    @Test
    fun `should parse spec sample 215 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url
                |bar
                |===
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <h1>bar</h1>
         * <p><a href="/url">foo</a></p>
         */
        parsed.assertEquals(
            heading(level = 1, Text("bar")),
            Paragraph(Link(destination = "/url", title = null, Text("foo"))),
        )
    }

    @Test
    fun `should parse spec sample 216 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url
                |===
                |[foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>===
         * <a href="/url">foo</a></p>
         */
        parsed.assertEquals(
            Paragraph(Text("==="), SoftLineBreak, Link(destination = "/url", title = null, Text("foo")))
        )
    }

    @Test
    fun `should parse spec sample 217 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /foo-url "foo"
                |[bar]: /bar-url
                |  "bar"
                |[baz]: /baz-url
                |
                |[foo],
                |[bar],
                |[baz]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/foo-url" title="foo">foo</a>,
         * <a href="/bar-url" title="bar">bar</a>,
         * <a href="/baz-url">baz</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(destination = "/foo-url", title = "foo", Text("foo")),
                Text(","),
                SoftLineBreak,
                Link(destination = "/bar-url", title = "bar", Text("bar")),
                Text(","),
                SoftLineBreak,
                Link(destination = "/baz-url", title = null, Text("baz")),
            )
        )
    }

    @Test
    fun `should parse spec sample 218 correctly {Link reference definitions}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]
                |
                |> [foo]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url">foo</a></p>
         * <blockquote>
         * </blockquote>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = null, Text("foo"))), blockQuote())
    }

    @Test
    fun `should parse spec sample 219 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |aaa
                |
                |bbb
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa</p>
         * <p>bbb</p>
         */
        parsed.assertEquals(paragraph("aaa"), paragraph("bbb"))
    }

    @Test
    fun `should parse spec sample 220 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |aaa
                |bbb
                |
                |ccc
                |ddd
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa
         * bbb</p>
         * <p>ccc
         * ddd</p>
         */
        parsed.assertEquals(
            Paragraph(Text("aaa"), SoftLineBreak, Text("bbb")),
            Paragraph(Text("ccc"), SoftLineBreak, Text("ddd")),
        )
    }

    @Test
    fun `should parse spec sample 221 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |aaa
                |
                |
                |bbb
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa</p>
         * <p>bbb</p>
         */
        parsed.assertEquals(paragraph("aaa"), paragraph("bbb"))
    }

    @Test
    fun `should parse spec sample 222 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  aaa
                | bbb
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa
         * bbb</p>
         */
        parsed.assertEquals(Paragraph(Text("aaa"), SoftLineBreak, Text("bbb")))
    }

    @Test
    fun `should parse spec sample 223 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |aaa
                |             bbb
                |                                       ccc
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa
         * bbb
         * ccc</p>
         */
        parsed.assertEquals(Paragraph(Text("aaa"), SoftLineBreak, Text("bbb"), SoftLineBreak, Text("ccc")))
    }

    @Test
    fun `should parse spec sample 224 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   aaa
                |bbb
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa
         * bbb</p>
         */
        parsed.assertEquals(Paragraph(Text("aaa"), SoftLineBreak, Text("bbb")))
    }

    @Test
    fun `should parse spec sample 225 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    aaa
                |bbb
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>aaa
         * </code></pre>
         * <p>bbb</p>
         */
        parsed.assertEquals(indentedCodeBlock("aaa"), paragraph("bbb"))
    }

    @Test
    fun `should parse spec sample 226 correctly {Paragraphs}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |aaa     
                |bbb     
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa<br />
         * bbb</p>
         */
        parsed.assertEquals(Paragraph(Text("aaa"), HardLineBreak, Text("bbb")))
    }

    @Test
    fun `should parse spec sample 227 correctly {Blank lines}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  
                |
                |aaa
                |  
                |
                |# aaa
                |
                |  
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>aaa</p>
         * <h1>aaa</h1>
         */
        parsed.assertEquals(paragraph("aaa"), heading(level = 1, Text("aaa")))
    }

    @Test
    fun `should parse spec sample 228 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> # Foo
                |> bar
                |> baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <h1>Foo</h1>
         * <p>bar
         * baz</p>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(heading(level = 1, Text("Foo")), Paragraph(Text("bar"), SoftLineBreak, Text("baz")))
        )
    }

    @Test
    fun `should parse spec sample 229 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |># Foo
                |>bar
                |> baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <h1>Foo</h1>
         * <p>bar
         * baz</p>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(heading(level = 1, Text("Foo")), Paragraph(Text("bar"), SoftLineBreak, Text("baz")))
        )
    }

    @Test
    fun `should parse spec sample 230 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   > # Foo
                |   > bar
                | > baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <h1>Foo</h1>
         * <p>bar
         * baz</p>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(heading(level = 1, Text("Foo")), Paragraph(Text("bar"), SoftLineBreak, Text("baz")))
        )
    }

    @Test
    fun `should parse spec sample 231 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    > # Foo
                |    > bar
                |    > baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>&gt; # Foo
         * &gt; bar
         * &gt; baz
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("> # Foo\n> bar\n> baz"))
    }

    @Test
    fun `should parse spec sample 232 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> # Foo
                |> bar
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <h1>Foo</h1>
         * <p>bar
         * baz</p>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(heading(level = 1, Text("Foo")), Paragraph(Text("bar"), SoftLineBreak, Text("baz")))
        )
    }

    @Test
    fun `should parse spec sample 233 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> bar
                |baz
                |> foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>bar
         * baz
         * foo</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(Paragraph(Text("bar"), SoftLineBreak, Text("baz"), SoftLineBreak, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 234 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> foo
                |---
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo</p>
         * </blockquote>
         * <hr />
         */
        parsed.assertEquals(blockQuote(paragraph("foo")), thematicBreak())
    }

    @Test
    fun `should parse spec sample 235 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> - foo
                |- bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <ul>
         * <li>foo</li>
         * </ul>
         * </blockquote>
         * <ul>
         * <li>bar</li>
         * </ul>
         */
        parsed.assertEquals(
            blockQuote(unorderedList(listItem(paragraph("foo")))),
            unorderedList(listItem(paragraph("bar"))),
        )
    }

    @Test
    fun `should parse spec sample 236 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |>     foo
                |    bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <pre><code>foo
         * </code></pre>
         * </blockquote>
         * <pre><code>bar
         * </code></pre>
         */
        parsed.assertEquals(blockQuote(indentedCodeBlock("foo")), indentedCodeBlock("bar"))
    }

    @Test
    fun `should parse spec sample 237 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> ```
                |foo
                |```
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <pre><code></code></pre>
         * </blockquote>
         * <p>foo</p>
         * <pre><code></code></pre>
         */
        parsed.assertEquals(blockQuote(fencedCodeBlock("")), paragraph("foo"), fencedCodeBlock(""))
    }

    @Test
    fun `should parse spec sample 238 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> foo
                |    - bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo
         * - bar</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(Paragraph(Text("foo"), SoftLineBreak, Text("- bar"))))
    }

    @Test
    fun `should parse spec sample 239 correctly {Block quotes}`() {
        val parsed = processor.processMarkdownDocument(">")

        /*
         * Expected HTML:
         * <blockquote>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote())
    }

    @Test
    fun `should parse spec sample 240 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |>
                |>  
                |> 
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote())
    }

    @Test
    fun `should parse spec sample 241 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |>
                |> foo
                |>  
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(paragraph("foo")))
    }

    @Test
    fun `should parse spec sample 242 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> foo
                |
                |> bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo</p>
         * </blockquote>
         * <blockquote>
         * <p>bar</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(paragraph("foo")), blockQuote(paragraph("bar")))
    }

    @Test
    fun `should parse spec sample 243 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> foo
                |> bar
            """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo
         * bar</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(Paragraph(Text("foo"), SoftLineBreak, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 244 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> foo
                |>
                |> bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>foo</p>
         * <p>bar</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(paragraph("foo"), paragraph("bar")))
    }

    @Test
    fun `should parse spec sample 245 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo
                |> bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo</p>
         * <blockquote>
         * <p>bar</p>
         * </blockquote>
         */
        parsed.assertEquals(paragraph("foo"), blockQuote(paragraph("bar")))
    }

    @Test
    fun `should parse spec sample 246 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> aaa
                |***
                |> bbb
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>aaa</p>
         * </blockquote>
         * <hr />
         * <blockquote>
         * <p>bbb</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(paragraph("aaa")), thematicBreak(), blockQuote(paragraph("bbb")))
    }

    @Test
    fun `should parse spec sample 247 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> bar
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>bar
         * baz</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(Paragraph(Text("bar"), SoftLineBreak, Text("baz"))))
    }

    @Test
    fun `should parse spec sample 248 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> bar
                |
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>bar</p>
         * </blockquote>
         * <p>baz</p>
         */
        parsed.assertEquals(blockQuote(paragraph("bar")), paragraph("baz"))
    }

    @Test
    fun `should parse spec sample 249 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> bar
                |>
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <p>bar</p>
         * </blockquote>
         * <p>baz</p>
         */
        parsed.assertEquals(blockQuote(paragraph("bar")), paragraph("baz"))
    }

    @Test
    fun `should parse spec sample 250 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> > > foo
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <blockquote>
         * <blockquote>
         * <p>foo
         * bar</p>
         * </blockquote>
         * </blockquote>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(blockQuote(blockQuote(Paragraph(Text("foo"), SoftLineBreak, Text("bar"))))))
    }

    @Test
    fun `should parse spec sample 251 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |>>> foo
                |> bar
                |>>baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <blockquote>
         * <blockquote>
         * <p>foo
         * bar
         * baz</p>
         * </blockquote>
         * </blockquote>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(
                blockQuote(blockQuote(Paragraph(Text("foo"), SoftLineBreak, Text("bar"), SoftLineBreak, Text("baz"))))
            )
        )
    }

    @Test
    fun `should parse spec sample 252 correctly {Block quotes}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |>     code
                |
                |>    not code
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <pre><code>code
         * </code></pre>
         * </blockquote>
         * <blockquote>
         * <p>not code</p>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(indentedCodeBlock("code")), blockQuote(paragraph("not code")))
    }

    @Test
    fun `should parse spec sample 253 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |A paragraph
                |with two lines.
                |
                |    indented code
                |
                |> A block quote.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>A paragraph
         * with two lines.</p>
         * <pre><code>indented code
         * </code></pre>
         * <blockquote>
         * <p>A block quote.</p>
         * </blockquote>
         */
        parsed.assertEquals(
            Paragraph(Text("A paragraph"), SoftLineBreak, Text("with two lines.")),
            indentedCodeBlock("indented code"),
            blockQuote(paragraph("A block quote.")),
        )
    }

    @Test
    fun `should parse spec sample 254 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
            |1.  A paragraph
            |    with two lines.
            |
            |        indented code
            |
            |    > A block quote.
            """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>A paragraph
         * with two lines.</p>
         * <pre><code>indented code
         * </code></pre>
         * <blockquote>
         * <p>A block quote.</p>
         * </blockquote>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(
                    Paragraph(Text("A paragraph"), SoftLineBreak, Text("with two lines.")),
                    indentedCodeBlock("indented code"),
                    blockQuote(paragraph("A block quote.")),
                ),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 255 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- one
                |
                | two
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>one</li>
         * </ul>
         * <p>two</p>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("one"))), paragraph("two"))
    }

    @Test
    fun `should parse spec sample 256 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- one
                |
                |  two
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>one</p>
         * <p>two</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("one"), paragraph("two")), isTight = false))
    }

    @Test
    fun `should parse spec sample 257 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                | -    one
                |
                |     two
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>one</li>
         * </ul>
         * <pre><code> two
         * </code></pre>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("one"))), indentedCodeBlock(" two"))
    }

    @Test
    fun `should parse spec sample 258 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                | -    one
                |
                |      two
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>one</p>
         * <p>two</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("one"), paragraph("two")), isTight = false))
    }

    @Test
    fun `should parse spec sample 259 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   > > 1.  one
                |>>
                |>>     two
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <blockquote>
         * <ol>
         * <li>
         * <p>one</p>
         * <p>two</p>
         * </li>
         * </ol>
         * </blockquote>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(blockQuote(orderedList(listItem(paragraph("one"), paragraph("two")), isTight = false)))
        )
    }

    @Test
    fun `should parse spec sample 260 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |>>- one
                |>>
                |  >  > two
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <blockquote>
         * <ul>
         * <li>one</li>
         * </ul>
         * <p>two</p>
         * </blockquote>
         * </blockquote>
         */
        parsed.assertEquals(blockQuote(blockQuote(unorderedList(listItem(paragraph("one"))), paragraph("two"))))
    }

    @Test
    fun `should parse spec sample 261 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |-one
                |
                |2.two
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>-one</p>
         * <p>2.two</p>
         */
        parsed.assertEquals(paragraph("-one"), paragraph("2.two"))
    }

    @Test
    fun `should parse spec sample 262 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |
                |
                |  bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <p>bar</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"), paragraph("bar")), isTight = false))
    }

    @Test
    fun `should parse spec sample 263 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1.  foo
                |
                |    ```
                |    bar
                |    ```
                |
                |    baz
                |
                |    > bam
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>foo</p>
         * <pre><code>bar
         * </code></pre>
         * <p>baz</p>
         * <blockquote>
         * <p>bam</p>
         * </blockquote>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(paragraph("foo"), fencedCodeBlock("bar"), paragraph("baz"), blockQuote(paragraph("bam"))),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 264 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- Foo
                |
                |      bar
                |
                |
                |      baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>Foo</p>
         * <pre><code>bar
         *
         *
         * baz
         * </code></pre>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("Foo"), indentedCodeBlock("bar\n\n\nbaz")), isTight = false)
        )
    }

    @Test
    fun `should parse spec sample 265 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("123456789. ok")

        /*
         * Expected HTML:
         * <ol start="123456789">
         * <li>ok</li>
         * </ol>
         */
        parsed.assertEquals(orderedList(listItem(paragraph("ok")), startFrom = 123456789))
    }

    @Test
    fun `should parse spec sample 266 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("1234567890. not ok")

        /*
         * Expected HTML:
         * <p>1234567890. not ok</p>
         */
        parsed.assertEquals(paragraph("1234567890. not ok"))
    }

    @Test
    fun `should parse spec sample 267 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("0. ok")

        /*
         * Expected HTML:
         * <ol start="0">
         * <li>ok</li>
         * </ol>
         */
        parsed.assertEquals(orderedList(listItem(paragraph("ok")), startFrom = 0))
    }

    @Test
    fun `should parse spec sample 268 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("003. ok")

        /*
         * Expected HTML:
         * <ol start="3">
         * <li>ok</li>
         * </ol>
         */
        parsed.assertEquals(orderedList(listItem(paragraph("ok")), startFrom = 3))
    }

    @Test
    fun `should parse spec sample 269 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("-1. not ok")

        /*
         * Expected HTML:
         * <p>-1. not ok</p>
         */
        parsed.assertEquals(paragraph("-1. not ok"))
    }

    @Test
    fun `should parse spec sample 270 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |
                |      bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <pre><code>bar
         * </code></pre>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"), indentedCodeBlock("bar")), isTight = false))
    }

    @Test
    fun `should parse spec sample 271 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  10.  foo
                |
                |           bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol start="10">
         * <li>
         * <p>foo</p>
         * <pre><code>bar
         * </code></pre>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(listItem(paragraph("foo"), indentedCodeBlock("bar")), startFrom = 10, isTight = false)
        )
    }

    @Test
    fun `should parse spec sample 272 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    indented code
                |
                |paragraph
                |
                |    more code
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>indented code
         * </code></pre>
         * <p>paragraph</p>
         * <pre><code>more code
         * </code></pre>
         */
        parsed.assertEquals(indentedCodeBlock("indented code"), paragraph("paragraph"), indentedCodeBlock("more code"))
    }

    @Test
    fun `should parse spec sample 273 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1.     indented code
                |
                |   paragraph
                |
                |       more code
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <pre><code>indented code
         * </code></pre>
         * <p>paragraph</p>
         * <pre><code>more code
         * </code></pre>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(indentedCodeBlock("indented code"), paragraph("paragraph"), indentedCodeBlock("more code")),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 274 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1.      indented code
                |
                |   paragraph
                |
                |       more code
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <pre><code> indented code
         * </code></pre>
         * <p>paragraph</p>
         * <pre><code>more code
         * </code></pre>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(indentedCodeBlock(" indented code"), paragraph("paragraph"), indentedCodeBlock("more code")),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 275 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   foo
                |
                |bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo</p>
         * <p>bar</p>
         */
        parsed.assertEquals(paragraph("foo"), paragraph("bar"))
    }

    @Test
    fun `should parse spec sample 276 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |-    foo
                |
                |  bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * </ul>
         * <p>bar</p>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"))), paragraph("bar"))
    }

    @Test
    fun `should parse spec sample 277 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |-  foo
                |
                |   bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <p>bar</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"), paragraph("bar")), isTight = false))
    }

    @Test
    fun `should parse spec sample 278 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |-
                |  foo
                |-
                |  ```
                |  bar
                |  ```
                |-
                |      baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * <li>
         * <pre><code>bar
         * </code></pre>
         * </li>
         * <li>
         * <pre><code>baz
         * </code></pre>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("foo")),
                listItem(fencedCodeBlock("bar")),
                listItem(indentedCodeBlock("baz")),
            )
        )
    }

    @Test
    fun `should parse spec sample 279 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |-   
                |  foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo"))))
    }

    @Test
    fun `should parse spec sample 280 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |-
                |
                |  foo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li></li>
         * </ul>
         * <p>foo</p>
         */
        parsed.assertEquals(unorderedList(listItem()), paragraph("foo"))
    }

    @Test
    fun `should parse spec sample 281 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |-
                |- bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * <li></li>
         * <li>bar</li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo")), listItem(), listItem(paragraph("bar"))))
    }

    @Test
    fun `should parse spec sample 282 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |-   
                |- bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * <li></li>
         * <li>bar</li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("foo")), listItem(), listItem(paragraph("bar"))))
    }

    @Test
    fun `should parse spec sample 283 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1. foo
                |2.
                |3. bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>foo</li>
         * <li></li>
         * <li>bar</li>
         * </ol>
         */
        parsed.assertEquals(orderedList(listItem(paragraph("foo")), listItem(), listItem(paragraph("bar"))))
    }

    @Test
    fun `should parse spec sample 284 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("*")

        /*
         * Expected HTML:
         * <ul>
         * <li></li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(), marker = "*"))
    }

    @Test
    fun `should parse spec sample 285 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo
                |*
                |
                |foo
                |1.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo
         * *</p>
         * <p>foo
         * 1.</p>
         */
        parsed.assertEquals(
            Paragraph(Text("foo"), SoftLineBreak, Text("*")),
            Paragraph(Text("foo"), SoftLineBreak, Text("1.")),
        )
    }

    @Test
    fun `should parse spec sample 286 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                | 1.  A paragraph
                |     with two lines.
                |
                |         indented code
                |
                |     > A block quote.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>A paragraph
         * with two lines.</p>
         * <pre><code>indented code
         * </code></pre>
         * <blockquote>
         * <p>A block quote.</p>
         * </blockquote>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(
                    Paragraph(Text("A paragraph"), SoftLineBreak, Text("with two lines.")),
                    indentedCodeBlock("indented code"),
                    blockQuote(paragraph("A block quote.")),
                ),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 287 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  1.  A paragraph
                |      with two lines.
                |
                |          indented code
                |
                |      > A block quote.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>A paragraph
         * with two lines.</p>
         * <pre><code>indented code
         * </code></pre>
         * <blockquote>
         * <p>A block quote.</p>
         * </blockquote>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(
                    Paragraph(Text("A paragraph"), SoftLineBreak, Text("with two lines.")),
                    indentedCodeBlock("indented code"),
                    blockQuote(paragraph("A block quote.")),
                ),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 288 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |   1.  A paragraph
                |       with two lines.
                |
                |           indented code
                |
                |       > A block quote.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>A paragraph
         * with two lines.</p>
         * <pre><code>indented code
         * </code></pre>
         * <blockquote>
         * <p>A block quote.</p>
         * </blockquote>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(
                    Paragraph(Text("A paragraph"), SoftLineBreak, Text("with two lines.")),
                    indentedCodeBlock("indented code"),
                    blockQuote(paragraph("A block quote.")),
                ),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 289 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |    1.  A paragraph
                |        with two lines.
                |
                |            indented code
                |
                |        > A block quote.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <pre><code>1.  A paragraph
         *     with two lines.
         *
         *         indented code
         *
         *     &gt; A block quote.
         * </code></pre>
         */
        parsed.assertEquals(
            indentedCodeBlock(
                """
                |1.  A paragraph
                |    with two lines.
                |
                |        indented code
                |
                |    > A block quote.
                """
                    .trimMargin()
            )
        )
    }

    @Test
    fun `should parse spec sample 290 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  1.  A paragraph
                |with two lines.
                |
                |          indented code
                |
                |      > A block quote.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>A paragraph
         * with two lines.</p>
         * <pre><code>indented code
         * </code></pre>
         * <blockquote>
         * <p>A block quote.</p>
         * </blockquote>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(
                    Paragraph(Text("A paragraph"), SoftLineBreak, Text("with two lines.")),
                    indentedCodeBlock("indented code"),
                    blockQuote(paragraph("A block quote.")),
                ),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 291 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |  1.  A paragraph
                |    with two lines.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>A paragraph
         * with two lines.</li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(listItem(Paragraph(Text("A paragraph"), SoftLineBreak, Text("with two lines."))))
        )
    }

    @Test
    fun `should parse spec sample 292 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> 1. > Blockquote
                |continued here.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <ol>
         * <li>
         * <blockquote>
         * <p>Blockquote
         * continued here.</p>
         * </blockquote>
         * </li>
         * </ol>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(
                orderedList(listItem(blockQuote(Paragraph(Text("Blockquote"), SoftLineBreak, Text("continued here.")))))
            )
        )
    }

    @Test
    fun `should parse spec sample 293 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |> 1. > Blockquote
                |> continued here.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <blockquote>
         * <ol>
         * <li>
         * <blockquote>
         * <p>Blockquote
         * continued here.</p>
         * </blockquote>
         * </li>
         * </ol>
         * </blockquote>
         */
        parsed.assertEquals(
            blockQuote(
                orderedList(listItem(blockQuote(Paragraph(Text("Blockquote"), SoftLineBreak, Text("continued here.")))))
            )
        )
    }

    @Test
    fun `should parse spec sample 294 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |  - bar
                |    - baz
                |      - boo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo
         * <ul>
         * <li>bar
         * <ul>
         * <li>baz
         * <ul>
         * <li>boo</li>
         * </ul>
         * </li>
         * </ul>
         * </li>
         * </ul>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(
                    paragraph("foo"),
                    unorderedList(
                        listItem(
                            paragraph("bar"),
                            unorderedList(listItem(paragraph("baz"), unorderedList(listItem(paragraph("boo"))))),
                        )
                    ),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 295 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                | - bar
                |  - baz
                |   - boo
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * <li>bar</li>
         * <li>baz</li>
         * <li>boo</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("foo")),
                listItem(paragraph("bar")),
                listItem(paragraph("baz")),
                listItem(paragraph("boo")),
            )
        )
    }

    @Test
    fun `should parse spec sample 296 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |10) foo
                |    - bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol start="10">
         * <li>foo
         * <ul>
         * <li>bar</li>
         * </ul>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(
                listItem(paragraph("foo"), unorderedList(listItem(paragraph("bar")))),
                startFrom = 10,
                delimiter = ")",
            )
        )
    }

    @Test
    fun `should parse spec sample 297 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |10) foo
                |   - bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol start="10">
         * <li>foo</li>
         * </ol>
         * <ul>
         * <li>bar</li>
         * </ul>
         */
        parsed.assertEquals(
            orderedList(listItem(paragraph("foo")), startFrom = 10, delimiter = ")"),
            unorderedList(listItem(paragraph("bar"))),
        )
    }

    @Test
    fun `should parse spec sample 298 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("- - foo")

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <ul>
         * <li>foo</li>
         * </ul>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(unorderedList(listItem(paragraph("foo"))))))
    }

    @Test
    fun `should parse spec sample 299 correctly {List items}`() {
        val parsed = processor.processMarkdownDocument("1. - 2. foo")

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <ul>
         * <li>
         * <ol start="2">
         * <li>foo</li>
         * </ol>
         * </li>
         * </ul>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(listItem(unorderedList(listItem(orderedList(listItem(paragraph("foo")), startFrom = 2)))))
        )
    }

    @Test
    fun `should parse spec sample 300 correctly {List items}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- # Foo
                |- Bar
                |  ---
                |  baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <h1>Foo</h1>
         * </li>
         * <li>
         * <h2>Bar</h2>
         * baz</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(heading(level = 1, Text("Foo"))),
                listItem(heading(level = 2, Text("Bar")), paragraph("baz")),
            )
        )
    }

    @Test
    fun `should parse spec sample 301 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |- bar
                |+ baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * <li>bar</li>
         * </ul>
         * <ul>
         * <li>baz</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("foo")), listItem(paragraph("bar"))),
            unorderedList(listItem(paragraph("baz")), marker = "+"),
        )
    }

    @Test
    fun `should parse spec sample 302 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1. foo
                |2. bar
                |3) baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>foo</li>
         * <li>bar</li>
         * </ol>
         * <ol start="3">
         * <li>baz</li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(listItem(paragraph("foo")), listItem(paragraph("bar"))),
            orderedList(listItem(paragraph("baz")), startFrom = 3, delimiter = ")"),
        )
    }

    @Test
    fun `should parse spec sample 303 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |Foo
                |- bar
                |- baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>Foo</p>
         * <ul>
         * <li>bar</li>
         * <li>baz</li>
         * </ul>
         */
        parsed.assertEquals(paragraph("Foo"), unorderedList(listItem(paragraph("bar")), listItem(paragraph("baz"))))
    }

    @Test
    fun `should parse spec sample 304 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |The number of windows in my house is
                |14.  The number of doors is 6.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>The number of windows in my house is
         * 14.  The number of doors is 6.</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("The number of windows in my house is"),
                SoftLineBreak,
                Text("14.  The number of doors is 6."),
            )
        )
    }

    @Test
    fun `should parse spec sample 305 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |The number of windows in my house is
                |1.  The number of doors is 6.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>The number of windows in my house is</p>
         * <ol>
         * <li>The number of doors is 6.</li>
         * </ol>
         */
        parsed.assertEquals(
            paragraph("The number of windows in my house is"),
            orderedList(listItem(paragraph("The number of doors is 6."))),
        )
    }

    @Test
    fun `should parse spec sample 306 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |
                |- bar
                |
                |
                |- baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * </li>
         * <li>
         * <p>bar</p>
         * </li>
         * <li>
         * <p>baz</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("foo")),
                listItem(paragraph("bar")),
                listItem(paragraph("baz")),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 307 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |  - bar
                |    - baz
                |
                |
                |      bim
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo
         * <ul>
         * <li>bar
         * <ul>
         * <li>
         * <p>baz</p>
         * <p>bim</p>
         * </li>
         * </ul>
         * </li>
         * </ul>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(
                    paragraph("foo"),
                    unorderedList(
                        listItem(
                            paragraph("bar"),
                            unorderedList(listItem(paragraph("baz"), paragraph("bim")), isTight = false),
                        )
                    ),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 308 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- foo
                |- bar
                |
                |<!-- -->
                |
                |- baz
                |- bim
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>foo</li>
         * <li>bar</li>
         * </ul>
         * <!-- -->
         * <ul>
         * <li>baz</li>
         * <li>bim</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("foo")), listItem(paragraph("bar"))),
            htmlBlock("<!-- -->"),
            unorderedList(listItem(paragraph("baz")), listItem(paragraph("bim"))),
        )
    }

    @Test
    fun `should parse spec sample 309 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |-   foo
                |
                |    notcode
                |
                |-   foo
                |
                |<!-- -->
                |
                |    code
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <p>notcode</p>
         * </li>
         * <li>
         * <p>foo</p>
         * </li>
         * </ul>
         * <!-- -->
         * <pre><code>code
         * </code></pre>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("foo"), paragraph("notcode")),
                listItem(paragraph("foo")),
                isTight = false,
            ),
            htmlBlock("<!-- -->"),
            indentedCodeBlock("code"),
        )
    }

    @Test
    fun `should parse spec sample 310 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                | - b
                |  - c
                |   - d
                |  - e
                | - f
                |- g
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>a</li>
         * <li>b</li>
         * <li>c</li>
         * <li>d</li>
         * <li>e</li>
         * <li>f</li>
         * <li>g</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("a")),
                listItem(paragraph("b")),
                listItem(paragraph("c")),
                listItem(paragraph("d")),
                listItem(paragraph("e")),
                listItem(paragraph("f")),
                listItem(paragraph("g")),
            )
        )
    }

    @Test
    fun `should parse spec sample 311 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1. a
                |
                |  2. b
                |
                |   3. c
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>a</p>
         * </li>
         * <li>
         * <p>b</p>
         * </li>
         * <li>
         * <p>c</p>
         * </li>
         * </ol>
         */
        parsed.assertEquals(
            orderedList(listItem(paragraph("a")), listItem(paragraph("b")), listItem(paragraph("c")), isTight = false)
        )
    }

    @Test
    fun `should parse spec sample 312 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                | - b
                |  - c
                |   - d
                |    - e
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>a</li>
         * <li>b</li>
         * <li>c</li>
         * <li>d
         * - e</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("a")),
                listItem(paragraph("b")),
                listItem(paragraph("c")),
                listItem(Paragraph(Text("d"), SoftLineBreak, Text("- e"))),
            )
        )
    }

    @Test
    fun `should parse spec sample 313 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1. a
                |
                |  2. b
                |
                |    3. c
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <p>a</p>
         * </li>
         * <li>
         * <p>b</p>
         * </li>
         * </ol>
         * <pre><code>3. c
         * </code></pre>
         */
        parsed.assertEquals(
            orderedList(listItem(paragraph("a")), listItem(paragraph("b")), isTight = false),
            indentedCodeBlock("3. c"),
        )
    }

    @Test
    fun `should parse spec sample 314 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |- b
                |
                |- c
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>a</p>
         * </li>
         * <li>
         * <p>b</p>
         * </li>
         * <li>
         * <p>c</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("a")), listItem(paragraph("b")), listItem(paragraph("c")), isTight = false)
        )
    }

    @Test
    fun `should parse spec sample 315 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |* a
                |*
                |
                |* c
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>a</p>
         * </li>
         * <li></li>
         * <li>
         * <p>c</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("a")), listItem(), listItem(paragraph("c")), isTight = false, marker = "*")
        )
    }

    @Test
    fun `should parse spec sample 316 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |- b
                |
                |  c
                |- d
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>a</p>
         * </li>
         * <li>
         * <p>b</p>
         * <p>c</p>
         * </li>
         * <li>
         * <p>d</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("a")),
                listItem(paragraph("b"), paragraph("c")),
                listItem(paragraph("d")),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 317 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |- b
                |
                |  [ref]: /url
                |- d
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>a</p>
         * </li>
         * <li>
         * <p>b</p>
         * </li>
         * <li>
         * <p>d</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("a")), listItem(paragraph("b")), listItem(paragraph("d")), isTight = false)
        )
    }

    @Test
    fun `should parse spec sample 318 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |- ```
                |  b
                |
                |
                |  ```
                |- c
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>a</li>
         * <li>
         * <pre><code>b
         *
         *
         * </code></pre>
         * </li>
         * <li>c</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("a")), listItem(fencedCodeBlock("b\n\n")), listItem(paragraph("c")))
        )
    }

    @Test
    fun `should parse spec sample 319 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |  - b
                |
                |    c
                |- d
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>a
         * <ul>
         * <li>
         * <p>b</p>
         * <p>c</p>
         * </li>
         * </ul>
         * </li>
         * <li>d</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("a"), unorderedList(listItem(paragraph("b"), paragraph("c")), isTight = false)),
                listItem(paragraph("d")),
            )
        )
    }

    @Test
    fun `should parse spec sample 320 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |* a
                |  > b
                |  >
                |* c
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>a
         * <blockquote>
         * <p>b</p>
         * </blockquote>
         * </li>
         * <li>c</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(listItem(paragraph("a"), blockQuote(paragraph("b"))), listItem(paragraph("c")), marker = "*")
        )
    }

    @Test
    fun `should parse spec sample 321 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |  > b
                |  ```
                |  c
                |  ```
                |- d
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>a
         * <blockquote>
         * <p>b</p>
         * </blockquote>
         * <pre><code>c
         * </code></pre>
         * </li>
         * <li>d</li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("a"), blockQuote(paragraph("b")), fencedCodeBlock("c")),
                listItem(paragraph("d")),
            )
        )
    }

    @Test
    fun `should parse spec sample 322 correctly {Lists}`() {
        val parsed = processor.processMarkdownDocument("- a")

        /*
         * Expected HTML:
         * <ul>
         * <li>a</li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("a"))))
    }

    @Test
    fun `should parse spec sample 323 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |  - b
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>a
         * <ul>
         * <li>b</li>
         * </ul>
         * </li>
         * </ul>
         */
        parsed.assertEquals(unorderedList(listItem(paragraph("a"), unorderedList(listItem(paragraph("b"))))))
    }

    @Test
    fun `should parse spec sample 324 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |1. ```
                |   foo
                |   ```
                |
                |   bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ol>
         * <li>
         * <pre><code>foo
         * </code></pre>
         * <p>bar</p>
         * </li>
         * </ol>
         */
        parsed.assertEquals(orderedList(listItem(fencedCodeBlock("foo"), paragraph("bar")), isTight = false))
    }

    @Test
    fun `should parse spec sample 325 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |* foo
                |  * bar
                |
                |  baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>foo</p>
         * <ul>
         * <li>bar</li>
         * </ul>
         * <p>baz</p>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("foo"), unorderedList(listItem(paragraph("bar")), marker = "*"), paragraph("baz")),
                marker = "*",
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 326 correctly {Lists}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |- a
                |  - b
                |  - c
                |
                |- d
                |  - e
                |  - f
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <ul>
         * <li>
         * <p>a</p>
         * <ul>
         * <li>b</li>
         * <li>c</li>
         * </ul>
         * </li>
         * <li>
         * <p>d</p>
         * <ul>
         * <li>e</li>
         * <li>f</li>
         * </ul>
         * </li>
         * </ul>
         */
        parsed.assertEquals(
            unorderedList(
                listItem(paragraph("a"), unorderedList(listItem(paragraph("b")), listItem(paragraph("c")))),
                listItem(paragraph("d"), unorderedList(listItem(paragraph("e")), listItem(paragraph("f")))),
                isTight = false,
            )
        )
    }

    @Test
    fun `should parse spec sample 327 correctly {Inlines}`() {
        val parsed = processor.processMarkdownDocument("`hi`lo`")

        /*
         * Expected HTML:
         * <p><code>hi</code>lo`</p>
         */
        parsed.assertEquals(Paragraph(Code("hi"), Text("lo`")))
    }

    @Test
    fun `should parse spec sample 328 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`foo`")

        /*
         * Expected HTML:
         * <p><code>foo</code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo")))
    }

    @Test
    fun `should parse spec sample 329 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`` foo ` bar ``")

        /*
         * Expected HTML:
         * <p><code>foo ` bar</code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo ` bar")))
    }

    @Test
    fun `should parse spec sample 330 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("` `` `")

        /*
         * Expected HTML:
         * <p><code>``</code></p>
         */
        parsed.assertEquals(Paragraph(Code("``")))
    }

    @Test
    fun `should parse spec sample 331 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`  ``  `")

        /*
         * Expected HTML:
         * <p><code> `` </code></p>
         */
        parsed.assertEquals(Paragraph(Code(" `` ")))
    }

    @Test
    fun `should parse spec sample 332 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("` a`")

        /*
         * Expected HTML:
         * <p><code> a</code></p>
         */
        parsed.assertEquals(Paragraph(Code(" a")))
    }

    @Test
    fun `should parse spec sample 333 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("` b `")

        /*
         * Expected HTML:
         * <p><code> b </code></p>
         */
        parsed.assertEquals(Paragraph(Code(" b ")))
    }

    @Test
    fun `should parse spec sample 334 correctly {Code spans}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |` `
                |`  `
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code> </code>
         * <code>  </code></p>
         */
        parsed.assertEquals(Paragraph(Code(" "), SoftLineBreak, Code("  ")))
    }

    @Test
    fun `should parse spec sample 335 correctly {Code spans}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |``
                |foo
                |bar  
                |baz
                |``
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code>foo bar   baz</code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo bar   baz")))
    }

    @Test
    fun `should parse spec sample 336 correctly {Code spans}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |``
                |foo 
                |``
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code>foo </code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo ")))
    }

    @Test
    fun `should parse spec sample 337 correctly {Code spans}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |`foo   bar 
                |baz`
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code>foo   bar  baz</code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo   bar  baz")))
    }

    @Test
    fun `should parse spec sample 338 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`foo\\`bar`")

        /*
         * Expected HTML:
         * <p><code>foo\</code>bar`</p>
         */
        parsed.assertEquals(Paragraph(Code("foo\\"), Text("bar`")))
    }

    @Test
    fun `should parse spec sample 339 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("``foo`bar``")

        /*
         * Expected HTML:
         * <p><code>foo`bar</code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo`bar")))
    }

    @Test
    fun `should parse spec sample 340 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("` foo `` bar `")

        /*
         * Expected HTML:
         * <p><code>foo `` bar</code></p>
         */
        parsed.assertEquals(Paragraph(Code("foo `` bar")))
    }

    @Test
    fun `should parse spec sample 341 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("*foo`*`")

        /*
         * Expected HTML:
         * <p>*foo<code>*</code></p>
         */
        parsed.assertEquals(Paragraph(Text("*foo"), Code("*")))
    }

    @Test
    fun `should parse spec sample 342 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("[not a `link](/foo`)")

        /*
         * Expected HTML:
         * <p>[not a <code>link](/foo</code>)</p>
         */
        parsed.assertEquals(Paragraph(Text("[not a "), Code("link](/foo"), Text(")")))
    }

    @Test
    fun `should parse spec sample 343 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`<a href=\"`\">`")

        /*
         * Expected HTML:
         * <p><code>&lt;a href=&quot;</code>&quot;&gt;`</p>
         */
        parsed.assertEquals(Paragraph(Code("<a href=\""), Text("\">`")))
    }

    @Test
    fun `should parse spec sample 344 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("<a href=\"`\">`")

        /*
         * Expected HTML:
         * <p><a href="`">`</p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("<a href=\"`\">"), Text("`")))
    }

    @Test
    fun `should parse spec sample 345 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`<https://foo.bar.`baz>`")

        /*
         * Expected HTML:
         * <p><code>&lt;https://foo.bar.</code>baz&gt;`</p>
         */
        parsed.assertEquals(Paragraph(Code("<https://foo.bar."), Text("baz>`")))
    }

    @Test
    fun `should parse spec sample 346 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("<https://foo.bar.`baz>`")

        /*
         * Expected HTML:
         * <p><a href="https://foo.bar.%60baz">https://foo.bar.`baz</a>`</p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "https://foo.bar.`baz", title = null, Text("https://foo.bar.`baz")), Text("`"))
        )
    }

    @Test
    fun `should parse spec sample 347 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("```foo``")

        /*
         * Expected HTML:
         * <p>```foo``</p>
         */
        parsed.assertEquals(paragraph("```foo``"))
    }

    @Test
    fun `should parse spec sample 348 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`foo")

        /*
         * Expected HTML:
         * <p>`foo</p>
         */
        parsed.assertEquals(paragraph("`foo"))
    }

    @Test
    fun `should parse spec sample 349 correctly {Code spans}`() {
        val parsed = processor.processMarkdownDocument("`foo``bar``")

        /*
         * Expected HTML:
         * <p>`foo<code>bar</code></p>
         */
        parsed.assertEquals(Paragraph(Text("`foo"), Code("bar")))
    }

    @Test
    fun `should parse spec sample 350 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo bar*")

        /*
         * Expected HTML:
         * <p><em>foo bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text(("foo bar")))))
    }

    @Test
    fun `should parse spec sample 351 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("a * foo bar*")

        /*
         * Expected HTML:
         * <p>a * foo bar*</p>
         */
        parsed.assertEquals(paragraph("a * foo bar*"))
    }

    @Test
    fun `should parse spec sample 352 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("a*\"foo\"*")

        /*
         * Expected HTML:
         * <p>a*&quot;foo&quot;*</p>
         */
        parsed.assertEquals(paragraph("a*\"foo\"*"))
    }

    @Test
    fun `should parse spec sample 353 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("* a *")

        /*
         * Expected HTML:
         * <p>* a *</p>
         */
        parsed.assertEquals(paragraph("* a *"))
    }

    @Test
    fun `should parse spec sample 354 correctly {Emphasis and strong emphasis}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |*$*alpha.
                |
                |*£*bravo.
                |
                |*€*charlie.
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>*$*alpha.</p>
         * <p>*£*bravo.</p>
         * <p>*€*charlie.</p>
         */
        parsed.assertEquals(paragraph("*$*alpha."), paragraph("*£*bravo."), paragraph("*€*charlie."))
    }

    @Test
    fun `should parse spec sample 355 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo*bar*")

        /*
         * Expected HTML:
         * <p>foo<em>bar</em></p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), Emphasis("*", Text("bar"))))
    }

    @Test
    fun `should parse spec sample 356 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("5*6*78")

        /*
         * Expected HTML:
         * <p>5<em>6</em>78</p>
         */
        parsed.assertEquals(Paragraph(Text("5"), Emphasis("*", Text("6")), Text("78")))
    }

    @Test
    fun `should parse spec sample 357 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo bar_")

        /*
         * Expected HTML:
         * <p><em>foo bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("foo bar"))))
    }

    @Test
    fun `should parse spec sample 358 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_ foo bar_")

        /*
         * Expected HTML:
         * <p>_ foo bar_</p>
         */
        parsed.assertEquals(paragraph("_ foo bar_"))
    }

    @Test
    fun `should parse spec sample 359 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("a_\"foo\"_")

        /*
         * Expected HTML:
         * <p>a_&quot;foo&quot;_</p>
         */
        parsed.assertEquals(paragraph("a_\"foo\"_"))
    }

    @Test
    fun `should parse spec sample 360 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo_bar_")

        /*
         * Expected HTML:
         * <p>foo_bar_</p>
         */
        parsed.assertEquals(paragraph("foo_bar_"))
    }

    @Test
    fun `should parse spec sample 361 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("5_6_78")

        /*
         * Expected HTML:
         * <p>5_6_78</p>
         */
        parsed.assertEquals(paragraph("5_6_78"))
    }

    @Test
    fun `should parse spec sample 362 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("пристаням_стремятся_")

        /*
         * Expected HTML:
         * <p>пристаням_стремятся_</p>
         */
        parsed.assertEquals(paragraph("пристаням_стремятся_"))
    }

    @Test
    fun `should parse spec sample 363 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("aa_\"bb\"_cc")

        /*
         * Expected HTML:
         * <p>aa_&quot;bb&quot;_cc</p>
         */
        parsed.assertEquals(paragraph("aa_\"bb\"_cc"))
    }

    @Test
    fun `should parse spec sample 364 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo-_(bar)_")

        /*
         * Expected HTML:
         * <p>foo-<em>(bar)</em></p>
         */
        parsed.assertEquals(Paragraph(Text("foo-"), Emphasis("_", Text("(bar)"))))
    }

    @Test
    fun `should parse spec sample 365 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo*")

        /*
         * Expected HTML:
         * <p>_foo*</p>
         */
        parsed.assertEquals(paragraph("_foo*"))
    }

    @Test
    fun `should parse spec sample 366 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo bar *")

        /*
         * Expected HTML:
         * <p>*foo bar *</p>
         */
        parsed.assertEquals(paragraph("*foo bar *"))
    }

    @Test
    fun `should parse spec sample 367 correctly {Emphasis and strong emphasis}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |*foo bar
                |*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>*foo bar
         * *</p>
         */
        parsed.assertEquals(Paragraph(Text("*foo bar"), SoftLineBreak, Text("*")))
    }

    @Test
    fun `should parse spec sample 368 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*(*foo)")

        /*
         * Expected HTML:
         * <p>*(*foo)</p>
         */
        parsed.assertEquals(paragraph("*(*foo)"))
    }

    @Test
    fun `should parse spec sample 369 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*(*foo*)*")

        /*
         * Expected HTML:
         * <p><em>(<em>foo</em>)</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("("), Emphasis("*", Text("foo")), Text(")"))))
    }

    @Test
    fun `should parse spec sample 370 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo*bar")

        /*
         * Expected HTML:
         * <p><em>foo</em>bar</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo")), Text("bar")))
    }

    @Test
    fun `should parse spec sample 371 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo bar _")

        /*
         * Expected HTML:
         * <p>_foo bar _</p>
         */
        parsed.assertEquals(paragraph("_foo bar _"))
    }

    @Test
    fun `should parse spec sample 372 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_(_foo)")

        /*
         * Expected HTML:
         * <p>_(_foo)</p>
         */
        parsed.assertEquals(paragraph("_(_foo)"))
    }

    @Test
    fun `should parse spec sample 373 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_(_foo_)_")

        /*
         * Expected HTML:
         * <p><em>(<em>foo</em>)</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("("), Emphasis("_", Text("foo")), Text(")"))))
    }

    @Test
    fun `should parse spec sample 374 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo_bar")

        /*
         * Expected HTML:
         * <p>_foo_bar</p>
         */
        parsed.assertEquals(paragraph("_foo_bar"))
    }

    @Test
    fun `should parse spec sample 375 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_пристаням_стремятся")

        /*
         * Expected HTML:
         * <p>_пристаням_стремятся</p>
         */
        parsed.assertEquals(paragraph("_пристаням_стремятся"))
    }

    @Test
    fun `should parse spec sample 376 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo_bar_baz_")

        /*
         * Expected HTML:
         * <p><em>foo_bar_baz</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("foo_bar_baz"))))
    }

    @Test
    fun `should parse spec sample 377 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_(bar)_.")

        /*
         * Expected HTML:
         * <p><em>(bar)</em>.</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("(bar)")), Text(".")))
    }

    @Test
    fun `should parse spec sample 378 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo bar**")

        /*
         * Expected HTML:
         * <p><strong>foo bar</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo bar"))))
    }

    @Test
    fun `should parse spec sample 379 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("** foo bar**")

        /*
         * Expected HTML:
         * <p>** foo bar**</p>
         */
        parsed.assertEquals(paragraph("** foo bar**"))
    }

    @Test
    fun `should parse spec sample 380 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("a**\"foo\"**")

        /*
         * Expected HTML:
         * <p>a**&quot;foo&quot;**</p>
         */
        parsed.assertEquals(paragraph("a**\"foo\"**"))
    }

    @Test
    fun `should parse spec sample 381 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo**bar**")

        /*
         * Expected HTML:
         * <p>foo<strong>bar</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), StrongEmphasis("**", Text("bar"))))
    }

    @Test
    fun `should parse spec sample 382 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo bar__")

        /*
         * Expected HTML:
         * <p><strong>foo bar</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("foo bar"))))
    }

    @Test
    fun `should parse spec sample 383 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__ foo bar__")

        /*
         * Expected HTML:
         * <p>__ foo bar__</p>
         */
        parsed.assertEquals(paragraph("__ foo bar__"))
    }

    @Test
    fun `should parse spec sample 384 correctly {Emphasis and strong emphasis}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |__
                |foo bar__
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>__
         * foo bar__</p>
         */
        parsed.assertEquals(Paragraph(Text("__"), SoftLineBreak, Text("foo bar__")))
    }

    @Test
    fun `should parse spec sample 385 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("a__\"foo\"__")

        /*
         * Expected HTML:
         * <p>a__&quot;foo&quot;__</p>
         */
        parsed.assertEquals(paragraph("a__\"foo\"__"))
    }

    @Test
    fun `should parse spec sample 386 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo__bar__")

        /*
         * Expected HTML:
         * <p>foo__bar__</p>
         */
        parsed.assertEquals(paragraph("foo__bar__"))
    }

    @Test
    fun `should parse spec sample 387 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("5__6__78")

        /*
         * Expected HTML:
         * <p>5__6__78</p>
         */
        parsed.assertEquals(paragraph("5__6__78"))
    }

    @Test
    fun `should parse spec sample 388 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("пристаням__стремятся__")

        /*
         * Expected HTML:
         * <p>пристаням__стремятся__</p>
         */
        parsed.assertEquals(paragraph("пристаням__стремятся__"))
    }

    @Test
    fun `should parse spec sample 389 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo, __bar__, baz__")

        /*
         * Expected HTML:
         * <p><strong>foo, <strong>bar</strong>, baz</strong></p>
         */
        parsed.assertEquals(
            Paragraph(StrongEmphasis("__", Text("foo, "), StrongEmphasis("__", Text("bar")), Text(", baz")))
        )
    }

    @Test
    fun `should parse spec sample 390 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo-__(bar)__")

        /*
         * Expected HTML:
         * <p>foo-<strong>(bar)</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("foo-"), StrongEmphasis("__", Text("(bar)"))))
    }

    @Test
    fun `should parse spec sample 391 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo bar **")

        /*
         * Expected HTML:
         * <p>**foo bar **</p>
         */
        parsed.assertEquals(paragraph("**foo bar **"))
    }

    @Test
    fun `should parse spec sample 392 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**(**foo)")

        /*
         * Expected HTML:
         * <p>**(**foo)</p>
         */
        parsed.assertEquals(paragraph("**(**foo)"))
    }

    @Test
    fun `should parse spec sample 393 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*(**foo**)*")

        /*
         * Expected HTML:
         * <p><em>(<strong>foo</strong>)</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("("), StrongEmphasis("**", Text("foo")), Text(")"))))
    }

    @Test
    fun `should parse spec sample 394 correctly {Emphasis and strong emphasis}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |**Gomphocarpus (*Gomphocarpus physocarpus*, syn.
                |*Asclepias physocarpa*)**
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><strong>Gomphocarpus (<em>Gomphocarpus physocarpus</em>, syn.
         * <em>Asclepias physocarpa</em>)</strong></p>
         */
        parsed.assertEquals(
            Paragraph(
                StrongEmphasis(
                    "**",
                    Text("Gomphocarpus ("),
                    Emphasis("*", Text("Gomphocarpus physocarpus")),
                    Text(", syn."),
                    SoftLineBreak,
                    Emphasis("*", Text("Asclepias physocarpa")),
                    Text(")"),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 395 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo \"*bar*\" foo**")

        /*
         * Expected HTML:
         * <p><strong>foo &quot;<em>bar</em>&quot; foo</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo \""), Emphasis("*", Text("bar")), Text("\" foo"))))
    }

    @Test
    fun `should parse spec sample 396 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo**bar")

        /*
         * Expected HTML:
         * <p><strong>foo</strong>bar</p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo")), Text("bar")))
    }

    @Test
    fun `should parse spec sample 397 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo bar __")

        /*
         * Expected HTML:
         * <p>__foo bar __</p>
         */
        parsed.assertEquals(paragraph("__foo bar __"))
    }

    @Test
    fun `should parse spec sample 398 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__(__foo)")

        /*
         * Expected HTML:
         * <p>__(__foo)</p>
         */
        parsed.assertEquals(paragraph("__(__foo)"))
    }

    @Test
    fun `should parse spec sample 399 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_(__foo__)_")

        /*
         * Expected HTML:
         * <p><em>(<strong>foo</strong>)</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("("), StrongEmphasis("__", Text("foo")), Text(")"))))
    }

    @Test
    fun `should parse spec sample 400 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo__bar")

        /*
         * Expected HTML:
         * <p>__foo__bar</p>
         */
        parsed.assertEquals(paragraph("__foo__bar"))
    }

    @Test
    fun `should parse spec sample 401 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__пристаням__стремятся")

        /*
         * Expected HTML:
         * <p>__пристаням__стремятся</p>
         */
        parsed.assertEquals(paragraph("__пристаням__стремятся"))
    }

    @Test
    fun `should parse spec sample 402 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo__bar__baz__")

        /*
         * Expected HTML:
         * <p><strong>foo__bar__baz</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("foo__bar__baz"))))
    }

    @Test
    fun `should parse spec sample 403 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__(bar)__.")

        /*
         * Expected HTML:
         * <p><strong>(bar)</strong>.</p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("(bar)")), Text(".")))
    }

    @Test
    fun `should parse spec sample 404 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo [bar](/url)*")

        /*
         * Expected HTML:
         * <p><em>foo <a href="/url">bar</a></em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("*", Text("foo "), Link(destination = "/url", title = null, Text("bar"))))
        )
    }

    @Test
    fun `should parse spec sample 405 correctly {Emphasis and strong emphasis}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |*foo
                |bar*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><em>foo
         * bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo"), SoftLineBreak, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 406 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo __bar__ baz_")

        /*
         * Expected HTML:
         * <p><em>foo <strong>bar</strong> baz</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("foo "), StrongEmphasis("__", Text("bar")), Text(" baz"))))
    }

    @Test
    fun `should parse spec sample 407 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo _bar_ baz_")

        /*
         * Expected HTML:
         * <p><em>foo <em>bar</em> baz</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("foo "), Emphasis("_", Text("bar")), Text(" baz"))))
    }

    @Test
    fun `should parse spec sample 408 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo_ bar_")

        /*
         * Expected HTML:
         * <p><em><em>foo</em> bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Emphasis("_", Text("foo")), Text(" bar"))))
    }

    @Test
    fun `should parse spec sample 409 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo *bar**")

        /*
         * Expected HTML:
         * <p><em>foo <em>bar</em></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo "), Emphasis("*", Text("bar")))))
    }

    @Test
    fun `should parse spec sample 410 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo **bar** baz*")

        /*
         * Expected HTML:
         * <p><em>foo <strong>bar</strong> baz</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo "), StrongEmphasis("**", Text("bar")), Text(" baz"))))
    }

    @Test
    fun `should parse spec sample 411 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo**bar**baz*")

        /*
         * Expected HTML:
         * <p><em>foo<strong>bar</strong>baz</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo"), StrongEmphasis("**", Text("bar")), Text("baz"))))
    }

    @Test
    fun `should parse spec sample 412 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo**bar*")

        /*
         * Expected HTML:
         * <p><em>foo**bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo**bar"))))
    }

    @Test
    fun `should parse spec sample 413 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("***foo** bar*")

        /*
         * Expected HTML:
         * <p><em><strong>foo</strong> bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", StrongEmphasis("**", Text("foo")), Text(" bar"))))
    }

    @Test
    fun `should parse spec sample 414 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo **bar***")

        /*
         * Expected HTML:
         * <p><em>foo <strong>bar</strong></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo "), StrongEmphasis("**", Text("bar")))))
    }

    @Test
    fun `should parse spec sample 415 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo**bar***")

        /*
         * Expected HTML:
         * <p><em>foo<strong>bar</strong></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo"), StrongEmphasis("**", Text("bar")))))
    }

    @Test
    fun `should parse spec sample 416 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo***bar***baz")

        /*
         * Expected HTML:
         * <p>foo<em><strong>bar</strong></em>baz</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), Emphasis("*", StrongEmphasis("**", Text("bar"))), Text("baz")))
    }

    @Test
    fun `should parse spec sample 417 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo******bar*********baz")

        /*
         * Expected HTML:
         * <p>foo<strong><strong><strong>bar</strong></strong></strong>***baz</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("foo"),
                StrongEmphasis("**", StrongEmphasis("**", StrongEmphasis("**", Text("bar")))),
                Text("***baz"),
            )
        )
    }

    @Test
    fun `should parse spec sample 418 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo **bar *baz* bim** bop*")

        /*
         * Expected HTML:
         * <p><em>foo <strong>bar <em>baz</em> bim</strong> bop</em></p>
         */
        parsed.assertEquals(
            Paragraph(
                Emphasis(
                    "*",
                    Text("foo "),
                    StrongEmphasis("**", Text("bar "), Emphasis("*", Text("baz")), Text(" bim")),
                    Text(" bop"),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 419 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo [*bar*](/url)*")

        /*
         * Expected HTML:
         * <p><em>foo <a href="/url"><em>bar</em></a></em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("*", Text("foo "), Link(destination = "/url", title = null, Emphasis("*", Text("bar")))))
        )
    }

    @Test
    fun `should parse spec sample 420 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("** is not an empty emphasis")

        /*
         * Expected HTML:
         * <p>** is not an empty emphasis</p>
         */
        parsed.assertEquals(paragraph("** is not an empty emphasis"))
    }

    @Test
    fun `should parse spec sample 421 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**** is not an empty strong emphasis")

        /*
         * Expected HTML:
         * <p>**** is not an empty strong emphasis</p>
         */
        parsed.assertEquals(paragraph("**** is not an empty strong emphasis"))
    }

    @Test
    fun `should parse spec sample 422 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo [bar](/url)**")

        /*
         * Expected HTML:
         * <p><strong>foo <a href="/url">bar</a></strong></p>
         */
        parsed.assertEquals(
            Paragraph(StrongEmphasis("**", Text("foo "), Link(destination = "/url", title = null, Text("bar"))))
        )
    }

    @Test
    fun `should parse spec sample 423 correctly {Emphasis and strong emphasis}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |**foo
                |bar**
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><strong>foo
         * bar</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo"), SoftLineBreak, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 424 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo _bar_ baz__")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em> baz</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("foo "), Emphasis("_", Text("bar")), Text(" baz"))))
    }

    @Test
    fun `should parse spec sample 425 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo __bar__ baz__")

        /*
         * Expected HTML:
         * <p><strong>foo <strong>bar</strong> baz</strong></p>
         */
        parsed.assertEquals(
            Paragraph(StrongEmphasis("__", Text("foo "), StrongEmphasis("__", Text("bar")), Text(" baz")))
        )
    }

    @Test
    fun `should parse spec sample 426 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("____foo__ bar__")

        /*
         * Expected HTML:
         * <p><strong><strong>foo</strong> bar</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", StrongEmphasis("__", Text("foo")), Text(" bar"))))
    }

    @Test
    fun `should parse spec sample 427 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo **bar****")

        /*
         * Expected HTML:
         * <p><strong>foo <strong>bar</strong></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo "), StrongEmphasis("**", Text("bar")))))
    }

    @Test
    fun `should parse spec sample 428 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo *bar* baz**")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em> baz</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo "), Emphasis("*", Text("bar")), Text(" baz"))))
    }

    @Test
    fun `should parse spec sample 429 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo*bar*baz**")

        /*
         * Expected HTML:
         * <p><strong>foo<em>bar</em>baz</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo"), Emphasis("*", Text("bar")), Text("baz"))))
    }

    @Test
    fun `should parse spec sample 430 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("***foo* bar**")

        /*
         * Expected HTML:
         * <p><strong><em>foo</em> bar</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Emphasis("*", Text("foo")), Text(" bar"))))
    }

    @Test
    fun `should parse spec sample 431 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo *bar***")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo "), Emphasis("*", Text("bar")))))
    }

    @Test
    fun `should parse spec sample 432 correctly {Emphasis and strong emphasis}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |**foo *bar **baz**
                |bim* bop**
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar <strong>baz</strong>
         * bim</em> bop</strong></p>
         */
        parsed.assertEquals(
            Paragraph(
                StrongEmphasis(
                    "**",
                    Text("foo "),
                    Emphasis("*", Text("bar "), StrongEmphasis("**", Text("baz")), SoftLineBreak, Text("bim")),
                    Text(" bop"),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 433 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo [*bar*](/url)**")

        /*
         * Expected HTML:
         * <p><strong>foo <a href="/url"><em>bar</em></a></strong></p>
         */
        parsed.assertEquals(
            Paragraph(
                StrongEmphasis("**", Text("foo "), Link(destination = "/url", title = null, Emphasis("*", Text("bar"))))
            )
        )
    }

    @Test
    fun `should parse spec sample 434 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__ is not an empty emphasis")

        /*
         * Expected HTML:
         * <p>__ is not an empty emphasis</p>
         */
        parsed.assertEquals(paragraph("__ is not an empty emphasis"))
    }

    @Test
    fun `should parse spec sample 435 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("____ is not an empty strong emphasis")

        /*
         * Expected HTML:
         * <p>____ is not an empty strong emphasis</p>
         */
        parsed.assertEquals(paragraph("____ is not an empty strong emphasis"))
    }

    @Test
    fun `should parse spec sample 436 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo ***")

        /*
         * Expected HTML:
         * <p>foo ***</p>
         */
        parsed.assertEquals(paragraph("foo ***"))
    }

    @Test
    fun `should parse spec sample 437 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo *\\**")

        /*
         * Expected HTML:
         * <p>foo <em>*</em></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), Emphasis("*", Text("*"))))
    }

    @Test
    fun `should parse spec sample 438 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo *_*")

        /*
         * Expected HTML:
         * <p>foo <em>_</em></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), Emphasis("*", Text("_"))))
    }

    @Test
    fun `should parse spec sample 439 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo *****")

        /*
         * Expected HTML:
         * <p>foo *****</p>
         */
        parsed.assertEquals(paragraph("foo *****"))
    }

    @Test
    fun `should parse spec sample 440 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo **\\***")

        /*
         * Expected HTML:
         * <p>foo <strong>*</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), StrongEmphasis("**", Text("*"))))
    }

    @Test
    fun `should parse spec sample 441 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo **_**")

        /*
         * Expected HTML:
         * <p>foo <strong>_</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), StrongEmphasis("**", Text("_"))))
    }

    @Test
    fun `should parse spec sample 442 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo*")

        /*
         * Expected HTML:
         * <p>*<em>foo</em></p>
         */
        parsed.assertEquals(Paragraph(Text("*"), Emphasis("*", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 443 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo**")

        /*
         * Expected HTML:
         * <p><em>foo</em>*</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo")), Text("*")))
    }

    @Test
    fun `should parse spec sample 444 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("***foo**")

        /*
         * Expected HTML:
         * <p>*<strong>foo</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("*"), StrongEmphasis("**", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 445 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("****foo*")

        /*
         * Expected HTML:
         * <p>***<em>foo</em></p>
         */
        parsed.assertEquals(Paragraph(Text("***"), Emphasis("*", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 446 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo***")

        /*
         * Expected HTML:
         * <p><strong>foo</strong>*</p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo")), Text("*")))
    }

    @Test
    fun `should parse spec sample 447 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo****")

        /*
         * Expected HTML:
         * <p><em>foo</em>***</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo")), Text("***")))
    }

    @Test
    fun `should parse spec sample 448 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo ___")

        /*
         * Expected HTML:
         * <p>foo ___</p>
         */
        parsed.assertEquals(paragraph("foo ___"))
    }

    @Test
    fun `should parse spec sample 449 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo _\\__")

        /*
         * Expected HTML:
         * <p>foo <em>_</em></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), Emphasis("_", Text("_"))))
    }

    @Test
    fun `should parse spec sample 450 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo _*_")

        /*
         * Expected HTML:
         * <p>foo <em>*</em></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), Emphasis("_", Text("*"))))
    }

    @Test
    fun `should parse spec sample 451 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo _____")

        /*
         * Expected HTML:
         * <p>foo _____</p>
         */
        parsed.assertEquals(paragraph("foo _____"))
    }

    @Test
    fun `should parse spec sample 452 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo __\\___")

        /*
         * Expected HTML:
         * <p>foo <strong>_</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), StrongEmphasis("__", Text("_"))))
    }

    @Test
    fun `should parse spec sample 453 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("foo __*__")

        /*
         * Expected HTML:
         * <p>foo <strong>*</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), StrongEmphasis("__", Text("*"))))
    }

    @Test
    fun `should parse spec sample 454 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo_")

        /*
         * Expected HTML:
         * <p>_<em>foo</em></p>
         */
        parsed.assertEquals(Paragraph(Text("_"), Emphasis("_", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 455 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo__")

        /*
         * Expected HTML:
         * <p><em>foo</em>_</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("foo")), Text("_")))
    }

    @Test
    fun `should parse spec sample 456 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("___foo__")

        /*
         * Expected HTML:
         * <p>_<strong>foo</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("_"), StrongEmphasis("__", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 457 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("____foo_")

        /*
         * Expected HTML:
         * <p>___<em>foo</em></p>
         */
        parsed.assertEquals(Paragraph(Text("___"), Emphasis("_", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 458 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo___")

        /*
         * Expected HTML:
         * <p><strong>foo</strong>_</p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("foo")), Text("_")))
    }

    @Test
    fun `should parse spec sample 459 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo____")

        /*
         * Expected HTML:
         * <p><em>foo</em>___</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("foo")), Text("___")))
    }

    @Test
    fun `should parse spec sample 460 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo**")

        /*
         * Expected HTML:
         * <p><strong>foo</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 461 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*_foo_*")

        /*
         * Expected HTML:
         * <p><em><em>foo</em></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Emphasis("_", Text("foo")))))
    }

    @Test
    fun `should parse spec sample 462 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo__")

        /*
         * Expected HTML:
         * <p><strong>foo</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 462+1b correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_*foo _bar_*_")

        /*
         * Expected HTML:
         * <p><em><em>foo <em>bar</em></em></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Emphasis("*", Text("foo "), Emphasis("_", Text("bar"))))))
    }

    @Test
    fun `should parse spec sample 462+1c correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo _bar___")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("foo "), Emphasis("_", Text("bar")))))
    }

    @Test
    fun `should parse spec sample 462+1d correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_*foo _bar_ a*_")

        /*
         * Expected HTML:
         * <p><em><em>foo <em>bar</em> a</em></em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("_", Emphasis("*", Text("foo "), Emphasis("_", Text("bar")), Text(" a"))))
        )
    }

    @Test
    fun `should parse spec sample 462+1e correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__foo _bar_ a__")

        /*
         * Expected HTML:
         * <p><strong>foo <em>bar</em> a</strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", Text("foo "), Emphasis("_", Text("bar")), Text(" a"))))
    }

    @Test
    fun `should parse spec sample 462+1f correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_*foo *bar* a*_")

        /*
         * Expected HTML:
         * <p><em><em>foo <em>bar</em> a</em></em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("_", Emphasis("*", Text("foo "), Emphasis("*", Text("bar")), Text(" a"))))
        )
    }

    @Test
    fun `should parse spec sample 463 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_*foo*_")

        /*
         * Expected HTML:
         * <p><em><em>foo</em></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Emphasis("*", Text("foo")))))
    }

    @Test
    fun `should parse spec sample 464 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("****foo****")

        /*
         * Expected HTML:
         * <p><strong><strong>foo</strong></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", StrongEmphasis("**", Text("foo")))))
    }

    @Test
    fun `should parse spec sample 465 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("____foo____")

        /*
         * Expected HTML:
         * <p><strong><strong>foo</strong></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("__", StrongEmphasis("__", Text("foo")))))
    }

    @Test
    fun `should parse spec sample 466 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("******foo******")

        /*
         * Expected HTML:
         * <p><strong><strong><strong>foo</strong></strong></strong></p>
         */
        parsed.assertEquals(Paragraph(StrongEmphasis("**", StrongEmphasis("**", StrongEmphasis("**", Text("foo"))))))
    }

    @Test
    fun `should parse spec sample 467 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("***foo***")

        /*
         * Expected HTML:
         * <p><em><strong>foo</strong></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", StrongEmphasis("**", Text("foo")))))
    }

    @Test
    fun `should parse spec sample 468 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_____foo_____")

        /*
         * Expected HTML:
         * <p><em><strong><strong>foo</strong></strong></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", StrongEmphasis("__", StrongEmphasis("__", Text("foo"))))))
    }

    @Test
    fun `should parse spec sample 469 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo _bar* baz_")

        /*
         * Expected HTML:
         * <p><em>foo _bar</em> baz_</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo _bar")), Text(" baz_")))
    }

    @Test
    fun `should parse spec sample 470 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo __bar *baz bim__ bam*")

        /*
         * Expected HTML:
         * <p><em>foo <strong>bar *baz bim</strong> bam</em></p>
         */
        parsed.assertEquals(
            Paragraph(Emphasis("*", Text("foo "), StrongEmphasis("__", Text("bar *baz bim")), Text(" bam")))
        )
    }

    @Test
    fun `should parse spec sample 471 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**foo **bar baz**")

        /*
         * Expected HTML:
         * <p>**foo <strong>bar baz</strong></p>
         */
        parsed.assertEquals(Paragraph(Text("**foo "), StrongEmphasis("**", Text("bar baz"))))
    }

    @Test
    fun `should parse spec sample 472 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*foo *bar baz*")

        /*
         * Expected HTML:
         * <p>*foo <em>bar baz</em></p>
         */
        parsed.assertEquals(Paragraph(Text("*foo "), Emphasis("*", Text("bar baz"))))
    }

    @Test
    fun `should parse spec sample 473 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*[bar*](/url)")

        /*
         * Expected HTML:
         * <p>*<a href="/url">bar*</a></p>
         */
        parsed.assertEquals(Paragraph(Text("*"), Link(destination = "/url", title = null, Text("bar*"))))
    }

    @Test
    fun `should parse spec sample 474 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_foo [bar_](/url)")

        /*
         * Expected HTML:
         * <p>_foo <a href="/url">bar_</a></p>
         */
        parsed.assertEquals(Paragraph(Text("_foo "), Link(destination = "/url", title = null, Text("bar_"))))
    }

    @Test
    fun `should parse spec sample 475 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*<img src=\"foo\" title=\"*\"/>")

        /*
         * Expected HTML:
         * <p>*<img src="foo" title="*"/></p>
         */
        parsed.assertEquals(Paragraph(Text("*"), HtmlInline("<img src=\"foo\" title=\"*\"/>")))
    }

    @Test
    fun `should parse spec sample 476 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**<a href=\"**\">")

        /*
         * Expected HTML:
         * <p>**<a href="**"></p>
         */
        parsed.assertEquals(Paragraph(Text("**"), HtmlInline("<a href=\"**\">")))
    }

    @Test
    fun `should parse spec sample 477 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__<a href=\"__\">")

        /*
         * Expected HTML:
         * <p>__<a href="__"></p>
         */
        parsed.assertEquals(Paragraph(Text("__"), HtmlInline("<a href=\"__\">")))
    }

    @Test
    fun `should parse spec sample 478 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("*a `*`*")

        /*
         * Expected HTML:
         * <p><em>a <code>*</code></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("a "), Code("*"))))
    }

    @Test
    fun `should parse spec sample 479 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("_a `_`_")

        /*
         * Expected HTML:
         * <p><em>a <code>_</code></em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("_", Text("a "), Code("_"))))
    }

    @Test
    fun `should parse spec sample 480 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("**a<https://foo.bar/?q=**>")

        /*
         * Expected HTML:
         * <p>**a<a href="https://foo.bar/?q=**">https://foo.bar/?q=**</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("**a"),
                Link(destination = "https://foo.bar/?q=**", title = null, Text("https://foo.bar/?q=**")),
            )
        )
    }

    @Test
    fun `should parse spec sample 481 correctly {Emphasis and strong emphasis}`() {
        val parsed = processor.processMarkdownDocument("__a<https://foo.bar/?q=__>")

        /*
         * Expected HTML:
         * <p>__a<a href="https://foo.bar/?q=__">https://foo.bar/?q=__</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("__a"),
                Link(destination = "https://foo.bar/?q=__", title = null, Text("https://foo.bar/?q=__")),
            )
        )
    }

    @Test
    fun `should parse spec sample 482 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](/uri \"title\")")

        /*
         * Expected HTML:
         * <p><a href="/uri" title="title">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = "title", Text("link"))))
    }

    @Test
    fun `should parse spec sample 483 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](/uri)")

        /*
         * Expected HTML:
         * <p><a href="/uri">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 484 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[](./target.md)")

        /*
         * Expected HTML:
         * <p><a href="./target.md"></a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "./target.md", title = null)))
    }

    @Test
    fun `should parse spec sample 485 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link]()")

        /*
         * Expected HTML:
         * <p><a href="">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 486 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](<>)")

        /*
         * Expected HTML:
         * <p><a href="">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 487 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[]()")

        /*
         * Expected HTML:
         * <p><a href=""></a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "", title = null)))
    }

    @Test
    fun `should parse spec sample 488 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](/my uri)")

        /*
         * Expected HTML:
         * <p>[link](/my uri)</p>
         */
        parsed.assertEquals(Paragraph(Text("[link](/my uri)")))
    }

    @Test
    fun `should parse spec sample 489 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](</my uri>)")

        /*
         * Expected HTML:
         * <p><a href="/my%20uri">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/my uri", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 490 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link](foo
                |bar)
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[link](foo
         * bar)</p>
         */
        parsed.assertEquals(Paragraph(Text("[link](foo"), SoftLineBreak, Text("bar)")))
    }

    @Test
    fun `should parse spec sample 491 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link](<foo
                |bar>)
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[link](<foo
         * bar>)</p>
         */
        parsed.assertEquals(Paragraph(Text("[link]("), HtmlInline("<foo\nbar>"), Text(")")))
    }

    @Test
    fun `should parse spec sample 492 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[a](<b)c>)")

        /*
         * Expected HTML:
         * <p><a href="b)c">a</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "b)c", title = null, Text("a"))))
    }

    @Test
    fun `should parse spec sample 493 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](<foo\\>)")

        /*
         * Expected HTML:
         * <p>[link](&lt;foo&gt;)</p>
         */
        parsed.assertEquals(paragraph("[link](<foo>)"))
    }

    @Test
    fun `should parse spec sample 494 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[a](<b)c
                |[a](<b)c>
                |[a](<b>c)
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[a](&lt;b)c
         * [a](&lt;b)c&gt;
         * [a](<b>c)</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text(content = "[a](<b)c"),
                SoftLineBreak,
                Text(content = "[a](<b)c>"),
                SoftLineBreak,
                Text(content = "[a]("),
                HtmlInline(content = "<b>"),
                Text(content = "c)"),
            )
        )
    }

    @Test
    fun `should parse spec sample 495 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](\\(foo\\))")

        /*
         * Expected HTML:
         * <p><a href="(foo)">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "(foo)", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 496 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](foo(and(bar)))")

        /*
         * Expected HTML:
         * <p><a href="foo(and(bar))">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "foo(and(bar))", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 497 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](foo(and(bar))")

        /*
         * Expected HTML:
         * <p>[link](foo(and(bar))</p>
         */
        parsed.assertEquals(paragraph("[link](foo(and(bar))"))
    }

    @Test
    fun `should parse spec sample 498 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](foo\\(and\\(bar\\))")

        /*
         * Expected HTML:
         * <p><a href="foo(and(bar)">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "foo(and(bar)", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 499 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](<foo(and(bar)>)")

        /*
         * Expected HTML:
         * <p><a href="foo(and(bar)">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "foo(and(bar)", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 500 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](foo\\)\\:)")

        /*
         * Expected HTML:
         * <p><a href="foo):">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "foo):", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 501 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link](#fragment)
                |
                |[link](https://example.com#fragment)
                |
                |[link](https://example.com?foo=3#frag)
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="#fragment">link</a></p>
         * <p><a href="https://example.com#fragment">link</a></p>
         * <p><a href="https://example.com?foo=3#frag">link</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "#fragment", title = null, Text("link"))),
            Paragraph(Link(destination = "https://example.com#fragment", title = null, Text("link"))),
            Paragraph(Link(destination = "https://example.com?foo=3#frag", title = null, Text("link"))),
        )
    }

    @Test
    fun `should parse spec sample 502 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](foo\\bar)")

        /*
         * Expected HTML:
         * <p><a href="foo%5Cbar">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "foo\\bar", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 503 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](foo%20b&auml;)")

        /*
         * Expected HTML:
         * <p><a href="foo%20b%C3%A4">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "foo%20bä", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 504 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](\"title\")")

        /*
         * Expected HTML:
         * <p><a href="%22title%22">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "\"title\"", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 505 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link](/url "title")
                |[link](/url 'title')
                |[link](/url (title))
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">link</a>
         * <a href="/url" title="title">link</a>
         * <a href="/url" title="title">link</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(destination = "/url", title = "title", Text("link")),
                SoftLineBreak,
                Link(destination = "/url", title = "title", Text("link")),
                SoftLineBreak,
                Link(destination = "/url", title = "title", Text("link")),
            )
        )
    }

    @Test
    fun `should parse spec sample 506 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](/url \"title \\\"&quot;\")")

        /*
         * Expected HTML:
         * <p><a href="/url" title="title &quot;&quot;">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title \"\"", Text("link"))))
    }

    @Test
    fun `should parse spec sample 507 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](/url \"title\")")

        /*
         * Expected HTML:
         * <p><a href="/url%C2%A0%22title%22">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url \"title\"", title = null, Text("link"))))
    }

    @Test
    fun `should parse spec sample 508 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](/url \"title \"and\" title\")")

        /*
         * Expected HTML:
         * <p>[link](/url &quot;title &quot;and&quot; title&quot;)</p>
         */
        parsed.assertEquals(paragraph("[link](/url \"title \"and\" title\")"))
    }

    @Test
    fun `should parse spec sample 509 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link](/url 'title \"and\" title')")

        /*
         * Expected HTML:
         * <p><a href="/url" title="title &quot;and&quot; title">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title \"and\" title", Text("link"))))
    }

    @Test
    fun `should parse spec sample 510 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link](   /uri
                |  "title"  )
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri" title="title">link</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = "title", Text("link"))))
    }

    @Test
    fun `should parse spec sample 511 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link] (/uri)")

        /*
         * Expected HTML:
         * <p>[link] (/uri)</p>
         */
        parsed.assertEquals(paragraph("[link] (/uri)"))
    }

    @Test
    fun `should parse spec sample 512 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link [foo [bar]]](/uri)")

        /*
         * Expected HTML:
         * <p><a href="/uri">link [foo [bar]]</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("link [foo [bar]]"))))
    }

    @Test
    fun `should parse spec sample 513 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link] bar](/uri)")

        /*
         * Expected HTML:
         * <p>[link] bar](/uri)</p>
         */
        parsed.assertEquals(paragraph("[link] bar](/uri)"))
    }

    @Test
    fun `should parse spec sample 514 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link [bar](/uri)")

        /*
         * Expected HTML:
         * <p>[link <a href="/uri">bar</a></p>
         */
        parsed.assertEquals(Paragraph(Text("[link "), Link(destination = "/uri", title = null, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 515 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link \\[bar](/uri)")

        /*
         * Expected HTML:
         * <p><a href="/uri">link [bar</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("link [bar"))))
    }

    @Test
    fun `should parse spec sample 516 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[link *foo **bar** `#`*](/uri)")

        /*
         * Expected HTML:
         * <p><a href="/uri">link <em>foo <strong>bar</strong> <code>#</code></em></a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(
                    destination = "/uri",
                    title = null,
                    Text("link "),
                    Emphasis("*", Text("foo "), StrongEmphasis("**", Text("bar")), Text(" "), Code("#")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 517 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[![moon](moon.jpg)](/uri)")

        /*
         * Expected HTML:
         * <p><a href="/uri"><img src="moon.jpg" alt="moon" /></a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(
                    destination = "/uri",
                    title = null,
                    Image(source = "moon.jpg", alt = "moon", title = null, Text("moon")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 518 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[foo [bar](/uri)](/uri)")

        /*
         * Expected HTML:
         * <p>[foo <a href="/uri">bar</a>](/uri)</p>
         */
        parsed.assertEquals(
            Paragraph(Text("[foo "), Link(destination = "/uri", title = null, Text("bar")), Text("](/uri)"))
        )
    }

    @Test
    fun `should parse spec sample 519 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[foo *[bar [baz](/uri)](/uri)*](/uri)")

        /*
         * Expected HTML:
         * <p>[foo <em>[bar <a href="/uri">baz</a>](/uri)</em>](/uri)</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("[foo "),
                Emphasis("*", Text("[bar "), Link(destination = "/uri", title = null, Text("baz")), Text("](/uri)")),
                Text("](/uri)"),
            )
        )
    }

    @Test
    fun `should parse spec sample 520 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("![[[foo](uri1)](uri2)](uri3)")

        /*
         * Expected HTML:
         * <p><img src="uri3" alt="[foo](uri2)" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "uri3",
                    alt = "[foo](uri2)",
                    title = null,
                    Text("["),
                    Link(destination = "uri1", title = null, Text("foo")),
                    Text("](uri2)"),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 521 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("*[foo*](/uri)")

        /*
         * Expected HTML:
         * <p>*<a href="/uri">foo*</a></p>
         */
        parsed.assertEquals(Paragraph(Text("*"), Link(destination = "/uri", title = null, Text("foo*"))))
    }

    @Test
    fun `should parse spec sample 522 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[foo *bar](baz*)")

        /*
         * Expected HTML:
         * <p><a href="baz*">foo *bar</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "baz*", title = null, Text("foo *bar"))))
    }

    @Test
    fun `should parse spec sample 523 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("*foo [bar* baz]")

        /*
         * Expected HTML:
         * <p><em>foo [bar</em> baz]</p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo [bar")), Text(" baz]")))
    }

    @Test
    fun `should parse spec sample 524 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[foo <bar attr=\"](baz)\">")

        /*
         * Expected HTML:
         * <p>[foo <bar attr="](baz)"></p>
         */
        parsed.assertEquals(Paragraph(Text("[foo "), HtmlInline("<bar attr=\"](baz)\">")))
    }

    @Test
    fun `should parse spec sample 525 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[foo`](/uri)`")

        /*
         * Expected HTML:
         * <p>[foo<code>](/uri)</code></p>
         */
        parsed.assertEquals(Paragraph(Text("[foo"), Code("](/uri)")))
    }

    @Test
    fun `should parse spec sample 526 correctly {Links}`() {
        val parsed = processor.processMarkdownDocument("[foo<https://example.com/?search=](uri)>")

        /*
         * Expected HTML:
         * <p>[foo<a href="https://example.com/?search=%5D(uri)">https://example.com/?search=](uri)</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("[foo"),
                Link(
                    destination = "https://example.com/?search=](uri)",
                    title = null,
                    Text("https://example.com/?search=](uri)"),
                ),
            )
        )
    }

    @Test
    fun `should parse spec sample 527 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][bar]
                |
                |[bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 528 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link [foo [bar]]][ref]
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri">link [foo [bar]]</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("link [foo [bar]]"))))
    }

    @Test
    fun `should parse spec sample 529 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link \[bar][ref]
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri">link [bar</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("link [bar"))))
    }

    @Test
    fun `should parse spec sample 530 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[link *foo **bar** `#`*][ref]
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri">link <em>foo <strong>bar</strong> <code>#</code></em></a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(
                    destination = "/uri",
                    title = null,
                    Text("link "),
                    Emphasis("*", Text("foo "), StrongEmphasis("**", Text("bar")), Text(" "), Code("#")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 531 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[![moon](moon.jpg)][ref]
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri"><img src="moon.jpg" alt="moon" /></a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(
                    destination = "/uri",
                    title = null,
                    Image(source = "moon.jpg", alt = "moon", title = null, Text("moon")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 532 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo [bar](/uri)][ref]
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo <a href="/uri">bar</a>]<a href="/uri">ref</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("[foo "),
                Link(destination = "/uri", title = null, Text("bar")),
                Text("]"),
                Link(destination = "/uri", title = null, Text("ref")),
            )
        )
    }

    @Test
    fun `should parse spec sample 533 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo *bar [baz][ref]*][ref]
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo <em>bar <a href="/uri">baz</a></em>]<a href="/uri">ref</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("[foo "),
                Emphasis("*", Text("bar "), Link("/uri", null, Text("baz"))),
                Text("]"),
                Link(destination = "/uri", title = null, Text("ref")),
            )
        )
    }

    @Test
    fun `should parse spec sample 534 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |*[foo*][ref]
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>*<a href="/uri">foo*</a></p>
         */
        parsed.assertEquals(Paragraph(Text("*"), Link(destination = "/uri", title = null, Text("foo*"))))
    }

    @Test
    fun `should parse spec sample 535 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo *bar][ref]*
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri">foo *bar</a>*</p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("foo *bar")), Text("*")))
    }

    @Test
    fun `should parse spec sample 536 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo <bar attr="][ref]">
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo <bar attr="][ref]"></p>
         */
        parsed.assertEquals(Paragraph(Text(content = "[foo "), HtmlInline("<bar attr=\"][ref]\">")))
    }

    @Test
    fun `should parse spec sample 537 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo`][ref]`
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo<code>][ref]</code></p>
         */
        parsed.assertEquals(Paragraph(Text(content = "[foo"), Code("][ref]")))
    }

    @Test
    fun `should parse spec sample 538 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo<https://example.com/?search=][ref]>
                |
                |[ref]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo<a href="https://example.com/?search=%5D%5Bref%5D">https://example.com/?search=][ref]</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Text(content = "[foo"),
                Link(
                    destination = "https://example.com/?search=][ref]",
                    title = null,
                    Text("https://example.com/?search=][ref]"),
                ),
            )
        )
    }

    @Test
    fun `should parse spec sample 539 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][BaR]
                |
                |[bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 540 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[ẞ]
                |
                |[SS]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url">ẞ</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = null, Text("ẞ"))))
    }

    @Test
    fun `should parse spec sample 541 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[Foo
                |  bar]: /url
                |
                |[Baz][Foo bar]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url">Baz</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = null, Text("Baz"))))
    }

    @Test
    fun `should parse spec sample 542 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo] [bar]
                |
                |[bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo] <a href="/url" title="title">bar</a></p>
         */
        parsed.assertEquals(Paragraph(Text("[foo] "), Link(destination = "/url", title = "title", Text("bar"))))
    }

    @Test
    fun `should parse spec sample 543 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]
                |[bar]
                |
                |[bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo]
         * <a href="/url" title="title">bar</a></p>
         */
        parsed.assertEquals(
            Paragraph(Text("[foo]"), SoftLineBreak, Link(destination = "/url", title = "title", Text("bar")))
        )
    }

    @Test
    fun `should parse spec sample 544 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]: /url1
                |
                |[foo]: /url2
                |
                |[bar][foo]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url1">bar</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url1", title = null, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 545 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[bar][foo\!]
                |
                |[foo!]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[bar][foo!]</p>
         */
        parsed.assertEquals(paragraph("[bar][foo!]"))
    }

    @Test
    fun `should parse spec sample 546 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][ref[]
                |
                |[ref[]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo][ref[]</p>
         * <p>[ref[]: /uri</p>
         */
        parsed.assertEquals(paragraph("[foo][ref[]"), paragraph("[ref[]: /uri"))
    }

    @Test
    fun `should parse spec sample 547 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][ref[bar]]
                |
                |[ref[bar]]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo][ref[bar]]</p>
         * <p>[ref[bar]]: /uri</p>
         */
        parsed.assertEquals(paragraph("[foo][ref[bar]]"), paragraph("[ref[bar]]: /uri"))
    }

    @Test
    fun `should parse spec sample 548 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[[[foo]]]
                |
                |[[[foo]]]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[[[foo]]]</p>
         * <p>[[[foo]]]: /url</p>
         */
        parsed.assertEquals(paragraph("[[[foo]]]"), paragraph("[[[foo]]]: /url"))
    }

    @Test
    fun `should parse spec sample 549 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][ref\[]
                |
                |[ref\[]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 550 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[bar\\]: /uri
                |
                |[bar\\]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/uri">bar\</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/uri", title = null, Text("bar\\"))))
    }

    @Test
    fun `should parse spec sample 551 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[]
                |
                |[]: /uri
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[]</p>
         * <p>[]: /uri</p>
         */
        parsed.assertEquals(paragraph("[]"), paragraph("[]: /uri"))
    }

    @Test
    fun `should parse spec sample 552 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
            |[
            | ]
            |
            |[
            | ]: /uri
            """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[
         * ]</p>
         * <p>[
         * ]: /uri</p>
         */
        parsed.assertEquals(
            Paragraph(Text("["), SoftLineBreak, Text("]")),
            Paragraph(Text("["), SoftLineBreak, Text("]: /uri")),
        )
    }

    @Test
    fun `should parse spec sample 553 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 554 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[*foo* bar][]
                |
                |[*foo* bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title"><em>foo</em> bar</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "/url", title = "title", Emphasis("*", Text("foo")), Text(" bar")))
        )
    }

    @Test
    fun `should parse spec sample 555 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[Foo][]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">Foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title", Text("Foo"))))
    }

    @Test
    fun `should parse spec sample 556 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo] 
                |[]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">foo</a>
         * []</p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(destination = "/url", title = "title", Text("foo")),
                Text(""), // This looks wrong but apparently is correct
                SoftLineBreak,
                Text("[]"),
            )
        )
    }

    @Test
    fun `should parse spec sample 557 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 558 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[*foo* bar]
                |
                |[*foo* bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title"><em>foo</em> bar</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "/url", title = "title", Emphasis("*", Text("foo")), Text(" bar")))
        )
    }

    @Test
    fun `should parse spec sample 559 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[[*foo* bar]]
                |
                |[*foo* bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[<a href="/url" title="title"><em>foo</em> bar</a>]</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("["),
                Link(destination = "/url", title = "title", Emphasis("*", Text("foo")), Text(" bar")),
                Text("]"),
            )
        )
    }

    @Test
    fun `should parse spec sample 560 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[[bar [foo]
                |
                |[foo]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[[bar <a href="/url">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Text("[[bar "), Link(destination = "/url", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 561 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[Foo]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url" title="title">Foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = "title", Text("Foo"))))
    }

    @Test
    fun `should parse spec sample 562 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo] bar
                |
                |[foo]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url">foo</a> bar</p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url", title = null, Text("foo")), Text(" bar")))
    }

    @Test
    fun `should parse spec sample 563 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |\[foo]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo]</p>
         */
        parsed.assertEquals(paragraph("[foo]"))
    }

    @Test
    fun `should parse spec sample 564 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo*]: /url
                |
                |*[foo*]
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>*<a href="/url">foo*</a></p>
         */
        parsed.assertEquals(Paragraph(Text("*"), Link(destination = "/url", title = null, Text("foo*"))))
    }

    @Test
    fun `should parse spec sample 565 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][bar]
                |
                |[foo]: /url1
                |[bar]: /url2
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url2">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url2", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 566 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][]
                |
                |[foo]: /url1
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url1">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url1", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 567 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo]()
                |
                |[foo]: /url1
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 568 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo](not a link)
                |
                |[foo]: /url1
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url1">foo</a>(not a link)</p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "/url1", title = null, Text("foo")), Text("(not a link)")))
    }

    @Test
    fun `should parse spec sample 569 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][bar][baz]
                |
                |[baz]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo]<a href="/url">bar</a></p>
         */
        parsed.assertEquals(Paragraph(Text("[foo]"), Link(destination = "/url", title = null, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 570 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][bar][baz]
                |
                |[baz]: /url1
                |[bar]: /url2
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="/url2">foo</a><a href="/url1">baz</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(destination = "/url2", title = null, Text("foo")),
                Link(destination = "/url1", title = null, Text("baz")),
            )
        )
    }

    @Test
    fun `should parse spec sample 571 correctly {Links}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |[foo][bar][baz]
                |
                |[baz]: /url1
                |[foo]: /url2
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>[foo]<a href="/url1">bar</a></p>
         */
        parsed.assertEquals(Paragraph(Text("[foo]"), Link(destination = "/url1", title = null, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 572 correctly {Images}`() {
        val parsed = processor.processMarkdownDocument("![foo](/url \"title\")")

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo" title="title" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "foo", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 573 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo *bar*]
                |
                |[foo *bar*]: train.jpg "train & tracks"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "train.jpg",
                    alt = "foo bar",
                    title = "train & tracks",
                    Text("foo "),
                    Emphasis("*", Text("bar")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 574 correctly {Images}`() {
        val parsed = processor.processMarkdownDocument("![foo ![bar](/url)](/url2)")

        /*
         * Expected HTML:
         * <p><img src="/url2" alt="foo bar" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "/url2",
                    alt = "foo bar",
                    title = null,
                    Text("foo "),
                    Image(source = "/url", alt = "bar", title = null, Text("bar")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 575 correctly {Images}`() {
        val parsed = processor.processMarkdownDocument("![foo [bar](/url)](/url2)")

        /*
         * Expected HTML:
         * <p><img src="/url2" alt="foo bar" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "/url2",
                    alt = "foo bar",
                    title = null,
                    Text("foo "),
                    Link(destination = "/url", title = null, Text("bar")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 576 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo *bar*][]
                |
                |[foo *bar*]: train.jpg "train & tracks"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "train.jpg",
                    alt = "foo bar",
                    title = "train & tracks",
                    Text("foo "),
                    Emphasis("*", Text("bar")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 577 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo *bar*][foobar]
                |
                |[FOOBAR]: train.jpg "train & tracks"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(
                    source = "train.jpg",
                    alt = "foo bar",
                    title = "train & tracks",
                    Text("foo "),
                    Emphasis("*", Text("bar")),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 578 correctly {Images}`() {
        val parsed = processor.processMarkdownDocument("![foo](train.jpg)")

        /*
         * Expected HTML:
         * <p><img src="train.jpg" alt="foo" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "train.jpg", alt = "foo", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 579 correctly {Images}`() {
        val parsed = processor.processMarkdownDocument("My ![foo bar](/path/to/train.jpg  \"title\"   )")

        /*
         * Expected HTML:
         * <p>My <img src="/path/to/train.jpg" alt="foo bar" title="title" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("My "),
                Image(source = "/path/to/train.jpg", alt = "foo bar", title = "title", Text("foo bar")),
            )
        )
    }

    @Test
    fun `should parse spec sample 580 correctly {Images}`() {
        val parsed = processor.processMarkdownDocument("![foo](<url>)")

        /*
         * Expected HTML:
         * <p><img src="url" alt="foo" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "url", alt = "foo", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 581 correctly {Images}`() {
        val parsed = processor.processMarkdownDocument("![](/url)")

        /*
         * Expected HTML:
         * <p><img src="/url" alt="" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "", title = null)))
    }

    @Test
    fun `should parse spec sample 582 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo][bar]
                |
                |[bar]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "foo", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 583 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo][bar]
                |
                |[BAR]: /url
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "foo", title = null, Text("foo"))))
    }

    @Test
    fun `should parse spec sample 584 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo][]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo" title="title" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "foo", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 585 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![*foo* bar][]
                |
                |[*foo* bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo bar" title="title" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(source = "/url", alt = "foo bar", title = "title", Emphasis("*", Text("foo")), Text(" bar"))
            )
        )
    }

    @Test
    fun `should parse spec sample 586 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![Foo][]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="Foo" title="title" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "Foo", title = "title", Text("Foo"))))
    }

    @Test
    fun `should parse spec sample 587 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo] 
                |[]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo" title="title" />
         * []</p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(source = "/url", alt = "foo", title = "title", Text("foo")),
                Text(""), // This looks wrong but it's correct
                SoftLineBreak,
                Text("[]"),
            )
        )
    }

    @Test
    fun `should parse spec sample 588 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![foo]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo" title="title" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "foo", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 589 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![*foo* bar]
                |
                |[*foo* bar]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="foo bar" title="title" /></p>
         */
        parsed.assertEquals(
            Paragraph(
                Image(source = "/url", alt = "foo bar", title = "title", Emphasis("*", Text("foo")), Text(" bar"))
            )
        )
    }

    @Test
    fun `should parse spec sample 590 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![[foo]]
                |
                |[[foo]]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>![[foo]]</p>
         * <p>[[foo]]: /url &quot;title&quot;</p>
         */
        parsed.assertEquals(paragraph("![[foo]]"), paragraph("[[foo]]: /url \"title\""))
    }

    @Test
    fun `should parse spec sample 591 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |![Foo]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><img src="/url" alt="Foo" title="title" /></p>
         */
        parsed.assertEquals(Paragraph(Image(source = "/url", alt = "Foo", title = "title", Text("Foo"))))
    }

    @Test
    fun `should parse spec sample 592 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |!\[foo]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>![foo]</p>
         */
        parsed.assertEquals(paragraph("![foo]"))
    }

    @Test
    fun `should parse spec sample 593 correctly {Images}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |\![foo]
                |
                |[foo]: /url "title"
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>!<a href="/url" title="title">foo</a></p>
         */
        parsed.assertEquals(Paragraph(Text("!"), Link(destination = "/url", title = "title", Text("foo"))))
    }

    @Test
    fun `should parse spec sample 594 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<http://foo.bar.baz>")

        /*
         * Expected HTML:
         * <p><a href="http://foo.bar.baz">http://foo.bar.baz</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "http://foo.bar.baz", title = null, Text("http://foo.bar.baz")))
        )
    }

    @Test
    fun `should parse spec sample 595 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<https://foo.bar.baz/test?q=hello&id=22&boolean>")

        /*
         * Expected HTML:
         * <p><a href="https://foo.bar.baz/test?q=hello&amp;id=22&amp;boolean">https://foo.bar.baz/test?q=hello&amp;id=22&amp;boolean</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(
                    destination = "https://foo.bar.baz/test?q=hello&id=22&boolean",
                    title = null,
                    Text("https://foo.bar.baz/test?q=hello&id=22&boolean"),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 596 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<irc://foo.bar:2233/baz>")

        /*
         * Expected HTML:
         * <p><a href="irc://foo.bar:2233/baz">irc://foo.bar:2233/baz</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "irc://foo.bar:2233/baz", title = null, Text("irc://foo.bar:2233/baz")))
        )
    }

    @Test
    fun `should parse spec sample 597 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<MAILTO:FOO@BAR.BAZ>")

        /*
         * Expected HTML:
         * <p><a href="MAILTO:FOO@BAR.BAZ">MAILTO:FOO@BAR.BAZ</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "MAILTO:FOO@BAR.BAZ", title = null, Text("MAILTO:FOO@BAR.BAZ")))
        )
    }

    @Test
    fun `should parse spec sample 598 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<a+b+c:d>")

        /*
         * Expected HTML:
         * <p><a href="a+b+c:d">a+b+c:d</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "a+b+c:d", title = null, Text("a+b+c:d"))))
    }

    @Test
    fun `should parse spec sample 599 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<made-up-scheme://foo,bar>")

        /*
         * Expected HTML:
         * <p><a href="made-up-scheme://foo,bar">made-up-scheme://foo,bar</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "made-up-scheme://foo,bar", title = null, Text("made-up-scheme://foo,bar")))
        )
    }

    @Test
    fun `should parse spec sample 600 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<https://../>")

        /*
         * Expected HTML:
         * <p><a href="https://../">https://../</a></p>
         */
        parsed.assertEquals(Paragraph(Link(destination = "https://../", title = null, Text("https://../"))))
    }

    @Test
    fun `should parse spec sample 601 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<localhost:5001/foo>")

        /*
         * Expected HTML:
         * <p><a href="localhost:5001/foo">localhost:5001/foo</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "localhost:5001/foo", title = null, Text("localhost:5001/foo")))
        )
    }

    @Test
    fun `should parse spec sample 602 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<https://foo.bar/baz bim>")

        /*
         * Expected HTML:
         * <p>&lt;https://foo.bar/baz bim&gt;</p>
         */
        parsed.assertEquals(paragraph("<https://foo.bar/baz bim>"))
    }

    @Test
    fun `should parse spec sample 603 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<https://example.com/\\[\\>")

        /*
         * Expected HTML:
         * <p><a href="https://example.com/%5C%5B%5C">https://example.com/\[\</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "https://example.com/\\[\\", title = null, Text("https://example.com/\\[\\")))
        )
    }

    @Test
    fun `should parse spec sample 604 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<foo@bar.example.com>")

        /*
         * Expected HTML:
         * <p><a href="mailto:foo@bar.example.com">foo@bar.example.com</a></p>
         */
        parsed.assertEquals(
            Paragraph(Link(destination = "mailto:foo@bar.example.com", title = null, Text("foo@bar.example.com")))
        )
    }

    @Test
    fun `should parse spec sample 605 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<foo+special@Bar.baz-bar0.com>")

        /*
         * Expected HTML:
         * <p><a href="mailto:foo+special@Bar.baz-bar0.com">foo+special@Bar.baz-bar0.com</a></p>
         */
        parsed.assertEquals(
            Paragraph(
                Link(
                    destination = "mailto:foo+special@Bar.baz-bar0.com",
                    title = null,
                    Text("foo+special@Bar.baz-bar0.com"),
                )
            )
        )
    }

    @Test
    fun `should parse spec sample 606 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<foo\\+@bar.example.com>")

        /*
         * Expected HTML:
         * <p>&lt;foo+@bar.example.com&gt;</p>
         */
        parsed.assertEquals(paragraph("<foo+@bar.example.com>"))
    }

    @Test
    fun `should parse spec sample 607 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<>")

        /*
         * Expected HTML:
         * <p>&lt;&gt;</p>
         */
        parsed.assertEquals(paragraph("<>"))
    }

    @Test
    fun `should parse spec sample 608 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("< https://foo.bar >")

        /*
         * Expected HTML:
         * <p>&lt; https://foo.bar &gt;</p>
         */
        parsed.assertEquals(paragraph("< https://foo.bar >"))
    }

    @Test
    fun `should parse spec sample 609 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<m:abc>")

        /*
         * Expected HTML:
         * <p>&lt;m:abc&gt;</p>
         */
        parsed.assertEquals(paragraph("<m:abc>"))
    }

    @Test
    fun `should parse spec sample 610 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("<foo.bar.baz>")

        /*
         * Expected HTML:
         * <p>&lt;foo.bar.baz&gt;</p>
         */
        parsed.assertEquals(paragraph("<foo.bar.baz>"))
    }

    @Test
    fun `should parse spec sample 611 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("https://example.com")

        /*
         * Expected HTML:
         * <p>https://example.com</p>
         */
        parsed.assertEquals(paragraph("https://example.com"))
    }

    @Test
    fun `should parse spec sample 612 correctly {Autolinks}`() {
        val parsed = processor.processMarkdownDocument("foo@bar.example.com")

        /*
         * Expected HTML:
         * <p>foo@bar.example.com</p>
         */
        parsed.assertEquals(paragraph("foo@bar.example.com"))
    }

    @Test
    fun `should parse spec sample 613 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("<a><bab><c2c>")

        /*
         * Expected HTML:
         * <p><a><bab><c2c></p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("<a>"), HtmlInline("<bab>"), HtmlInline("<c2c>")))
    }

    @Test
    fun `should parse spec sample 614 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("<a/><b2/>")

        /*
         * Expected HTML:
         * <p><a/><b2/></p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("<a/>"), HtmlInline("<b2/>")))
    }

    @Test
    fun `should parse spec sample 615 correctly {Raw HTML}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<a  /><b2
                |data="foo" >
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a  /><b2
         * data="foo" ></p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("<a  />"), HtmlInline("<b2\ndata=\"foo\" >")))
    }

    @Test
    fun `should parse spec sample 616 correctly {Raw HTML}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<a foo="bar" bam = 'baz <em>"</em>'
                |_boolean zoop:33=zoop:33 />
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a foo="bar" bam = 'baz <em>"</em>'
         * _boolean zoop:33=zoop:33 /></p>
         */
        parsed.assertEquals(
            Paragraph(HtmlInline("<a foo=\"bar\" bam = 'baz <em>\"</em>'\n_boolean zoop:33=zoop:33 />"))
        )
    }

    @Test
    fun `should parse spec sample 617 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("Foo <responsive-image src=\"foo.jpg\" />")

        /*
         * Expected HTML:
         * <p>Foo <responsive-image src="foo.jpg" /></p>
         */
        parsed.assertEquals(Paragraph(Text("Foo "), HtmlInline("<responsive-image src=\"foo.jpg\" />")))
    }

    @Test
    fun `should parse spec sample 618 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("<33> <__>")

        /*
         * Expected HTML:
         * <p>&lt;33&gt; &lt;__&gt;</p>
         */
        parsed.assertEquals(paragraph("<33> <__>"))
    }

    @Test
    fun `should parse spec sample 619 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("<a h*#ref=\"hi\">")

        /*
         * Expected HTML:
         * <p>&lt;a h*#ref=&quot;hi&quot;&gt;</p>
         */
        parsed.assertEquals(paragraph("<a h*#ref=\"hi\">"))
    }

    @Test
    fun `should parse spec sample 620 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("<a href=\"hi'> <a href=hi'>")

        /*
         * Expected HTML:
         * <p>&lt;a href=&quot;hi'&gt; &lt;a href=hi'&gt;</p>
         */
        parsed.assertEquals(paragraph("<a href=\"hi'> <a href=hi'>"))
    }

    @Test
    fun `should parse spec sample 621 correctly {Raw HTML}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |< a><
                |foo><bar/ >
                |<foo bar=baz
                |bim!bop />
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>&lt; a&gt;&lt;
         * foo&gt;&lt;bar/ &gt;
         * &lt;foo bar=baz
         * bim!bop /&gt;</p>
         */
        parsed.assertEquals(
            Paragraph(
                Text("< a><"),
                SoftLineBreak,
                Text("foo><bar/ >"),
                SoftLineBreak,
                Text("<foo bar=baz"),
                SoftLineBreak,
                Text("bim!bop />"),
            )
        )
    }

    @Test
    fun `should parse spec sample 622 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("<a href='bar'title=title>")

        /*
         * Expected HTML:
         * <p>&lt;a href='bar'title=title&gt;</p>
         */
        parsed.assertEquals(paragraph("<a href='bar'title=title>"))
    }

    @Test
    fun `should parse spec sample 623 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("</a></foo >")

        /*
         * Expected HTML:
         * <p></a></foo ></p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("</a>"), HtmlInline("</foo >")))
    }

    @Test
    fun `should parse spec sample 624 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("</a href=\"foo\">")

        /*
         * Expected HTML:
         * <p>&lt;/a href=&quot;foo&quot;&gt;</p>
         */
        parsed.assertEquals(paragraph("</a href=\"foo\">"))
    }

    @Test
    fun `should parse spec sample 625 correctly {Raw HTML}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo <!-- this is a --
                |comment - with hyphens -->
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo <!-- this is a --
         * comment - with hyphens --></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), HtmlInline("<!-- this is a --\ncomment - with hyphens -->")))
    }

    @Test
    fun `should parse spec sample 626 correctly {Raw HTML}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo <!--> foo -->
                |
                |foo <!---> foo -->
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo <!--> foo --&gt;</p>
         * <p>foo <!---> foo --&gt;</p>
         */
        parsed.assertEquals(
            Paragraph(Text("foo "), HtmlInline("<!-->"), Text(" foo -->")),
            Paragraph(Text("foo "), HtmlInline("<!--->"), Text(" foo -->")),
        )
    }

    @Test
    fun `should parse spec sample 627 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("foo <?php echo \$a; ?>")

        /*
         * Expected HTML:
         * <p>foo <?php echo $a; ?></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), HtmlInline("<?php echo \$a; ?>")))
    }

    @Test
    fun `should parse spec sample 628 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("foo <!ELEMENT br EMPTY>")

        /*
         * Expected HTML:
         * <p>foo <!ELEMENT br EMPTY></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), HtmlInline("<!ELEMENT br EMPTY>")))
    }

    @Test
    fun `should parse spec sample 629 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("foo <![CDATA[>&<]]>")

        /*
         * Expected HTML:
         * <p>foo <![CDATA[>&<]]></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), HtmlInline("<![CDATA[>&<]]>")))
    }

    @Test
    fun `should parse spec sample 630 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("foo <a href=\"&ouml;\">")

        /*
         * Expected HTML:
         * <p>foo <a href="&ouml;"></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), HtmlInline("<a href=\"&ouml;\">")))
    }

    @Test
    fun `should parse spec sample 631 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("foo <a href=\"\\*\">")

        /*
         * Expected HTML:
         * <p>foo <a href="\*"></p>
         */
        parsed.assertEquals(Paragraph(Text("foo "), HtmlInline("<a href=\"\\*\">")))
    }

    @Test
    fun `should parse spec sample 632 correctly {Raw HTML}`() {
        val parsed = processor.processMarkdownDocument("<a href=\"\\\"\">")

        /*
         * Expected HTML:
         * <p>&lt;a href=&quot;&quot;&quot;&gt;</p>
         */
        parsed.assertEquals(paragraph("<a href=\"\"\">"))
    }

    @Test
    fun `should parse spec sample 633 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo  
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo<br />
         * baz</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), HardLineBreak, Text("baz")))
    }

    @Test
    fun `should parse spec sample 634 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo\
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo<br />
         * baz</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), HardLineBreak, Text("baz")))
    }

    @Test
    fun `should parse spec sample 635 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo       
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo<br />
         * baz</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), HardLineBreak, Text("baz")))
    }

    @Test
    fun `should parse spec sample 636 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo  
                |     bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo<br />
         * bar</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), HardLineBreak, Text("bar")))
    }

    @Test
    fun `should parse spec sample 637 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo\
                |     bar
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo<br />
         * bar</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), HardLineBreak, Text("bar")))
    }

    @Test
    fun `should parse spec sample 638 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |*foo  
                |bar*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><em>foo<br />
         * bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo"), HardLineBreak, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 639 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |*foo\
                |bar*
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><em>foo<br />
         * bar</em></p>
         */
        parsed.assertEquals(Paragraph(Emphasis("*", Text("foo"), HardLineBreak, Text("bar"))))
    }

    @Test
    fun `should parse spec sample 640 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |`code  
                |span`
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code>code   span</code></p>
         */
        parsed.assertEquals(Paragraph(Code("code   span")))
    }

    @Test
    fun `should parse spec sample 641 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |`code\
                |span`
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><code>code\ span</code></p>
         */
        parsed.assertEquals(Paragraph(Code("code\\ span")))
    }

    @Test
    fun `should parse spec sample 642 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<a href="foo  
                |bar">
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="foo
         * bar"></p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("<a href=\"foo  \nbar\">")))
    }

    @Test
    fun `should parse spec sample 643 correctly {Hard line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |<a href="foo\
                |bar">
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p><a href="foo\
         * bar"></p>
         */
        parsed.assertEquals(Paragraph(HtmlInline("<a href=\"foo\\\nbar\">")))
    }

    @Test
    fun `should parse spec sample 644 correctly {Hard line breaks}`() {
        val parsed = processor.processMarkdownDocument("foo\\")

        /*
         * Expected HTML:
         * <p>foo\</p>
         */
        parsed.assertEquals(paragraph("foo\\"))
    }

    @Test
    fun `should parse spec sample 645 correctly {Hard line breaks}`() {
        val parsed = processor.processMarkdownDocument("foo  ")

        /*
         * Expected HTML:
         * <p>foo</p>
         */
        parsed.assertEquals(paragraph("foo"))
    }

    @Test
    fun `should parse spec sample 646 correctly {Hard line breaks}`() {
        val parsed = processor.processMarkdownDocument("### foo\\")

        /*
         * Expected HTML:
         * <h3>foo\</h3>
         */
        parsed.assertEquals(heading(level = 3, Text("foo\\")))
    }

    @Test
    fun `should parse spec sample 647 correctly {Hard line breaks}`() {
        val parsed = processor.processMarkdownDocument("### foo  ")

        /*
         * Expected HTML:
         * <h3>foo</h3>
         */
        parsed.assertEquals(heading(level = 3, Text("foo")))
    }

    @Test
    fun `should parse spec sample 648 correctly {Soft line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo
                |baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo
         * baz</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), SoftLineBreak, Text("baz")))
    }

    @Test
    fun `should parse spec sample 649 correctly {Soft line breaks}`() {
        val parsed =
            processor.processMarkdownDocument(
                """
                |foo 
                | baz
                """
                    .trimMargin()
            )

        /*
         * Expected HTML:
         * <p>foo
         * baz</p>
         */
        parsed.assertEquals(Paragraph(Text("foo"), SoftLineBreak, Text("baz")))
    }

    @Test
    fun `should parse spec sample 650 correctly {Textual content}`() {
        val parsed = processor.processMarkdownDocument("hello $.;'there")

        /*
         * Expected HTML:
         * <p>hello $.;'there</p>
         */
        parsed.assertEquals(paragraph("hello \$.;'there"))
    }

    @Test
    fun `should parse spec sample 651 correctly {Textual content}`() {
        val parsed = processor.processMarkdownDocument("Foo χρῆν")

        /*
         * Expected HTML:
         * <p>Foo χρῆν</p>
         */
        parsed.assertEquals(paragraph("Foo χρῆν"))
    }

    @Test
    fun `should parse spec sample 652 correctly {Textual content}`() {
        val parsed = processor.processMarkdownDocument("Multiple     spaces")

        /*
         * Expected HTML:
         * <p>Multiple     spaces</p>
         */
        parsed.assertEquals(paragraph("Multiple     spaces"))
    }
}

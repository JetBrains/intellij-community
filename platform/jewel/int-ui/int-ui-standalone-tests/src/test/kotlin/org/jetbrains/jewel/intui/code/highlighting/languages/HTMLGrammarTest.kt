// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class HTMLGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "html") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        val identifiers = listOf("html", "htm", "xhtml", "xht", "shtml", "mdoc", "jshtm", "volt", "ejs", "rhtml")
        for (identifier in identifiers) {
            assertTrue(highlight("<div>", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("<!-- a comment -->").colorAt(0))
    }

    @Test
    fun `tag inside a comment is not colored as a tag`() = runTest {
        // "<" of the inner tag is at index 5 in "<!-- <div> -->"
        assertEquals(testColors.comment, highlight("<!-- <div> -->").colorAt(5))
    }

    @Test
    fun `doctype name is colored as keyword`() = runTest {
        // Only the DOCTYPE word carries entity.name.tag.html; the leading "<!" is punctuation, and the
        // bundle scopes "html" through a doctype-internal rule that needs a tag context to be ported.
        val highlighted = highlight("<!DOCTYPE html>")
        assertEquals(testColors.keyword, highlighted.colorAt(2))
        assertNull(highlighted.colorAt(0))
        assertNull(highlighted.colorAt(10))
    }

    @Test
    fun `xml processing instruction name is colored as keyword`() = runTest {
        // "xml" starts at index 2 in `<?xml version="1.0"?>`
        assertEquals(testColors.keyword, highlight("<?xml version=\"1.0\"?>").colorAt(2))
    }

    @Test
    fun `cdata content is colored as string`() = runTest {
        // "raw" starts at index 10 in "<![CDATA[ raw ]]>"; the delimiters are punctuation
        val highlighted = highlight("<![CDATA[ raw ]]>")
        assertEquals(testColors.string, highlighted.colorAt(10))
        assertNull(highlighted.colorAt(0))
    }

    @Test
    fun `tag name is colored as keyword`() = runTest {
        val highlighted = highlight("<div>")
        assertEquals(testColors.keyword, highlighted.colorAt(1))
        assertNull(highlighted.colorAt(0))
    }

    @Test
    fun `closing tag name is colored as keyword`() = runTest {
        // "div" starts at index 2 in "</div>"
        assertEquals(testColors.keyword, highlight("</div>").colorAt(2))
    }

    @Test
    fun `heading and void tag names are colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("<h1>").colorAt(1))
        assertEquals(testColors.keyword, highlight("<thead>").colorAt(1))
        assertEquals(testColors.keyword, highlight("<br/>").colorAt(1))
    }

    @Test
    fun `script and style tag names are colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("<script>").colorAt(1))
        assertEquals(testColors.keyword, highlight("</script>").colorAt(2))
        assertEquals(testColors.keyword, highlight("<style>").colorAt(1))
        assertEquals(testColors.keyword, highlight("</style>").colorAt(2))
    }

    @Test
    fun `custom element name is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("<my-widget>").colorAt(1))
        assertEquals(testColors.keyword, highlight("</my-widget>").colorAt(2))
    }

    @Test
    fun `obsolete and unrecognized tag names are still colored as keyword`() = runTest {
        // The bundle scopes these as entity.name.tag.html plus an invalid.* scope that this highlighter skips
        assertEquals(testColors.keyword, highlight("<applet>").colorAt(1))
        assertEquals(testColors.keyword, highlight("<blink>").colorAt(1))
        assertEquals(testColors.keyword, highlight("<UNKNOWNTAG>").colorAt(1))
    }

    @Test
    fun `attribute name is colored as property key`() = runTest {
        // "href" starts at index 3 in `<a href="x">`
        val highlighted = highlight("<a href=\"x\">")
        assertEquals(testColors.propertyKey, highlighted.colorAt(3))
        assertEquals(testColors.keyword, highlighted.colorAt(1))
    }

    @Test
    fun `data and event handler attributes are colored as property key`() = runTest {
        // Each of these attribute names starts at index 5
        assertEquals(testColors.propertyKey, highlight("<div data-foo=\"1\">").colorAt(5))
        assertEquals(testColors.propertyKey, highlight("<div onclick=\"f()\">").colorAt(5))
        assertEquals(testColors.propertyKey, highlight("<div style=\"a\">").colorAt(5))
    }

    @Test
    fun `an attribute name buried in a longer name is not colored`() = runTest {
        // The bundle's names carry a trailing (?![\w:-]) but no leading guard, so without the one we add
        // these match a suffix and color only part of the name.
        assertNull(highlight("<div mytitle=\"x\">").colorAt(7), "html5 name as a suffix")
        assertNull(highlight("<div myonclick=\"f()\">").colorAt(7), "event handler as a suffix")
        assertNull(highlight("<div xstyle=\"x\">").colorAt(6), "style as a suffix")
        assertNull(highlight("<div notdata-foo=\"x\">").colorAt(8), "data- as a suffix")
        assertNull(highlight("<div a-title=\"x\">").colorAt(7), "after a hyphen")
        assertNull(highlight("<div xml:lang=\"en\">").colorAt(9), "after a namespace colon")
    }

    @Test
    fun `valueless attributes are not colored`() = runTest {
        // The `(?=\s*=)` guard that keeps attribute names out of prose also excludes boolean attributes.
        // "type" is at index 7 and "required" at index 19 in `<input type="text" required>`.
        val highlighted = highlight("<input type=\"text\" required>")
        assertEquals(testColors.propertyKey, highlighted.colorAt(7))
        assertNull(highlighted.colorAt(19))
    }

    @Test
    fun `attribute names in text content are not colored`() = runTest {
        // Regression: HTML5 attribute names are ordinary English words, and the bundle relies on the grammar
        // tree to only ever try them between `<` and `>`. Without the `(?=\s*=)` guard, every one of these
        // words was colored as a property key.
        for (word in listOf("title", "for", "size", "value", "list", "method", "type", "open")) {
            assertNull(highlight("<p>a $word b</p>").colorAt(5), "'$word' should not be colored in text content")
        }
    }

    @Test
    fun `prose with no markup is not highlighted at all`() = runTest {
        assertTrue(highlight("The list method returns a value of that type.").spanStyles.isEmpty())
    }

    @Test
    fun `deprecated attribute name is not colored`() = runTest {
        // The bundle's only scope for align, bgcolor and border is invalid.deprecated.*, which is skipped
        assertNull(highlight("<div align=\"x\">").colorAt(5))
    }

    @Test
    fun `attribute value is colored as string`() = runTest {
        // The opening quote is at index 8 in `<a href="x">`
        assertEquals(testColors.string, highlight("<a href=\"x\">").colorAt(8))
        // The opening quote is at index 11 in `<div class='a'>`
        assertEquals(testColors.string, highlight("<div class='a'>").colorAt(11))
    }

    @Test
    fun `entities are colored as constant`() = runTest {
        for (entity in listOf("&nbsp;", "&amp;", "&#160;", "&#xA0;", "&#XA0;")) {
            assertEquals(testColors.constant, highlight(entity).colorAt(0), "'$entity' should be colored as constant")
        }
    }

    @Test
    fun `text content is not highlighted`() = runTest {
        // "hello" starts at index 3 in "<p>hello</p>"
        val highlighted = highlight("<p>hello</p>")
        assertNull(highlighted.colorAt(3))
        assertEquals(testColors.keyword, highlighted.colorAt(1))
        assertEquals(testColors.keyword, highlighted.colorAt(10))
    }

    @Test
    fun `tags on separate lines are highlighted`() = runTest {
        // "span" starts at index 9 and its closing name at index 17
        val highlighted = highlight("<div>\n  <span>x</span>\n</div>")
        assertEquals(testColors.keyword, highlighted.colorAt(9))
        assertEquals(testColors.keyword, highlighted.colorAt(17))
    }
}

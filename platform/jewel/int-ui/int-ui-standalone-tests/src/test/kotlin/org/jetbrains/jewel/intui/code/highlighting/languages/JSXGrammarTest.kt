// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class JSXGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "jsx") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        for (identifier in listOf("jsx", "javascriptreact")) {
            assertTrue(highlight("let x = 1", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `the javascript grammar does not highlight jsx tags`() = runTest {
        // Pins the split from the other side: JAVASCRIPT is registered first, so if it ever reclaims the
        // "jsx" alias the tag tests below start resolving to this (unstyled) grammar instead.
        assertNull(highlight("<div>", "javascript").colorAt(1))
    }

    @Test
    fun `lowercase tag name is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("<div>").colorAt(1))
    }

    @Test
    fun `closing tag name is colored as keyword`() = runTest {
        // "div" starts at index 2 in "</div>"
        assertEquals(testColors.keyword, highlight("</div>").colorAt(2))
    }

    @Test
    fun `capitalized tag name is colored as type`() = runTest {
        val result = highlight("<Foo />")
        assertEquals(testColors.type, result.colorAt(1))
        assertNotEquals(testColors.keyword, result.colorAt(1), "Components should not read as DOM elements")
    }

    @Test
    fun `dotted component name is colored as type`() = runTest {
        assertEquals(testColors.type, highlight("<Foo.Bar />").colorAt(1))
    }

    @Test
    fun `attribute name is colored as property key`() = runTest {
        // "className" starts at index 5 in `<div className="x">`
        assertEquals(testColors.propertyKey, highlight("<div className=\"x\">").colorAt(5))
    }

    @Test
    fun `attribute value is colored as string`() = runTest {
        // The opening quote is at index 15 in `<div className="x">`
        assertEquals(testColors.string, highlight("<div className=\"x\">").colorAt(15))
    }

    @Test
    fun `attribute with an expression value is colored as property key`() = runTest {
        // "onClick" starts at index 5 in "<div onClick={f}>"
        assertEquals(testColors.propertyKey, highlight("<div onClick={f}>").colorAt(5))
    }

    @Test
    fun `attribute rule does not fire on javascript assignments`() = runTest {
        // Spacing is a convention, not a syntactic boundary, so the rule is anchored to opening-tag
        // context instead: an unclosed `<` in expression position, a tag name, then whitespace.
        assertNull(highlight("const x = \"y\"").colorAt(6), "spaced assignment")
        assertNull(highlight("const x=\"y\";").colorAt(6), "compact assignment")
        assertNull(highlight("let a=\"b\", c=\"d\";").colorAt(4), "compact let")
        assertNull(highlight("let a=\"b\", c=\"d\";").colorAt(11), "second compact assignment")
        assertNull(highlight("obj={k:\"v\"};").colorAt(0), "object literal")
        assertNull(highlight("foo.bar=\"baz\";").colorAt(0), "member assignment")
    }

    @Test
    fun `a comparison operator does not open tag context`() = runTest {
        assertNull(highlight("if (a<b && c=\"d\") {}").colorAt(11))
        assertNull(highlight("x[0]<y && z=\"w\";").colorAt(10), "after a subscript")
        assertNull(highlight("f()<g && h=\"i\";").colorAt(9), "after a call")
    }

    @Test
    fun `attributes are still recognized across tag shapes`() = runTest {
        assertEquals(testColors.propertyKey, highlight("<div class=\"a\" id=\"b\">").colorAt(15), "second attr")
        assertEquals(testColors.propertyKey, highlight("return <div id=\"x\">;").colorAt(12), "after return")
        assertEquals(testColors.propertyKey, highlight("<Foo bar={baz}>").colorAt(5), "brace value")
    }

    @Test
    fun `html entity is colored as constant`() = runTest {
        for (entity in listOf("&nbsp;", "&#160;", "&#xA0;")) {
            assertEquals(testColors.constant, highlight(entity).colorAt(0), "'$entity' should be colored as constant")
        }
    }

    @Test fun `logical and is not mistaken for an entity`() = runTest { assertNull(highlight("a && b").colorAt(2)) }

    @Test
    fun `less-than in an expression is not mistaken for a tag`() = runTest {
        // "b" is at index 6 in "if (a<b) {}"
        val result = highlight("if (a<b) {}")
        assertEquals(testColors.keyword, result.colorAt(0), "'if' should still be a keyword")
        assertNull(result.colorAt(6), "'b' should not read as a tag name")
    }

    @Test
    fun `tag inside a comment is not colored as a tag`() = runTest {
        // "<" is at index 3 in "// <div>"
        assertEquals(testColors.comment, highlight("// <div>").colorAt(3))
    }

    @Test
    fun `tag inside a string is not colored as a tag`() = runTest {
        // "<" is at index 1 in "\"<div>\""
        assertEquals(testColors.string, highlight("\"<div>\"").colorAt(1))
    }

    @Test
    fun `attribute rule wins over the javascript keyword rule`() = runTest {
        // "for" is a JS keyword, but inside a tag it is an attribute name
        assertEquals(testColors.propertyKey, highlight("<label for=\"x\">").colorAt(7))
    }

    @Test
    fun `javascript rules still apply`() = runTest {
        assertEquals(testColors.keyword, highlight("const x = 1").colorAt(0))
        assertEquals(testColors.number, highlight("const x = 1").colorAt(10))
        assertEquals(testColors.comment, highlight("// a comment").colorAt(0))
        assertEquals(testColors.builtin, highlight("console").colorAt(0))
    }

    @Test
    fun `component in a return statement is colored as type`() = runTest {
        // "Foo" starts at index 8 in "return <Foo />"
        val result = highlight("return <Foo />")
        assertEquals(testColors.keyword, result.colorAt(0))
        assertEquals(testColors.type, result.colorAt(8))
    }
}

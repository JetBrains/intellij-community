// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.spansAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class CSSGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "css") =
        highlighter.highlight(code, language).first()

    @Test fun `name is recognized`() = runTest { assertTrue(highlight("a { color: red; }").spanStyles.isNotEmpty()) }

    @Test
    fun `comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("/* a comment */").colorAt(0))
    }

    @Test
    fun `property name is colored as property key`() = runTest {
        // "color" starts at index 4 in "a { color: red; }"
        assertEquals(testColors.propertyKey, highlight("a { color: red; }").colorAt(4))
    }

    @Test
    fun `custom property is colored as property key`() = runTest {
        // "--brand" starts at index 4 in "a { --brand: red; }"
        assertEquals(testColors.propertyKey, highlight("a { --brand: red; }").colorAt(4))
    }

    @Test
    fun `vendored property name is colored as property key`() = runTest {
        assertEquals(testColors.propertyKey, highlight("a { -webkit-box-shadow: none; }").colorAt(4))
    }

    @Test
    fun `element selector is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("a { color: red; }").colorAt(0))
    }

    @Test
    fun `custom element selector is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("my-widget { color: red; }").colorAt(0))
    }

    @Test
    fun `class selector is colored as type`() = runTest {
        assertEquals(testColors.type, highlight(".foo { color: red; }").colorAt(0))
    }

    @Test
    fun `class selector may contain digits and double hyphens`() = runTest {
        assertEquals(testColors.type, highlight(".card--wide { color: red; }").colorAt(0))
        assertEquals(testColors.type, highlight(".col-6 { color: red; }").colorAt(0))
    }

    @Test
    fun `id selector is colored as type`() = runTest {
        assertEquals(testColors.type, highlight("#foo { color: red; }").colorAt(0))
    }

    @Test
    fun `an id selector that looks like a hex color is still a selector`() = runTest {
        // `#foo` above never reached the hex rule, since `o` is not a hex digit. These do.
        assertEquals(testColors.type, highlight("#abc { color: red; }").colorAt(0))
        assertEquals(testColors.type, highlight("#abcdef { color: red; }").colorAt(0))
        assertEquals(testColors.type, highlight("#abc, #def { color: red; }").colorAt(6))
        assertEquals(testColors.type, highlight("a:hover #abc { color: red; }").colorAt(8))
        assertEquals(testColors.type, highlight("a { color: red; } #abc { color: red; }").colorAt(18))
    }

    @Test
    fun `a hyphenated id selector is colored whole, not just its hex-looking prefix`() = runTest {
        val result = highlight("#abc-def { color: red; }")
        assertEquals(testColors.type, result.colorAt(0))
        assertEquals(testColors.type, result.colorAt(5))
    }

    @Test
    fun `pseudo-class is colored as builtin`() = runTest {
        val result = highlight("a:hover { color: red; }")
        assertEquals(testColors.keyword, result.colorAt(0), "'a' is still an element selector")
        assertEquals(testColors.builtin, result.colorAt(1), "':hover' should be a pseudo-class")
        assertEquals(testColors.builtin, result.colorAt(2), "the name is part of the same match")
    }

    @Test
    fun `pseudo-element is colored as builtin`() = runTest {
        assertEquals(testColors.builtin, highlight("a::before { color: red; }").colorAt(1))
    }

    @Test
    fun `pseudo-class is not mistaken for a property`() = runTest {
        // The grammar's property-name list does not contain `hover`, and the pseudo-class rule owns the colon
        assertNotEquals(testColors.propertyKey, highlight("a:hover { color: red; }").colorAt(0))
    }

    @Test
    fun `functional pseudo-class colors its nth expression as a number`() = runTest {
        val result = highlight("li:nth-child(2n+1) { color: red; }")
        assertEquals(testColors.builtin, result.colorAt(2))
        assertEquals(testColors.number, result.colorAt(13))
    }

    @Test
    fun `functional pseudo-class colors its parity keyword as builtin`() = runTest {
        assertEquals(testColors.builtin, highlight("li:nth-child(odd) { color: red; }").colorAt(13))
    }

    @Test
    fun `negation pseudo-class is colored as builtin`() = runTest {
        assertEquals(testColors.builtin, highlight("a:not(.x) { color: red; }").colorAt(1))
    }

    @Test
    fun `a value that is also a tag name stays a value without a trailing semicolon`() = runTest {
        // Regression: about twenty words are in both the tag-name and property-value lists. The bundle keeps
        // them apart by nesting; flattened, `table` matched the tag rule because `\s` is in its lookahead set
        // and `}` follows. Declarations ending in `;` were never affected.
        for (value in listOf("table", "small", "ruby", "progress", "menu")) {
            assertEquals(
                testColors.builtin,
                highlight("a { display: $value }").colorAt(13),
                "'$value' without a trailing semicolon should still be a property value",
            )
            assertEquals(
                testColors.builtin,
                highlight("a { display: $value; }").colorAt(13),
                "'$value' with a trailing semicolon should be a property value",
            )
        }
    }

    @Test
    fun `the same words are still tag names in selector position`() = runTest {
        // The other half of the guard: it must not cost us selector highlighting
        for (tag in listOf("table", "small", "ruby", "progress", "menu", "code")) {
            assertEquals(
                testColors.keyword,
                highlight("$tag { color: red; }").colorAt(0),
                "'$tag' in selector position should be a tag name",
            )
        }
    }

    @Test
    fun `hex color is colored as constant`() = runTest {
        // "#fff" starts at index 11 in "a { color: #fff; }"
        assertEquals(testColors.constant, highlight("a { color: #fff; }").colorAt(11))
    }

    @Test
    fun `a hex color in value position is a color, not an id`() = runTest {
        val result = highlight("a { color: #abc; }")
        assertEquals(testColors.constant, result.colorAt(11))
        assertNotEquals(testColors.type, result.colorAt(11), "'#abc' in a value is a color, not an id")
        // Every shape the hex rule accepts, in the positions a value can take
        assertEquals(testColors.constant, highlight("a { color: #abc }").colorAt(11))
        assertEquals(testColors.constant, highlight("a { color:#abc; }").colorAt(10))
        assertEquals(testColors.constant, highlight("a { color: #abcd; }").colorAt(11))
        assertEquals(testColors.constant, highlight("a { color: #aabbccdd; }").colorAt(11))
        assertEquals(testColors.constant, highlight("a { border: 1px solid #ff0000; }").colorAt(22))
        assertEquals(
            testColors.constant,
            highlight("a { background: linear-gradient(#abc, #def); }").colorAt(32),
            "hex inside a function call",
        )
    }

    @Test
    fun `document-rule functions are colored as function call`() = runTest {
        // support.function.document-rule.css — each name starts at index 10 in `@document <name>("x") { }`
        for (function in listOf("url-prefix", "domain", "regexp")) {
            assertEquals(
                testColors.functionCall,
                highlight("@document $function(\"x\") { }").colorAt(10),
                "'$function' should be colored as a function call",
            )
        }
    }

    @Test
    fun `at-rule is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("@media screen").colorAt(0))
        assertEquals(testColors.keyword, highlight("@font-face { }").colorAt(0))
    }

    @Test
    fun `vendor-prefixed at-rule is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("@-webkit-keyframes spin { }").colorAt(0))
    }

    @Test
    fun `important is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("!important").colorAt(0))
        assertEquals(testColors.keyword, highlight("a { color: red !important; }").colorAt(15))
    }

    @Test
    fun `numbers with units are colored as number`() = runTest {
        // "10px" starts at index 11 in "a { width: 10px; }"
        assertEquals(testColors.number, highlight("a { width: 10px; }").colorAt(11))
        assertEquals(testColors.number, highlight("a { width: 50%; }").colorAt(11))
        assertEquals(testColors.number, highlight("a { margin: -1.5em; }").colorAt(12))
    }

    @Test
    fun `unit gets its own keyword span`() = runTest {
        // The bundle scopes the unit keyword.other.unit.*.css, so "rem" is both inside the number span and its own
        // keyword span. "rem" starts at index 12 in "a { width: 2rem; }".
        assertTrue(highlight("a { width: 2rem; }").spansAt(12).any { it.color == testColors.keyword })
    }

    @Test
    fun `decimal without a leading digit is a number, not a class`() = runTest {
        assertEquals(testColors.number, highlight("a { opacity: .5; }").colorAt(13))
        assertEquals(testColors.number, highlight("a { color: rgba(0, 0, 0, .5); }").colorAt(25))
    }

    @Test
    fun `string is colored as string`() = runTest {
        // The opening quote is at index 13 in "a { content: \"x\"; }"
        assertEquals(testColors.string, highlight("a { content: \"x\"; }").colorAt(13))
        assertEquals(testColors.string, highlight("a { content: '\\201C'; }").colorAt(13))
    }

    @Test
    fun `color keyword is colored as builtin`() = runTest {
        assertEquals(testColors.builtin, highlight("a { color: red; }").colorAt(11))
        assertEquals(testColors.builtin, highlight("a { color: rebeccapurple; }").colorAt(11))
        assertEquals(testColors.builtin, highlight("a { color: currentColor; }").colorAt(11))
    }

    @Test
    fun `property value keyword is colored as builtin`() = runTest {
        assertEquals(testColors.builtin, highlight("a { display: flex; }").colorAt(13))
        assertEquals(testColors.builtin, highlight("a { list-style-type: lower-roman; }").colorAt(21))
        assertEquals(testColors.builtin, highlight("a { font-family: helvetica; }").colorAt(17))
        assertEquals(testColors.builtin, highlight("a { color: -moz-fixed; }").colorAt(11))
    }

    @Test
    fun `function name is colored as function call`() = runTest {
        assertEquals(testColors.functionCall, highlight("a { width: calc(1px); }").colorAt(11))
        assertEquals(testColors.functionCall, highlight("a { color: rgba(0,0,0,.5); }").colorAt(11))
        assertEquals(testColors.functionCall, highlight("a { width: min(1px, 2px); }").colorAt(11))
        assertEquals(testColors.functionCall, highlight("a { clip-path: circle(1px); }").colorAt(15))
        assertEquals(testColors.functionCall, highlight("a { transform: translateX(1px); }").colorAt(15))
        assertEquals(testColors.functionCall, highlight("a { transition-timing-function: steps(2); }").colorAt(32))
    }

    @Test
    fun `gradient url and var are colored as function calls`() = runTest {
        assertEquals(testColors.functionCall, highlight("a { background: linear-gradient(red, blue); }").colorAt(16))
        assertEquals(testColors.functionCall, highlight("a { background: url(x.png); }").colorAt(16))
        assertEquals(testColors.functionCall, highlight("a { color: var(--x); }").colorAt(11))
    }

    @Test
    fun `attribute selector names its attribute and operator`() = runTest {
        assertEquals(testColors.propertyKey, highlight("a[href] { color: red; }").colorAt(2))
        assertEquals(testColors.operator, highlight("a[href=\"x\"] { color: red; }").colorAt(6))
    }

    @Test
    fun `media feature is colored as property key`() = runTest {
        assertEquals(testColors.propertyKey, highlight("@media (min-width: 600px) { }").colorAt(8))
    }

    @Test
    fun `media type and logical operator are colored`() = runTest {
        val result = highlight("@media screen and (color) { }")
        assertEquals(testColors.builtin, result.colorAt(7), "'screen' is a media type")
        assertEquals(testColors.operator, result.colorAt(14), "'and' is a logical operator")
    }

    @Test
    fun `media feature keyword is colored as builtin`() = runTest {
        assertEquals(testColors.builtin, highlight("@media (orientation: portrait) { }").colorAt(21))
    }

    @Test
    fun `aspect ratio is colored as two numbers around an operator`() = runTest {
        val result = highlight("@media (aspect-ratio: 16/9) { }")
        assertEquals(testColors.number, result.colorAt(22))
        assertEquals(testColors.operator, result.colorAt(24))
    }

    @Test
    fun `unicode range is colored as constant`() = runTest {
        assertEquals(testColors.constant, highlight("a { unicode-range: U+0025-00FF; }").colorAt(19))
    }

    @Test
    fun `combinator is an operator and the wildcard is a tag`() = runTest {
        assertEquals(testColors.operator, highlight("a > b { color: red; }").colorAt(2))
        assertEquals(testColors.keyword, highlight("* { color: red; }").colorAt(0))
    }

    @Test
    fun `an identifier the bundle does not know is left unstyled`() = runTest {
        assertEquals(null, highlight("a { color: zzzz; }").colorAt(11))
    }

    @Test
    fun `shorthand property names from the bundle list are recognized`() = runTest {
        assertEquals(testColors.propertyKey, highlight("a { background-position-x: 0; }").colorAt(4))
        assertEquals(testColors.propertyKey, highlight("a { overflow-y: hidden; }").colorAt(4))
    }
}

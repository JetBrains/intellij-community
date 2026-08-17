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

internal class YAMLGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "yaml") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        for (identifier in listOf("yaml", "yml", "eyaml", "eyml", "cff", "winget")) {
            assertTrue(highlight("key: 1", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("# a comment").colorAt(0))
    }

    @Test
    fun `plain key is colored as property key`() = runTest {
        assertEquals(testColors.propertyKey, highlight("key: value").colorAt(0))
    }

    @Test
    fun `indented key is colored as property key`() = runTest {
        // "nested" starts at index 2; the indentation is matched but not colored
        assertEquals(testColors.propertyKey, highlight("root:\n  nested: 1").colorAt(8))
    }

    @Test
    fun `key after a sequence marker is colored as property key`() = runTest {
        // "name" starts at index 2 in "- name: x"
        assertEquals(testColors.propertyKey, highlight("- name: x").colorAt(2))
    }

    @Test
    fun `quoted key is colored as property key`() = runTest {
        val result = highlight("\"key\": value")
        assertEquals(testColors.propertyKey, result.colorAt(0))
        assertNotEquals(testColors.string, result.colorAt(0), "A quoted key should not read as a string")
    }

    @Test
    fun `flow map keys are colored as property key`() = runTest {
        // The block rules are anchored to a line start, so a flow map on one line needs its own pair
        assertEquals(testColors.propertyKey, highlight("a: {foo: bar}").colorAt(4), "plain")
        assertEquals(testColors.propertyKey, highlight("a: {\"foo\": bar}").colorAt(4), "double-quoted")
        assertEquals(testColors.propertyKey, highlight("a: {'foo': bar}").colorAt(4), "single-quoted")
        assertEquals(testColors.propertyKey, highlight("{foo: bar}").colorAt(1), "at line start")
        assertEquals(testColors.propertyKey, highlight("a: {foo-bar: 1}").colorAt(4), "hyphenated")
    }

    @Test
    fun `every key in a flow map is colored, not just the first`() = runTest {
        val result = highlight("a: {foo: bar, baz: qux}")
        assertEquals(testColors.propertyKey, result.colorAt(4))
        assertEquals(testColors.propertyKey, result.colorAt(14))
        assertEquals(testColors.propertyKey, highlight("a: [{k: 1}, {k: 2}]").colorAt(5), "inside a sequence")
    }

    @Test
    fun `a quoted flow map key is not a string`() = runTest {
        val result = highlight("a: {\"foo\": bar}")
        assertNotEquals(testColors.string, result.colorAt(4))
    }

    @Test
    fun `flow map values stay unstyled`() = runTest {
        assertNull(highlight("a: {foo: bar}").colorAt(9))
        assertNull(highlight("a: {foo: bar, baz: qux}").colorAt(19))
    }

    @Test
    fun `quoted value is colored as string`() = runTest {
        // The opening quote is at index 5 in "key: \"value\""
        assertEquals(testColors.string, highlight("key: \"value\"").colorAt(5))
    }

    @Test
    fun `colon inside a value does not start a key`() = runTest {
        // Key rules are anchored to line start, and "12" fails the end-of-scalar lookahead every numeric
        // rule carries, so "12:30" stays an unstyled plain scalar
        val result = highlight("time: 12:30")
        assertEquals(testColors.propertyKey, result.colorAt(0))
        assertNull(result.colorAt(6))
    }

    @Test
    fun `hash without leading whitespace stays part of the key`() = runTest {
        // YAML only opens a comment when the # follows whitespace
        assertEquals(testColors.propertyKey, highlight("foo#bar: 1").colorAt(3))
    }

    @Test
    fun `trailing comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("key: # note").colorAt(5))
    }

    @Test
    fun `yaml directive colors the name and the version`() = runTest {
        val result = highlight("%YAML 1.2")
        assertEquals(testColors.keyword, result.colorAt(1)) // "YAML"
        assertEquals(testColors.number, result.colorAt(6)) // "1.2"
    }

    @Test
    fun `tag directive is colored as keyword`() = runTest {
        // keyword.other.directive.tag.yaml — "TAG" starts at index 1
        assertEquals(testColors.keyword, highlight("%TAG !e! tag:example.com,2000:app/").colorAt(1))
        assertEquals(testColors.keyword, highlight("%TAG").colorAt(1))
    }

    @Test
    fun `document marker is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("---\nkey: 1").colorAt(0))
    }

    @Test
    fun `anchors and aliases are colored as keyword`() = runTest {
        // keyword.control.flow.anchor.yaml / .alias.yaml — both start at index 6 here
        assertEquals(testColors.keyword, highlight("base: &base").colorAt(6))
        assertEquals(testColors.keyword, highlight("copy: *base").colorAt(6))
    }

    @Test
    fun `tag handles are colored as keyword`() = runTest {
        // The shorthand and verbatim forms both start at index 5
        assertEquals(testColors.keyword, highlight("key: !!str 1").colorAt(5))
        assertEquals(testColors.keyword, highlight("key: !<tag:x> 1").colorAt(5))
    }

    @Test
    fun `language constants are colored as constant`() = runTest {
        for (constant in listOf("true", "True", "TRUE", "false", "null", "NULL", "~")) {
            // Each appears as a value at index 5 in "key: <constant>"
            assertEquals(
                testColors.constant,
                highlight("key: $constant").colorAt(5),
                "'$constant' should be colored as constant",
            )
        }
    }

    @Test
    fun `yaml 1_1 boolean spellings are not constants`() = runTest {
        // The 1.2 grammar recognizes only true/false/null/~; yes/no/on/off are 1.1 and absent from it
        for (word in listOf("yes", "no", "on", "off")) {
            assertNull(highlight("key: $word").colorAt(5), "'$word' should not be colored")
        }
    }

    @Test
    fun `numbers are colored as number`() = runTest {
        for (number in listOf("42", "-1", "3.14", "1e10", "0x1F", "0o755", ".inf")) {
            assertEquals(
                testColors.number,
                highlight("key: $number").colorAt(5),
                "'$number' should be colored as number",
            )
        }
    }
}

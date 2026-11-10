// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting

import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.intui.standalone.code.highlighting.BuiltInLanguageGrammars
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * A rule like `"(?:[^"\\]|\\.)*"` makes `java.util.regex` recurse once per repetition, so one long token overflows the
 * stack of the highlighting thread. Each case here is one long token, such as a minified JSON payload in a string.
 *
 * The highlighter runs on a thread with a small stack. A rule that recurses per character overflows it at a few hundred
 * characters, so short tokens catch it and the test stays fast.
 */
internal class LongTokenHighlightingTest {
    private val smallStackDispatcher =
        Executors.newSingleThreadExecutor { task -> Thread(null, task, "small-stack-highlighter", STACK_SIZE_BYTES) }
            .asCoroutineDispatcher()

    private val highlighter = SimpleCodeHighlighter(testColors, emptyList(), smallStackDispatcher)

    private val body = "a".repeat(TOKEN_LENGTH)
    private val lines = "a\n".repeat(TOKEN_LENGTH / 2)

    private val shapes =
        mapOf(
            "double-quoted string" to "x = \"$body\"",
            "single-quoted string" to "x = '$body'",
            "backtick string" to "x = `$body`",
            "string with escapes" to "x = \"${"a\\\"".repeat(TOKEN_LENGTH / 3)}\"",
            "unterminated string" to "x = \"$body",
            "triple-quoted string" to "x = \"\"\"$body\"\"\"",
            "multi-line triple-quoted string" to "x = \"\"\"$lines\"\"\"",
            "triple single-quoted string" to "x = '''$body'''",
            "JSON string value" to "{\"data\": \"$body\"}",
            "JSON property key" to "{\"$body\": 1}",
            "block comment" to "/* $body */",
            "multi-line block comment" to "/*\n$lines*/",
            "doc comment" to "/** $body */",
            "line comment" to "// $body",
            "hash comment" to "# $body",
            "SQL comment" to "-- $body",
            "HTML comment" to "<!-- $body -->",
            "HTML attribute" to "<a href=\"$body\">",
            "CSS url" to "a { background: url($body); }",
            "CSS class selector" to ".$body { }",
            "CSS ID selector" to "#$body { }",
            "CSS custom property" to "a { --$body: 1; }",
            "CSS attribute selector" to "[$body] { }",
            "CSS attribute selector with escapes" to "[${"a\\-".repeat(MIXED_TOKEN_LENGTH / 3)}] { }",
            "template string" to $$"x = `${a}$$body${b}`",
            "identifier" to body,
            "number" to "1".repeat(TOKEN_LENGTH),
            "whitespace" to " ".repeat(TOKEN_LENGTH),
        )

    @AfterEach
    fun closeDispatcher() {
        smallStackDispatcher.close()
    }

    @TestFactory
    fun `long tokens highlight without a stack overflow`(): List<DynamicTest> =
        BuiltInLanguageGrammars.all.flatMap { grammar ->
            shapes.map { (shape, code) ->
                DynamicTest.dynamicTest("${grammar.name}: $shape") {
                    val highlighted = runBlocking { highlighter.highlight(code, grammar.name).first() }

                    assertEquals(code, highlighted.text)
                }
            }
        }

    private companion object {
        const val STACK_SIZE_BYTES = 256L * 1024L

        // A greedy loop over a group recurses once per repetition. It overflows a 256 KB stack below 250 characters.
        const val TOKEN_LENGTH = 2_000

        // A greedy loop over an atomic group recurses only when the length of one repetition changes. Atoms of 1 and 2
        // characters, like `a\-`, overflow a 256 KB stack at about 3,500 characters.
        const val MIXED_TOKEN_LENGTH = 5_000
    }
}

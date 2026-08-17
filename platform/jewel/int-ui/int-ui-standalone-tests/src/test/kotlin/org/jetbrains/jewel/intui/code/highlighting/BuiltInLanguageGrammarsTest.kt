// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting

import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.code.highlighting.BuiltInLanguageGrammars
import org.junit.jupiter.api.Test

internal class BuiltInLanguageGrammarsTest {
    // A \G whose backslash isn't itself escaped, so a literal \\G doesn't count as the anchor.
    private val backtrackingAnchor = Regex("""(?<!\\)(?:\\\\)*\\G""")

    @Test
    fun `no built-in grammar uses the backslash-G anchor`() {
        // \G anchors to the search start, which invalidates the next-match cache in SimpleCodeHighlighter and
        // produces wrong spans. See docs/standalone-code-highlighting.md.
        val offenders =
            BuiltInLanguageGrammars.all.flatMap { grammar ->
                grammar.rules
                    .map { it.pattern }
                    .filter { backtrackingAnchor.containsMatchIn(it) }
                    .map { grammar.name to it }
            }

        assertTrue(
            offenders.isEmpty(),
            "These built-in rules use \\G, which breaks the next-match cache in SimpleCodeHighlighter:\n" +
                offenders.joinToString("\n") { (language, pattern) -> "  $language: $pattern" },
        )
    }
}

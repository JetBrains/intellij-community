// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.unit

import com.intellij.platform.lsp.impl.util.LspSnippetParser
import com.intellij.platform.lsp.impl.util.LspSnippetTemplate
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Large snippets of the constructs that make a parser backtrack or recurse.
 */
class LspSnippetsPerformanceTest {
  private val pieces = listOf(
    $$"${1:", // placeholders that do not close, each one inside the one before
    $$"${VAR:", // variable defaults that do not close
    $$"${1:${2:x}", // placeholders that do not close around ones that do
    $$"${1|a,b\\,c", // choices that do not close
    $$"${1/(.*)/${1:x", // transforms whose format entries do not close
    $$"${1/a/${", // transforms with a format `${` that starts no entry
    $$"${1/a/b/c", // transforms without the closing `}`
    $$"${2:${3:x}}$2${4|p,q|}$TM_FILENAME${UNKNOWN}${TM_LINE_INDEX:d}", // closed constructs, mirrors and variables
    $$"\\$\\}\\\\$ $$ ${ ${} ${x $_", // escapes and `$` that starts nothing
  )

  @Test
  fun `a large snippet of each kind parses in linear time`() {
    LspSnippetParser.parse("warm up")
    val snippets = pieces.map { repeatTo(it, 100_000) } + repeatTo(pieces.joinToString(""), 100_000)
    assertTimeoutPreemptively(Duration.ofSeconds(5)) {
      for (snippet in snippets) {
        val parts = LspSnippetParser.parse(snippet)
        LspSnippetTemplate.of(parts) { name -> if (name.startsWith("TM_")) name.lowercase() else null }.plainText()
        LspSnippetTemplate.of(parts) { "" }
      }
    }
  }

  private fun repeatTo(piece: String, length: Int): String = piece.repeat(length / piece.length + 1)
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The lexer must agree with the reader on what a comment, a pattern, and a blank line are. See `AnalysisIgnorePatternTest`.
 */
class AnalysisIgnoreLexerTest {

  @Test
  fun `a hash in column 0 starts a comment`() {
    assertTokens(
      "# a comment\nbuild/\n",
      "COMMENT ('# a comment')",
      "WHITE_SPACE ('\\n')",
      "PATTERN ('build/')",
      "WHITE_SPACE ('\\n')",
    )
  }

  @Test
  fun `a comment keeps its trailing spaces`() {
    assertTokens("# a comment  ", "COMMENT ('# a comment  ')")
  }

  @Test
  fun `an indented hash is a pattern`() {
    assertTokens("  # note", "PATTERN ('  # note')")
  }

  @Test
  fun `a hash after content is a pattern`() {
    assertTokens("build#tmp", "PATTERN ('build#tmp')")
  }

  @Test
  fun `trailing spaces are white space`() {
    assertTokens(
      "build  \n",
      "PATTERN ('build')",
      "WHITE_SPACE ('  \\n')",
    )
  }

  @Test
  fun `a tab is content`() {
    assertTokens(
      "build\t\n\t\n",
      "PATTERN ('build\\t')",
      "WHITE_SPACE ('\\n')",
      "PATTERN ('\\t')",
      "WHITE_SPACE ('\\n')",
    )
  }

  @Test
  fun `blank lines join one white space`() {
    assertTokens(
      "a\n\n   \n\nb",
      "PATTERN ('a')",
      "WHITE_SPACE ('\\n\\n   \\n\\n')",
      "PATTERN ('b')",
    )
  }

  @Test
  fun `leading blank lines are white space`() {
    assertTokens(
      "   \n\n# c",
      "WHITE_SPACE ('   \\n\\n')",
      "COMMENT ('# c')",
    )
  }

  @Test
  fun `a CRLF break is white space`() {
    assertTokens(
      "# c\r\nbuild\r\n",
      "COMMENT ('# c')",
      "WHITE_SPACE ('\\r\\n')",
      "PATTERN ('build')",
      "WHITE_SPACE ('\\r\\n')",
    )
  }

  @Test
  fun `an empty text has no token`() {
    assertTokens("")
  }

  @Test
  fun `a restart in the middle of a line gives no comment`() {
    assertEquals(listOf("PATTERN ('#x')"), tokensOf("build#x", start = 5))
  }

  @Test
  fun `a restart at any token start gives the same tokens`() {
    val text = "# c  \n  build/  \n\n   \n\tout\r\n**/x #y\n"
    val all = tokensOf(text)
    for ((index, start) in tokenStartsOf(text).withIndex()) {
      assertEquals(all.subList(index, all.size), tokensOf(text, start), "restart at $start")
    }
  }

  private fun assertTokens(text: String, vararg expected: String) {
    assertEquals(expected.toList(), tokensOf(text))
  }
}

private fun tokensOf(text: String, start: Int = 0): List<String> {
  val lexer = AnalysisIgnoreLexer()
  lexer.start(text, start, text.length)
  return buildList {
    while (lexer.tokenType != null) {
      add("${lexer.tokenType} ('${escape(text.substring(lexer.tokenStart, lexer.tokenEnd))}')")
      lexer.advance()
    }
  }
}

private fun tokenStartsOf(text: String): List<Int> {
  val lexer = AnalysisIgnoreLexer()
  lexer.start(text, 0, text.length)
  return buildList {
    while (lexer.tokenType != null) {
      add(lexer.tokenStart)
      lexer.advance()
    }
  }
}

private fun escape(text: String): String = text.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

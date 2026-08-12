// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [parseGfmMarkdownToAst] is the syntax authority two callers share: the HTML rendering below, and a caller
 * that decides from the same tree which fenced blocks it may replace with a component of its own. Both halves
 * are pinned here — the tokens a fence is made of, and the HTML the converter has always produced.
 */
class MarkdownToHtmlConverterTest {
  @Test
  fun `complete fence exposes its delimiters, info string and content`() {
    val fence = singleCodeFence("```kotlin\nval x = 1\n```\n")

    assertEquals("```", fence.tokenText(MarkdownTokenTypes.CODE_FENCE_START))
    assertEquals("kotlin", fence.tokenText(MarkdownTokenTypes.FENCE_LANG))
    assertEquals("val x = 1", fence.tokenText(MarkdownTokenTypes.CODE_FENCE_CONTENT))
    assertEquals("```", fence.tokenText(MarkdownTokenTypes.CODE_FENCE_END))
  }

  /** The line break after the closing delimiter belongs to the enclosing block, not to the fence. */
  @Test
  fun `fence ends at its closing delimiter`() {
    val fence = singleCodeFence("```kotlin\nval x = 1\n```\nafter\n")

    assertEquals("```kotlin\nval x = 1\n```", fence.text())
    assertEquals('\n', fence.textAfter())
  }

  @Test
  fun `incomplete fence has no end token`() {
    val fence = singleCodeFence("```kotlin\nval x = 1\n")

    assertEquals("kotlin", fence.tokenText(MarkdownTokenTypes.FENCE_LANG))
    assertEquals("val x = 1", fence.tokenText(MarkdownTokenTypes.CODE_FENCE_CONTENT))
    assertNull(fence.tokenText(MarkdownTokenTypes.CODE_FENCE_END))
  }

  /** An opening delimiter with nothing after it yet is already a fence, and carries no line break. */
  @Test
  fun `fence opened at the end of the text has neither content nor line break`() {
    val fence = singleCodeFence("```kotlin")

    assertEquals("kotlin", fence.tokenText(MarkdownTokenTypes.FENCE_LANG))
    assertNull(fence.tokenText(MarkdownTokenTypes.EOL))
    assertNull(fence.tokenText(MarkdownTokenTypes.CODE_FENCE_END))
  }

  /** The info string is the rest of the opening line, verbatim — trailing spaces included. */
  @Test
  fun `info string is not trimmed`() {
    assertEquals("kotlin   ", singleCodeFence("```kotlin   \nbody\n```\n").tokenText(MarkdownTokenTypes.FENCE_LANG))
  }

  /** A blank line inside a fence produces no content token, so content has to be read as a range. */
  @Test
  fun `blank content line produces no content token`() {
    val fence = singleCodeFence("```\nA\n\nB\n```\n")

    assertEquals(listOf("A", "B"), fence.tokenTexts(MarkdownTokenTypes.CODE_FENCE_CONTENT))
  }

  /** A fence in a list item is not a child of the root, which is how a caller can tell it apart. */
  @Test
  fun `nested fence is not a root level node`() {
    val tree = parseGfmMarkdownToAst("- item\n  ```kotlin\n  body\n  ```\n", parseInlines = false)

    assertTrue(tree.children.none { it.type == MarkdownElementTypes.CODE_FENCE })
    assertEquals(1, tree.codeFences().size)
  }

  @Test
  fun `skipping inline parsing keeps the block structure`() {
    val text = "para **bold**\n\n```kotlin\nval x = 1\n```\n"
    val withInlines = parseGfmMarkdownToAst(text).codeFences().single()
    val withoutInlines = parseGfmMarkdownToAst(text, parseInlines = false).codeFences().single()

    assertEquals(withInlines.startOffset, withoutInlines.startOffset)
    assertEquals(withInlines.endOffset, withoutInlines.endOffset)
    assertEquals(withInlines.children.map(ASTNode::type), withoutInlines.children.map(ASTNode::type))
  }

  /** Extracting the parser out of the converter must not move the generated HTML. */
  @Test
  fun `converter output is unchanged`() {
    assertEquals(
      "<p>Intro</p><pre><code class=\"language-kotlin\">val x = 1\n</code></pre><p>After <strong>bold</strong>.</p>",
      convertMarkdownToHtml("Intro\n```kotlin\nval x = 1\n```\nAfter **bold**.\n"),
    )
  }

  private fun singleCodeFence(text: String): CodeFence =
    CodeFence(text, parseGfmMarkdownToAst(text, parseInlines = false).codeFences().single())

  private class CodeFence(private val text: String, private val node: ASTNode) {
    fun text(): String = text.substring(node.startOffset, node.endOffset)

    fun textAfter(): Char? = text.getOrNull(node.endOffset)

    fun tokenText(type: IElementType): String? = tokenTexts(type).firstOrNull()

    fun tokenTexts(type: IElementType): List<String> = node.children
      .filter { it.type == type }
      .map { text.substring(it.startOffset, it.endOffset) }
  }

  private fun ASTNode.codeFences(): List<ASTNode> = buildList {
    val pending = ArrayDeque(listOf(this@codeFences))
    while (pending.isNotEmpty()) {
      val node = pending.removeFirst()
      if (node.type == MarkdownElementTypes.CODE_FENCE) add(node) else pending.addAll(node.children)
    }
  }
}

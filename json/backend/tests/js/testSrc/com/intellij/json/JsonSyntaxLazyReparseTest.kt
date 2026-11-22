// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.syntax.JsonLazyParsing
import com.intellij.json.syntax.JsonSyntaxElementTypes
import com.intellij.json.syntax.JsonSyntaxLexer
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.canLazyNodeBeReparsedIncrementally
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.tree.SyntaxNode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * The node reparse check of a lazy JSON container in the syntax library.
 *
 * The node of the context has the old text, as in `KmpSyntaxNode.tryReparse`.
 */
internal class JsonSyntaxLazyReparseTest {
  @Test
  fun testAcceptsAnEditInACorrectlyNestedArray() {
    assertTrue(canReparse(JsonSyntaxElementTypes.ARRAY, oldText = "[1, 2]", newText = "[1, 3]"))
  }

  @Test
  fun testRefusesAnArrayWithAnOpenBraceInTheOldText() {
    // The new text nests correctly, so only the check of the old text refuses it.
    assertFalse(canReparse(JsonSyntaxElementTypes.ARRAY, oldText = "[1, {]", newText = "[1, {}]"))
  }

  @Test
  fun testRefusesAnObjectWithAnOpenBracketInTheOldText() {
    assertFalse(canReparse(JsonSyntaxElementTypes.OBJECT, oldText = "{\"a\": [}", newText = "{\"a\": []}"))
  }

  private fun canReparse(type: SyntaxElementType, oldText: String, newText: String): Boolean {
    assumeTrue(JsonLazyParsing, "lazy parsing is off")
    val cancellationProvider = CancellationProvider { }
    val tokens = performLexing(newText, JsonSyntaxLexer(), cancellationProvider, null)
    val builder = SyntaxTreeBuilderFactory.builder(newText, tokens, emptySet(), emptySet()).build()
    val context = LazyParsingContext(OldNode(oldText, type), tokens, builder, cancellationProvider)
    return canLazyNodeBeReparsedIncrementally(context)
  }

  private class OldNode(override val text: CharSequence, override val type: SyntaxElementType) : SyntaxNode {
    override val startOffset: Int get() = 0
    override val endOffset: Int get() = text.length
    override val errorMessage: String? get() = null
    override val language: SyntaxLanguage get() = SyntaxLanguage("JSON")
    override fun parent(): SyntaxNode? = null
    override fun prevSibling(): SyntaxNode? = null
    override fun nextSibling(): SyntaxNode? = null
    override fun firstChild(): SyntaxNode? = null
    override fun lastChild(): SyntaxNode? = null
    override fun childByOffset(offset: Int): SyntaxNode? = null
  }
}
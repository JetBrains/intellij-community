// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.todo

import com.intellij.openapi.Disposable
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.id.IdDataConsumer
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.cache.impl.idCache.JavaIdIndexer
import com.intellij.psi.impl.cache.impl.idCache.JavaTodoIndexer
import com.intellij.psi.search.IndexPattern
import com.intellij.psi.search.IndexPatternProvider
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class JavaTodoIndexingTest {
  @Test
  fun countsOnlyComments(@TestDisposable disposable: Disposable) {
    val pattern = IndexPattern("\\bTODO\\b", true)
    ExtensionTestUtil.maskExtensions(IndexPatternProvider.EP_NAME, listOf(IndexPatternProvider { arrayOf(pattern) }), disposable)
    val consumer = OccurrenceConsumer(null, true)
    val lexer = JavaTodoIndexer().createLexer(consumer)
    lexer.start("""
      class C {
        String text = "TODO";
        int TODO;
        // TODO TODO
        /* TODO */
        /** TODO */
      }
    """.trimIndent())
    while (lexer.tokenType != null) lexer.advance()
    assertEquals(4, consumer.getOccurrenceCount(pattern))
  }

  @Test
  fun respectsCustomPatternsAndCaseSensitivity(@TestDisposable disposable: Disposable) {
    val sensitive = IndexPattern("\\bFIXME\\b", true)
    val insensitive = IndexPattern("\\breview\\b", false)
    val provider = IndexPatternProvider { arrayOf(sensitive, insensitive) }
    ExtensionTestUtil.maskExtensions(IndexPatternProvider.EP_NAME, listOf(provider), disposable)
    val consumer = OccurrenceConsumer(null, true)
    val lexer = JavaTodoIndexer().createLexer(consumer)
    lexer.start("// FIXME fixme REVIEW review\n/* FIXME REVIEW */")
    while (lexer.tokenType != null) lexer.advance()
    assertEquals(2, consumer.getOccurrenceCount(sensitive))
    assertEquals(3, consumer.getOccurrenceCount(insensitive))
  }

  @Test
  fun identifierIndexingSkipsTodoPatterns(@TestDisposable disposable: Disposable) {
    val provider = IndexPatternProvider { error("Identifier indexing must not request TODO patterns") }
    ExtensionTestUtil.maskExtensions(IndexPatternProvider.EP_NAME, listOf(provider), disposable)
    val words = IdDataConsumer()
    val consumer = OccurrenceConsumer(words, /* needToDo = */ false)
    val lexer = JavaIdIndexer.createIndexingLexer(consumer)
    lexer.start("class Example { String field = \"literal\"; /* comment TODO */ }")
    while (lexer.tokenType != null) lexer.advance()
    val entries = words.result
    assertEquals(UsageSearchContext.IN_CODE.toInt(), entries[IdIndexEntry("Example", true)])
    assertEquals(UsageSearchContext.IN_CODE.toInt(), entries[IdIndexEntry("field", true)])
    assertEquals(UsageSearchContext.IN_COMMENTS.toInt(), entries[IdIndexEntry("comment", true)])
    assertEquals(UsageSearchContext.IN_STRINGS.toInt() or UsageSearchContext.IN_FOREIGN_LANGUAGES.toInt(),
                 entries[IdIndexEntry("literal", true)])
    assertEquals(0, consumer.getOccurrenceCount(IndexPattern("\\bTODO\\b", true)))
  }
}

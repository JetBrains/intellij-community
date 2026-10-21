// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.experimental.LineageMismatchException
import com.intellij.openapi.editor.impl.experimental.EventGraphImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The branch that a document core publishes. The core keeps no history, so each change publishes a branch with no
 * events. The seqs of its agent still grow with every change.
 */
internal class DocumentCoreBranchTest {

  @Test
  fun `a new document publishes a branch with no events`() {
    val document = DocumentImpl("abc", true)
    assertNoHistory(document, "a new document")
    assertEquals("abc", document.core.snapshot().text().string())
  }

  @Test
  fun `each kind of change publishes a branch with no events`() {
    val document = DocumentImpl("abc\ndef\n", true)
    val changes = listOf<Pair<String, (DocumentImpl) -> Unit>>(
      "an insert" to { it.insertString(1, "x") },
      "a delete" to { it.deleteString(0, 1) },
      "a replace" to { it.replaceString(1, 3, "yz") },
      "a move" to { it.moveText(0, 2, 6) },
      "a whole-text replace" to { it.setText("new text\n") },
      "a whole-text replace with an immutable text" to { it.setText(DocumentImpl("other\n").immutableCharSequence) },
      "a reload" to { it.replaceText("reloaded\n", 42) },
    )
    for ((name, change) in changes) {
      change(document)
      assertNoHistory(document, name)
    }
    assertEquals("reloaded\n", document.text)
  }

  @Test
  fun `a console that drops old text publishes a branch with no events`() {
    val document = DocumentImpl("12345", true)
    document.setCyclicBufferSize(5)
    repeat(3) {
      document.insertString(document.textLength, "ab")
    }
    assertEquals("babab", document.text)
    assertNoHistory(document, "the console")
  }

  @Test
  fun `a metadata change that changes nothing keeps the published branch`() {
    val document = DocumentImpl("a\nb", true)
    document.insertString(0, "x")
    val edited = document.core.snapshot()
    document.modificationStamp = document.modificationStamp
    assertSame(edited, document.core.snapshot())
    document.clearLineModificationFlags()
    val cleared = document.core.snapshot()
    assertNoHistory(document, "the clear")
    document.clearLineModificationFlags()
    assertSame(cleared, document.core.snapshot())
  }

  @Test
  fun `the seqs of the core agent grow with each change`() {
    val document = DocumentImpl("abc", true)
    repeat(4) {
      document.insertString(0, "xy")
    }
    document.deleteString(0, 3)
    val branch = document.core.snapshot()
    val graph = EventGraphImpl.implOf(branch.graph())
    assertEquals("main", branch.agent().toString())
    assertEquals(4L * 2 + 3, graph.nextSeqFor(branch.agent()))
  }

  @Test
  fun `the branches of two documents never merge`() {
    val first = DocumentImpl("abc", true).core.snapshot()
    val second = DocumentImpl("abc", true).core.snapshot()
    assertThrows(LineageMismatchException::class.java) {
      first.merge(second)
    }
  }

  @Test
  fun `a published branch prints its identity and its mod state`() {
    val document = DocumentImpl("abc", true)
    document.insertString(0, "x")
    val branch = document.core.snapshot()
    val printed = branch.toString()
    val id = Integer.toHexString(System.identityHashCode(branch))
    assertTrue(printed.startsWith("DocumentBranch@$id{")) { printed }
    assertTrue(printed.contains("stamp=${document.modificationStamp}, sequence=1")) { printed }
  }

  /**
   * Fails unless the published branch of [document] holds no event, and its base text is its text.
   */
  private fun assertNoHistory(document: DocumentImpl, name: String) {
    val branch = document.core.snapshot()
    val graph = branch.graph()
    assertEquals(0, graph.size(), name)
    assertEquals(0, graph.runCount(), name)
    assertEquals(branch.text().string(), graph.replay().string(), name)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests how [DocumentBranchImpl] records a move op. A valid move keeps its move offset, and any other move
 * records as a plain op, so a faulty move offset from a caller never fails an edit.
 */
internal class DocBranchImplMoveTest {

  @Test
  fun `a valid move records its move offsets`() {
    val moveInsert = DocumentOp.insertOp(4, "01", 0)
    val moveDelete = DocumentOp.deleteOp(0, 2, 4)
    val moved = EDITOR.applyOp(moveInsert).applyOp(moveDelete)
    assertEquals("2301456789", moved.text().string())
    assertEquals(listOf(moveInsert, moveDelete), recordedOps(BASE, moved))
  }

  @Test
  fun `a move offset that does not name the moved text records as a plain op`() {
    // The source of the copied "01" is at 0, not at 1.
    val wrongSource = EDITOR.applyOp(DocumentOp.insertOp(4, "01", 1))
    assertEquals(listOf(DocumentOp.insertOp(4, "01")), recordedOps(BASE, wrongSource))
    // The copy of the deleted "01" is at 4, not at 5.
    val moveInsert = DocumentOp.insertOp(4, "01", 0)
    val wrongCopy = EDITOR.applyOp(moveInsert).applyOp(DocumentOp.deleteOp(0, 2, 5))
    assertEquals("2301456789", wrongCopy.text().string())
    assertEquals(listOf(moveInsert, DocumentOp.deleteOp(0, 2)), recordedOps(BASE, wrongCopy))
  }

  @Test
  fun `a move offset outside the text records as a plain op`() {
    val pastEndInsert = EDITOR.applyOp(DocumentOp.insertOp(10, "01", 11))
    assertEquals(listOf(DocumentOp.insertOp(10, "01")), recordedOps(BASE, pastEndInsert))
    val negative = EDITOR.applyOp(DocumentOp.deleteOp(0, 2, -1))
    assertEquals(listOf(DocumentOp.deleteOp(0, 2)), recordedOps(BASE, negative))
    val pastEndDelete = EDITOR.applyOp(DocumentOp.deleteOp(0, 2, 9))
    assertEquals(listOf(DocumentOp.deleteOp(0, 2)), recordedOps(BASE, pastEndDelete))
  }

  @Test
  fun `a move that overlaps its own text records as a plain op`() {
    // The text at the move offset is the moved text, but it overlaps the text that the op changes.
    val base = DocumentBranch.createBranch("aaaa", Agent.createAgent("base"))
    val insert = base.fork(A).applyOp(DocumentOp.insertOp(1, "aa", 2))
    assertEquals(listOf(DocumentOp.insertOp(1, "aa")), recordedOps(base, insert))
    val delete = base.fork(A).applyOp(DocumentOp.deleteOp(0, 2, 1))
    assertEquals(listOf(DocumentOp.deleteOp(0, 2)), recordedOps(base, delete))
  }

  /**
   * The ops that [branch] recorded after the history of [base], one per run.
   */
  private fun recordedOps(base: DocumentBranch, branch: DocumentBranch): List<DocumentOp.Text> {
    val graph = EventGraphImpl.implOf(branch.graph())
    val ops = ArrayList<DocumentOp.Text>()
    var lv = base.graph().size()
    while (lv < graph.size()) {
      val run = graph.runAt(lv)
      ops.add(run.event.op())
      lv = run.lvEnd()
    }
    return ops
  }

  private companion object {
    val A: Agent = Agent.createAgent("a")
    val BASE: DocumentBranch = DocumentBranch.createBranch("0123456789", Agent.createAgent("base"))

    /**
     * A fork of [BASE] under another agent, so that no op of a test extends the run of [BASE].
     */
    val EDITOR: DocumentBranch = BASE.fork(A)
  }
}

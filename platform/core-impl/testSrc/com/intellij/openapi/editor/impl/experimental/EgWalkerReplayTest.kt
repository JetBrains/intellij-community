// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocBranch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the precondition of [EgWalkerReplay.mergeInto] that no public call can break.
 */
internal class EgWalkerReplayTest {

  /**
   * The graph of "a" holds its own delete at the lv 0 and the delete of "b" at the lv 1. A merge
   * into the version of "b" would walk the lv 1 first and the new lv 0 after it, which the delete
   * targets cannot take. The merge must say so, and not fail inside them.
   */
  @Test
  fun `a merge into a branch whose own units sit above the new units is rejected`() {
    val base = DocBranch.createBranch("abcdef", Agent.createAgent("a"))
    val a = base.applyOp(DocumentOp.deleteOp(0, 1))
    val b = base.fork(Agent.createAgent("b")).applyOp(DocumentOp.deleteOp(5, 1))
    val merged = EventGraphImpl.implOf(a.graph()).mergeFromImpl(EventGraphImpl.implOf(b.graph())).graph()
    val failure = assertThrows(IllegalArgumentException::class.java) {
      EgWalkerReplay.mergeInto(merged, LvVersion(intArrayOf(1)), BatchingSink(b.text()))
    }
    assertTrue(failure.message.orEmpty().contains("do not all sit above"), failure.message)
    // The direction that a merge uses works on the same graph.
    val sink = BatchingSink(a.text())
    EgWalkerReplay.mergeInto(merged, EventGraphImpl.implOf(a.graph()).lvVersion(), sink)
    assertEquals("bcde", sink.result().string())
  }

  /**
   * No graph is large enough to reach the bound, so the test calls the count directly.
   */
  @Test
  fun `a placeholder that overflows the unit space is rejected`() {
    val lastUnit = LvVersion(intArrayOf(Int.MAX_VALUE - 1))
    assertEquals(Int.MAX_VALUE, EgWalkerReplay.placeholderCount(0, lastUnit))
    assertThrows(IllegalArgumentException::class.java) {
      EgWalkerReplay.placeholderCount(1, lastUnit)
    }
  }
}

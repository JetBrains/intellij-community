// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.EventIdClashException
import com.intellij.openapi.editor.ex.experimental.TrimmedHistoryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Seqs that pass [INT_SEQ_LIMIT]. A seq indexes every unit that its agent authored, so a long-lived
 * branch passes that limit even when it trims after each op.
 */
internal class DocBranchSeqSpaceTest {

  @Test
  fun `closed runs that cross the Int limit keep their ids`() {
    val steps = crossingSteps()
    val last = steps.last()
    val agent = last.agent()
    val graph = EventGraphImpl.implOf(last.graph())
    graph.checkInvariants()
    assertEquals(INT_SEQ_LIMIT + 14, graph.nextSeqFor(agent))
    // One closed run holds seqs on both sides of the limit.
    val spanning = (0 until graph.size()).map { lv ->
      graph.runAt(lv)
    }.distinct().filter { run ->
      run.event.seq() < INT_SEQ_LIMIT && run.endSeq() > INT_SEQ_LIMIT
    }
    assertEquals(1, spanning.size)
    assertTrue(spanning[0].lvEnd() < graph.size()) { "the run ${spanning[0]} is the newest run" }
    // Every unit finds its own lv by its id.
    for (lv in 0 until graph.size()) {
      val seq = graph.runAt(lv).seqAt(lv)
      assertEquals(lv, graph.lvOfUnit(agent, seq)) { "the unit ($agent, $seq)" }
    }
    // The versions name heads on both sides of the limit.
    for ((index, step) in steps.withIndex()) {
      val replayed = last.graph().replay(step.version())
      assertEquals(step.text().string(), replayed.string()) { "the version after step $index" }
    }
  }

  @Test
  fun `a fork from inside the crossing merges in both directions`() {
    val steps = crossingSteps()
    val last = steps.last()
    // The fork knows the runs up to the one that spans the limit. So the merge into it starts inside
    // the closed runs of the other branch.
    val fork = steps[3].fork(Agent.createAgent("fork"))
    val forkEdited = fork.applyOp(DocumentOp.insertOp(fork.text().length(), "F"))
    val forward = last.merge(forkEdited)
    val backward = forkEdited.merge(last)
    assertEquals("${last.text().string()}F", forward.text().string())
    assertEquals(forward.text().string(), backward.text().string())
    assertEquals(forward.version(), backward.version())
  }

  @Test
  fun `a fork above the Int limit merges in both directions`() {
    val main = aboveLimit()
    val fork = main.fork(Agent.createAgent("fork"))
    val mainEdited = main.applyOp(DocumentOp.insertOp(0, "M"))
    val forkEdited = fork.applyOp(DocumentOp.insertOp(fork.text().length(), "F"))
    val forward = mainEdited.merge(forkEdited)
    val backward = forkEdited.merge(mainEdited)
    assertEquals("M${main.text().string()}F", forward.text().string())
    assertEquals(forward.text().string(), backward.text().string())
    assertEquals(forward.version(), backward.version())
    assertEquals(mainEdited.text().string(), forward.graph().replay(mainEdited.version()).string())
    assertEquals(forkEdited.text().string(), backward.graph().replay(forkEdited.version()).string())
  }

  @Test
  fun `an id clash above the Int limit names its seq`() {
    val main = aboveLimit()
    val first = main.applyOp(DocumentOp.insertOp(0, "x"))
    val second = main.newInstance().applyOp(DocumentOp.insertOp(0, "y"))
    val clash = assertThrows(EventIdClashException::class.java) {
      first.merge(second)
    }
    assertEquals(INT_SEQ_LIMIT + 14, clash.seq())
  }

  @Test
  fun `a stale fork above the Int limit merges in neither direction`() {
    val main = aboveLimit()
    val fork = main.fork(Agent.createAgent("fork")).applyOp(DocumentOp.insertOp(0, "F"))
    val edited = main.applyOp(DocumentOp.insertOp(0, "M"))
    val trimmed = edited.trimmedTo(edited.version())
    assertThrows(TrimmedHistoryException::class.java) {
      trimmed.merge(fork)
    }
    assertThrows(TrimmedHistoryException::class.java) {
      fork.merge(trimmed)
    }
  }

  private companion object {
    /**
     * [NEAR_LIMIT_BRANCH], then seven ops with no trim, one branch per step. The inserts and the
     * deletes take turns, so each op closes the run before it. The third op spans [INT_SEQ_LIMIT],
     * and the next seq ends at `INT_SEQ_LIMIT + 14`.
     */
    fun crossingSteps(): List<DocumentBranch> {
      val steps = arrayListOf(NEAR_LIMIT_BRANCH)
      repeat(7) { step ->
        val op = if (step % 2 == 0) {
          DocumentOp.insertOp(1, "wxyz")
        } else {
          DocumentOp.deleteOp(1, 2)
        }
        steps.add(steps.last().applyOp(op))
      }
      return steps
    }

    /**
     * The last of [crossingSteps], trimmed to its version. Its next seq is `INT_SEQ_LIMIT + 14`.
     */
    fun aboveLimit(): DocumentBranch {
      val last = crossingSteps().last()
      return last.trimmedTo(last.version())
    }
  }
}

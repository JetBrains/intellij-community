// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocBranch
import com.intellij.openapi.editor.ex.experimental.TrimmedHistoryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [EventGraphImpl.trimmedTo] from inside: the seqs that a trim leaves, and the base version as
 * the empty lv version.
 */
internal class EventGraphTrimTest {

  @Test
  fun `a trim to the version keeps no unit and starts each agent at its next seq`() {
    val branch = joinedBranch()
    val graph = impl(branch)
    val trimmed = graph.trimmedTo(graph.lvVersion(), branch.text())
    assertEquals(0, trimmed.size())
    assertEquals(0, trimmed.runCount())
    assertEquals(LvVersion.ROOT, trimmed.lvVersion())
    assertEquals(graph.version(), trimmed.version())
    for (agent in listOf(L, R)) {
      assertEquals(graph.nextSeqFor(agent), trimmed.startSeqOf(agent))
      assertEquals(graph.nextSeqFor(agent), trimmed.nextSeqFor(agent))
      assertEquals(graph.nextSeqFor(agent), trimmed.summarize().endSeq(agent))
    }
    trimmed.checkInvariants()
  }

  @Test
  fun `an append after a trim continues the seqs of its agent`() {
    val branch = joinedBranch()
    val graph = impl(branch)
    val trimmed = graph.trimmedTo(graph.lvVersion(), branch.text())
    val nextSeq = trimmed.nextSeqFor(L)
    val stale = EventImpl(L, 0, DocumentOp.insertOp(0, "x"))
    assertThrows(IllegalArgumentException::class.java) {
      trimmed.appendAtVersion(stale)
    }
    val appended = trimmed.appendAtVersion(EventImpl(L, nextSeq, DocumentOp.insertOp(0, "x")))
    assertEquals(1, appended.size())
    assertEquals(nextSeq + 1, appended.nextSeqFor(L))
    assertTrue(appended.hasTrimmed(L, nextSeq - 1))
    assertFalse(appended.hasTrimmed(L, nextSeq))
    appended.checkInvariants()
  }

  /**
   * After the trim, the only run of the left agent is the tail of the graph. The summary covers the
   * right agent, but not the units of the left agent that the trim dropped.
   */
  @Test
  fun `a summary below the start seqs is rejected, also for the tail`() {
    val branch = joinedBranch()
    val graph = impl(branch)
    val trimmed = graph.trimmedTo(graph.lvVersion(), branch.text())
    val nextSeq = trimmed.nextSeqFor(L)
    val appended = trimmed.appendAtVersion(EventImpl(L, nextSeq, DocumentOp.insertOp(0, "x")))
    val coversRight = VersionSummary(mapOf(R to trimmed.startSeqOf(R)))
    val failure = assertThrows(IllegalArgumentException::class.java) {
      appended.newRunStarts(coversRight)
    }
    assertTrue(failure.message.orEmpty().contains("the seqs of left below 0"), failure.message)
    val coversBoth = VersionSummary(mapOf(L to nextSeq, R to trimmed.startSeqOf(R)))
    assertEquals(listOf(0), appended.newRunStarts(coversBoth).toList())
  }

  @Test
  fun `the base version and the empty lv version map to each other`() {
    val branch = joinedBranch()
    val graph = impl(branch)
    val trimmed = graph.trimmedTo(graph.lvVersion(), branch.text())
    val baseVersion = trimmed.baseVersion()
    assertEquals(2, graph.lvVersion().lvs.size)
    assertEquals(graph.version(), baseVersion)
    assertEquals(baseVersion, VersionImpl.fromLvVersion(trimmed, LvVersion.ROOT))
    assertEquals(LvVersion.ROOT, baseVersion.lvVersionIn(trimmed))
    // The root and one head of the two lie below the base version.
    val oneHead = VersionImpl.fromLvVersion(graph, LvVersion(intArrayOf(graph.lvVersion().lvs[0])))
    for (below in listOf(VersionImpl.ROOT, oneHead)) {
      assertThrows(TrimmedHistoryException::class.java, {
        below.lvVersionIn(trimmed)
      }, "$below")
    }
  }

  @Test
  fun `a trim to the base version keeps the graph`() {
    val branch = joinedBranch()
    val graph = impl(branch)
    assertSame(graph, graph.trimmedTo(LvVersion.ROOT, branch.text()))
    val trimmed = graph.trimmedTo(graph.lvVersion(), branch.text())
    assertSame(trimmed, trimmed.trimmedTo(trimmed.lvVersion(), branch.text()))
  }

  @Test
  fun `a trim below the version is not supported yet`() {
    val start = DocBranch.createBranch("ab", L).applyOp(DocumentOp.insertOp(2, "c"))
    val branch = start.applyOp(DocumentOp.insertOp(3, "d"))
    val graph = impl(branch)
    val failure = assertThrows(IllegalArgumentException::class.java) {
      graph.trimmedTo(impl(start).lvVersion(), start.text())
    }
    assertTrue(failure.message.orEmpty().contains("not supported yet"), failure.message)
  }

  /**
   * Two forks that typed at the start of "ab", merged: a graph whose version has two heads.
   */
  private fun joinedBranch(): DocBranch {
    val start = DocBranch.createBranch("ab", Agent.createAgent("start"))
    val left = start.fork(L).applyOp(DocumentOp.insertOp(0, "L"))
    val right = start.fork(R).applyOp(DocumentOp.insertOp(2, "R"))
    return left.merge(right)
  }

  private fun impl(branch: DocBranch): EventGraphImpl {
    return EventGraphImpl.implOf(branch.graph())
  }

  private companion object {
    val L: Agent = Agent.createAgent("left")
    val R: Agent = Agent.createAgent("right")
  }
}

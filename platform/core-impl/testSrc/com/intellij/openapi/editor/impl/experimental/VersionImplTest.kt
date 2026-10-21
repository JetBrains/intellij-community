// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.Event
import com.intellij.openapi.editor.ex.experimental.EventGraph
import com.intellij.openapi.editor.ex.experimental.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [VersionImpl]: the order of the heads, the conversion between the ids and the lvs of a
 * graph, the equality, and the message text.
 */
internal class VersionImplTest {

  @Test
  fun `the heads sort by agent and then by seq`() {
    // Three concurrent roots: "x" of v at the lv 0, then "y" and "z" of u at the lvs 1 and 2.
    val graph = graphOf(Event.createInsert(V, 0, 0, "x"))
      .append(Event.createInsert(U, 0, 0, "y"), Version.root())
      .append(Event.createInsert(U, 1, 0, "z"), Version.root())
    assertEquals("v[(u, 0), (u, 1), (v, 0)]", graph.version().toString())
  }

  @Test
  fun `the ids of a head find its own lv in every graph`() {
    // The unit of v takes the lv 1 in one graph and the lv 0 in the other.
    val uFirst = impl(concurrentRoots(U, V))
    val vFirst = impl(concurrentRoots(V, U))
    val ofV = VersionImpl.fromLvVersion(uFirst, LvVersion(intArrayOf(1)))
    assertEquals(LvVersion(intArrayOf(1)), ofV.lvVersionIn(uFirst))
    assertEquals(LvVersion(intArrayOf(0)), ofV.lvVersionIn(vFirst))
    // Both heads land at the lvs 0 and 1 in either graph, in another order.
    val both = VersionImpl.implOf(uFirst.version())
    assertEquals(LvVersion(intArrayOf(0, 1)), both.lvVersionIn(vFirst))
  }

  @Test
  fun `a head at the end of a run finds its unit inside a closed run of another graph`() {
    // In the graph of u, "b" ends the newest run.
    val base = DocumentBranch.createBranch("", Agent.createAgent("base"))
    val typed = base.fork(U)
      .applyOp(DocumentOp.insertOp(0, "a"))
      .applyOp(DocumentOp.insertOp(1, "b"))
    val atB = VersionImpl.implOf(typed.version())
    // The run grows past "b", and an insert at the front closes it into the trees.
    val later = typed
      .applyOp(DocumentOp.insertOp(2, "c"))
      .applyOp(DocumentOp.insertOp(0, "X"))
    // The unit of v comes first in its own graph, so "b" takes the lv 2 there, inside "abc".
    val other = base.fork(V).applyOp(DocumentOp.insertOp(0, "Y"))
    val merged = impl(other.merge(later).graph())
    assertEquals(LvVersion(intArrayOf(2)), atB.lvVersionIn(merged))
    // The run of "X" is the tail, so the run of "abc" is closed.
    assertEquals(1, merged.runIndexOf(2))
    assertEquals(3, merged.runCount())
    assertEquals("ab", merged.replay(atB).string())
  }

  @Test
  fun `versions are equal exactly when their heads are`() {
    val uFirst = concurrentRoots(U, V)
    val vFirst = concurrentRoots(V, U)
    assertEquals(uFirst.version(), vFirst.version())
    assertEquals(uFirst.version().hashCode(), vFirst.version().hashCode())
    // One head differs in the agent only, and one in the seq only.
    val ofU = graphOf(Event.createInsert(U, 0, 0, "a")).version()
    val ofV = graphOf(Event.createInsert(V, 0, 0, "a")).version()
    val ofLaterU = graphOf(Event.createInsert(U, 0, 0, "aa")).version()
    assertNotEquals(ofU, ofV)
    assertNotEquals(ofU, ofLaterU)
  }

  @Test
  fun `a head that the graph does not hold fails`() {
    val ofV = VersionImpl.implOf(graphOf(Event.createInsert(V, 0, 0, "ab")).version())
    // This graph has no unit of v at all.
    val ofU = impl(graphOf(Event.createInsert(U, 0, 0, "ab")))
    val noAgent = assertThrows(IllegalArgumentException::class.java) { ofV.lvVersionIn(ofU) }
    assertEquals(
      "A graph of size 2 does not hold the head (v, 1) of the version v[(v, 1)]",
      noAgent.message,
    )
    // This graph has the first unit of v, but not the second.
    val shorter = impl(graphOf(Event.createInsert(V, 0, 0, "a")))
    val noSeq = assertThrows(IllegalArgumentException::class.java) { ofV.lvVersionIn(shorter) }
    assertEquals(
      "A graph of size 1 does not hold the head (v, 1) of the version v[(v, 1)]",
      noSeq.message,
    )
  }

  @Test
  fun `the root has no heads in any graph`() {
    val graph = impl(graphOf(Event.createInsert(U, 0, 0, "a")))
    val root = VersionImpl.fromLvVersion(impl(EventGraph.createGraph()), LvVersion.ROOT)
    assertEquals(VersionImpl.ROOT, root)
    assertTrue(root.isRoot())
    assertEquals(LvVersion.ROOT, root.lvVersionIn(graph))
    assertEquals("v[]", root.toString())
  }

  @Test
  fun `a long list of heads keeps its first items in a message`() {
    var graph = EventGraph.createGraph()
    for (i in 0 until 12) {
      val agent = Agent.createAgent("a${i.toString().padStart(2, '0')}")
      graph = graph.append(Event.createInsert(agent, 0, 0, "x"), Version.root())
    }
    val listed = (0 until 10).joinToString { i ->
      "(a${i.toString().padStart(2, '0')}, 0)"
    }
    assertEquals("v[$listed, ... (12 heads)]", graph.version().toString())
  }

  @Test
  fun `a foreign version is rejected`() {
    val foreign = object : Version {
      override fun isRoot(): Boolean {
        return true
      }
    }
    val failure = assertThrows(IllegalArgumentException::class.java) { VersionImpl.implOf(foreign) }
    val message = failure.message.orEmpty()
    assertTrue(message.startsWith("Foreign Version implementation"), message)
  }

  private fun graphOf(event: Event): EventGraph {
    return EventGraph.createGraph().append(event, Version.root())
  }

  /**
   * One unit of [first] and then one unit of [second], both at the root. Each unit inserts the
   * name of its agent, so the two orders hold the same events.
   */
  private fun concurrentRoots(first: Agent, second: Agent): EventGraph {
    return graphOf(Event.createInsert(first, 0, 0, first.toString()))
      .append(Event.createInsert(second, 0, 0, second.toString()), Version.root())
  }

  private fun impl(graph: EventGraph): EventGraphImpl {
    return EventGraphImpl.implOf(graph)
  }

  private companion object {
    val U: Agent = Agent.createAgent("u")
    val V: Agent = Agent.createAgent("v")
  }
}

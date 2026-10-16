// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Tests [RunTree] against a flat list, at the sizes where its shape changes: a leaf fills at
 * [RunTree.WIDTH] runs, the tail fills after it, and every power of [RunTree.WIDTH] leaves adds an
 * inner level.
 */
internal class RunTreeTest {

  @Test
  fun `a tree answers by key and by index across every fullness boundary`() {
    val random = Random(20260930)
    val expected = ArrayList<StoredRun>()
    var tree = RunTree.EMPTY
    var lv = FIRST_KEY
    for (size in SIZES) {
      while (expected.size < size) {
        val run = runAt(lv, 1 + random.nextInt(3), expected.size)
        tree = tree.appended(run.lvStart, run)
        expected.add(run)
        lv = run.lvEnd()
      }
      checkTree(tree, expected)
    }
  }

  @Test
  fun `an empty tree finds nothing`() {
    assertEquals(0, RunTree.EMPTY.size())
    assertNull(RunTree.EMPTY.last())
    assertNull(RunTree.EMPTY.floor(0))
    assertEquals(-1, RunTree.EMPTY.floorIndex(0))
    assertThrows(IllegalArgumentException::class.java) { RunTree.EMPTY.get(0) }
  }

  /**
   * Two siblings grow one tree at every boundary where an append copies a path. Each sibling must
   * see its own newest run, and the tree they share must not change.
   */
  @Test
  fun `siblings grow one tree each on their own`() {
    var shared = RunTree.EMPTY
    val expected = ArrayList<StoredRun>()
    var lv = 0
    for (size in SIZES) {
      while (expected.size < size) {
        val run = runAt(lv, 1, expected.size)
        shared = shared.appended(run.lvStart, run)
        expected.add(run)
        lv = run.lvEnd()
      }
      val left = runAt(lv, 1, expected.size)
      val right = runAt(lv, 2, expected.size)
      val leftTree = shared.appended(left.lvStart, left)
      val rightTree = shared.appended(right.lvStart, right)
      assertSame(left, leftTree.last())
      assertSame(right, rightTree.last())
      assertSame(left, leftTree.floor(lv))
      assertSame(right, rightTree.floor(lv + 1))
      assertEquals(size + 1, leftTree.size())
      checkTree(shared, expected)
    }
  }

  @Test
  fun `a key that does not ascend is rejected`() {
    val first = runAt(10, 2, 0)
    val tree = RunTree.EMPTY.appended(first.lvStart, first)
    assertThrows(IllegalArgumentException::class.java) { tree.appended(10, runAt(12, 1, 1)) }
    assertThrows(IllegalArgumentException::class.java) { tree.appended(9, runAt(12, 1, 1)) }
  }

  @Test
  fun `an index outside the tree is rejected`() {
    val first = runAt(0, 2, 0)
    val tree = RunTree.EMPTY.appended(first.lvStart, first)
    assertThrows(IllegalArgumentException::class.java) { tree.get(-1) }
    assertThrows(IllegalArgumentException::class.java) { tree.get(1) }
  }

  // ------------------------------------------------------------------------------ the agent index

  @Test
  fun `the agent index answers per agent`() {
    var index = AgentIndex.EMPTY
    val runs = ArrayList<StoredRun>()
    var lv = 0
    val nextSeqs = HashMap<Agent, Int>()
    // Two agents interleave, and some runs are long, so an agent's runs cover a leaf boundary.
    repeat(3 * RunTree.WIDTH) { i ->
      val agent = if (i % 3 == 0) V else U
      val length = 1 + i % 4
      val seq = nextSeqs[agent] ?: 0
      val run = StoredRun(EventImpl(agent, seq, DocumentOp.insertOp(0, "x".repeat(length))), lv, IntArray(0))
      index = index.appended(run)
      runs.add(run)
      nextSeqs[agent] = seq + length
      lv += length
    }
    for (agent in listOf(U, V)) {
      assertEquals(nextSeqs[agent], index.nextSeq(agent))
    }
    assertEquals(0, index.nextSeq(W))
    for (run in runs) {
      val agent = run.event.agent()
      for (unit in 0 until run.event.length()) {
        assertEquals(run.lvStart + unit, index.lvOfSeq(agent, run.event.seq() + unit))
      }
    }
    assertEquals(-1, index.lvOfSeq(U, nextSeqs[U]!!))
    assertEquals(-1, index.lvOfSeq(W, 0))

    val summary = index.summarize(null)
    assertEquals(nextSeqs[U], summary.endSeq(U))
    assertEquals(nextSeqs[V], summary.endSeq(V))
    assertEquals(0, index.newRunStarts(summary).size)

    // A summary that knows a prefix of each agent, cut inside a run, gets that run and every later one.
    val known = VersionSummary(mapOf(U to nextSeqs[U]!! / 2, V to 1))
    val expected = runs.filter { run -> run.endSeq() > known.endSeq(run.event.agent()) }.map { it.lvStart }
    assertEquals(expected, index.newRunStarts(known).toList())
    assertEquals(runs.map { it.lvStart }, index.newRunStarts(VersionSummary(emptyMap())).toList())
  }

  @Test
  fun `the agent summary takes the tail`() {
    val closed = StoredRun(EventImpl(U, 0, DocumentOp.insertOp(0, "ab")), 0, IntArray(0))
    val tail = StoredRun(EventImpl(U, 2, DocumentOp.insertOp(2, "cd")), 2, intArrayOf(1))
    val index = AgentIndex.EMPTY.appended(closed)
    assertEquals(4, index.summarize(tail).endSeq(U))
    assertEquals(2, index.summarize(null).endSeq(U))
  }

  @Test
  fun `a seq that does not continue its agent is rejected`() {
    val index = AgentIndex.EMPTY.appended(StoredRun(EventImpl(U, 0, DocumentOp.insertOp(0, "ab")), 0, IntArray(0)))
    for (seq in intArrayOf(0, 1, 3)) {
      assertThrows(IllegalArgumentException::class.java, {
        index.appended(StoredRun(EventImpl(U, seq, DocumentOp.insertOp(0, "c")), 2, intArrayOf(1)))
      }, "seq $seq")
    }
    assertThrows(IllegalArgumentException::class.java) {
      AgentIndex.EMPTY.appended(StoredRun(EventImpl(V, 1, DocumentOp.insertOp(0, "c")), 0, IntArray(0)))
    }
  }

  @Test
  fun `an index after a trim continues the seqs where the trim left them`() {
    // The trim left U at the seq 3 and W at the seq 5. W stores no run after it.
    val startSeqs = VersionSummary(mapOf(U to 3, W to 5))
    val empty = AgentIndex.startingAt(startSeqs)
    assertEquals(3, empty.nextSeq(U))
    assertEquals(5, empty.nextSeq(W))
    assertEquals(0, empty.nextSeq(V))
    assertThrows(IllegalArgumentException::class.java) {
      empty.appended(StoredRun(EventImpl(U, 0, DocumentOp.insertOp(0, "x")), 0, IntArray(0)))
    }
    val run = StoredRun(EventImpl(U, 3, DocumentOp.insertOp(0, "ab")), 0, IntArray(0))
    val index = empty.appended(run)
    assertEquals(5, index.nextSeq(U))
    assertEquals(-1, index.lvOfSeq(U, 2))
    assertEquals(0, index.lvOfSeq(U, 3))
    // The summary keeps an agent that only the trim knows.
    val summary = index.summarize(null)
    assertEquals(5, summary.endSeq(U))
    assertEquals(5, summary.endSeq(W))
    assertEquals(listOf(0), index.newRunStarts(startSeqs).toList())
  }

  @Test
  fun `siblings of one agent index answer each for itself`() {
    val base = AgentIndex.EMPTY.appended(StoredRun(EventImpl(U, 0, DocumentOp.insertOp(0, "ab")), 0, IntArray(0)))
    val left = base.appended(StoredRun(EventImpl(U, 2, DocumentOp.insertOp(0, "L")), 2, intArrayOf(1)))
    val right = base.appended(StoredRun(EventImpl(V, 0, DocumentOp.insertOp(0, "R")), 2, intArrayOf(1)))
    assertEquals(2, base.nextSeq(U))
    assertEquals(3, left.nextSeq(U))
    assertEquals(0, left.nextSeq(V))
    assertEquals(2, right.nextSeq(U))
    assertEquals(1, right.nextSeq(V))
    assertEquals(-1, base.lvOfSeq(U, 2))
    assertEquals(2, left.lvOfSeq(U, 2))
  }

  /**
   * Checks every index, and the first and the last unit of every run, against [expected].
   */
  private fun checkTree(tree: RunTree, expected: List<StoredRun>) {
    assertEquals(expected.size, tree.size())
    if (expected.isEmpty()) {
      assertNull(tree.last())
      return
    }
    assertSame(expected.last(), tree.last())
    for ((index, run) in expected.withIndex()) {
      assertSame(run, tree.get(index)) { "get($index) at size ${expected.size}" }
      assertSame(run, tree.floor(run.lvStart)) { "floor(${run.lvStart}) at size ${expected.size}" }
      assertSame(run, tree.floor(run.lvEnd() - 1)) { "floor(${run.lvEnd() - 1}) at size ${expected.size}" }
      assertEquals(index, tree.floorIndex(run.lvStart)) { "floorIndex(${run.lvStart}) at size ${expected.size}" }
      assertEquals(index, tree.floorIndex(run.lvEnd() - 1)) {
        "floorIndex(${run.lvEnd() - 1}) at size ${expected.size}"
      }
    }
    // Every key below the first run finds nothing, and every key past the last finds the last run.
    val first = expected[0].lvStart
    if (first > 0) {
      assertNull(tree.floor(first - 1))
      assertEquals(-1, tree.floorIndex(first - 1))
    }
    assertSame(expected.last(), tree.floor(Int.MAX_VALUE))
    assertEquals(expected.size - 1, tree.floorIndex(Int.MAX_VALUE))
  }

  private fun runAt(lvStart: LV, length: Int, seq: Int): StoredRun {
    return StoredRun(EventImpl(U, seq, DocumentOp.insertOp(0, "x".repeat(length))), lvStart, IntArray(0))
  }

  private companion object {
    val U: Agent = Agent.createAgent("u")
    val V: Agent = Agent.createAgent("v")
    val W: Agent = Agent.createAgent("w")

    /**
     * A first key above 0, so a search below it has something to miss.
     */
    const val FIRST_KEY = 10

    /**
     * The sizes where the shape of the tree changes, and one run past each.
     */
    val SIZES = intArrayOf(
      0, 1, 31, 32, 33, 63, 64, 65, 96, 97,
      1_024, 1_055, 1_056, 1_057, 2_080, 2_081,
      32_768, 32_800, 32_801, 32_833, 65_600,
    )
  }
}

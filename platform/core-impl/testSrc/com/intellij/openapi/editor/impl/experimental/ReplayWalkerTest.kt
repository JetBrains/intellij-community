// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Tests [ReplayWalker] over histories long enough that its [ItemTree] grows inner levels. The other
 * replay tests walk a few items, so their tree stays one leaf.
 *
 * The text of a branch comes from [DocumentText][com.intellij.openapi.editor.ex.experimental.DocumentText] ops,
 * and not from a walk. So it is an oracle for a full replay of a history of one agent. A merge has
 * no such oracle, and its partial replay must give the text of a full replay. The tests also pin
 * that the replay of a linear history never builds the unit index of the tree.
 */
internal class ReplayWalkerTest {

  @Test
  fun `a history of edits at random places replays to its text`() {
    val branch = randomEdits(Random(20261001L), DocumentBranch.createBranch("", U), OPS)
    val walker = replayInFull(branch)
    assertTrue(walker.itemTreeDepth() >= 2, "the item tree has fewer than two inner levels")
    // A linear history never looks an item up by unit.
    assertFalse(walker.hasUnitIndex())
  }

  @Test
  fun `a past version of a long linear history replays to its text`() {
    val random = Random(20261004L)
    val half = randomEdits(random, DocumentBranch.createBranch("", U), OPS / 2)
    val full = randomEdits(random, half, OPS / 2)
    val graph = EventGraphImpl.implOf(full.graph())
    val walker = ReplayWalker(graph, placeholderCount = 0)
    val text = StringBuilder()
    // The version of the earlier value names the same units in the later graph.
    val pastVersion = EventGraphImpl.implOf(half.graph()).lvVersion()
    walker.replayAt(pastVersion, TextSink(text))
    assertEquals(half.text().string(), text.toString())
    walker.checkItems()
    // The past version holds a prefix of the history, so the walk never retreats.
    assertFalse(walker.hasUnitIndex())
  }

  @Test
  fun `typing with backspaces at a caret replays to its text`() {
    // One long run of typing in the middle of a text. A run of inserts at the end of a leaf moves
    // no older item when the leaf splits, so this checks the leaves that the run fills.
    var branch = DocumentBranch.createBranch("[" + "o".repeat(OPS) + "]", U)
    val random = Random(20261002L)
    var caret = 1 + OPS / 2
    repeat(OPS) {
      if (random.nextInt(5) == 0) {
        caret--
        branch = branch.applyOp(DocumentOp.deleteOp(caret, 1))
      } else {
        branch = branch.applyOp(DocumentOp.insertOp(caret, "x"))
        caret++
      }
    }
    val walker = replayInFull(branch)
    assertTrue(walker.itemTreeDepth() >= 1, "the item tree has no inner level")
    assertFalse(walker.hasUnitIndex())
  }

  @Test
  fun `a history that overtypes replays to its text`() {
    // Each pair of ops leaves one deleted item at the place of the next pair.
    var branch = DocumentBranch.createBranch("o".repeat(OPS), U)
    for (offset in 0 until OPS) {
      branch = branch.applyOp(DocumentOp.deleteOp(offset, 1))
      branch = branch.applyOp(DocumentOp.insertOp(offset, "x"))
    }
    assertEquals("x".repeat(OPS), branch.text().string())
    val walker = replayInFull(branch)
    assertTrue(walker.itemTreeDepth() >= 2, "the item tree has fewer than two inner levels")
    assertFalse(walker.hasUnitIndex())
  }

  @Test
  fun `many concurrent inserts at one place come out in the order of their agents`() {
    // Each run lands among the runs of every agent before it, so the Fugue scan steps through
    // hundreds of items that the prepare version has not applied.
    val base = DocumentBranch.createBranch("[]", U)
    var merged = base
    for (i in 0 until AGENTS) {
      val typed = base.fork(Agent.createAgent("a$i")).applyOp(DocumentOp.insertOp(1, "<$i>"))
      merged = merged.merge(typed)
    }
    // The runs share the left origin and the right parent, so the tie-break sorts them by agent,
    // and an agent name sorts as a string. The reference gives this text for 40 and 300 agents.
    val names = (0 until AGENTS).map { "a$it" }.sorted()
    val expected = names.joinToString(separator = "", prefix = "[", postfix = "]") { name ->
      "<" + name.substring(1) + ">"
    }
    assertEquals(expected, merged.text().string())
    val walker = replayInFull(merged)
    assertTrue(walker.itemTreeDepth() >= 1, "the item tree has no inner level")
  }

  @Test
  fun `two long concurrent histories merge to the text of a full replay`() {
    val random = Random(20261003L)
    val base = randomEdits(random, DocumentBranch.createBranch("", U), OPS / 10)
    val left = randomEdits(random, base.fork(V), OPS / 2)
    val right = randomEdits(random, base.fork(W), OPS / 2)
    val forward = left.merge(right)
    val backward = right.merge(left)
    assertEquals(forward.text().string(), backward.text().string())
    val walker = replayInFull(forward)
    assertTrue(walker.itemTreeDepth() >= 2, "the item tree has fewer than two inner levels")
    // The concurrent edits make the walk retreat and advance, which looks its items up by unit.
    // The inserts and splits after that first lookup reach the index too, or the text would differ.
    assertTrue(walker.hasUnitIndex())
  }

  /**
   * Replays the whole graph of [branch] with a fresh walker, checks its text against the text of
   * [branch], checks the item tree, and returns the walker.
   */
  private fun replayInFull(branch: DocumentBranch): ReplayWalker {
    val graph = EventGraphImpl.implOf(branch.graph())
    val baseText = graph.baseText()
    val walker = ReplayWalker(graph, placeholderCount = baseText.length())
    val text = StringBuilder(baseText.chars())
    walker.replayAt(graph.lvVersion(), TextSink(text))
    assertEquals(branch.text().string(), text.toString())
    walker.checkItems()
    return walker
  }

  /**
   * [ops] edits of [start] at random places. Seven of ten insert one or two characters, and the
   * rest delete one.
   */
  private fun randomEdits(random: Random, start: DocumentBranch, ops: Int): DocumentBranch {
    var branch = start
    repeat(ops) {
      val length = branch.text().length()
      val at = random.nextInt(length + 1)
      branch = if (length > 0 && random.nextInt(10) < 3) {
        val deleteAt = minOf(at, length - 1)
        branch.applyOp(DocumentOp.deleteOp(deleteAt, 1))
      } else {
        val fragment = "ab".substring(0, 1 + random.nextInt(2))
        branch.applyOp(DocumentOp.insertOp(at, fragment))
      }
    }
    return branch
  }

  private class TextSink(private val text: StringBuilder) : EgWalkerReplay.Sink {
    override fun insert(effectPos: Int, fragment: CharSequence) {
      text.insert(effectPos, fragment)
    }

    override fun delete(effectPos: Int, count: Int) {
      text.delete(effectPos, effectPos + count)
    }
  }

  private companion object {
    /**
     * Enough ops for two inner levels of the item tree: a leaf holds up to [ItemTree.WIDTH] items.
     */
    const val OPS = 3_000

    /**
     * The agents that insert at one place: enough for more than one leaf of concurrent runs.
     */
    const val AGENTS = 300

    val U: Agent = Agent.createAgent("u")
    val V: Agent = Agent.createAgent("v")
    val W: Agent = Agent.createAgent("w")
  }
}

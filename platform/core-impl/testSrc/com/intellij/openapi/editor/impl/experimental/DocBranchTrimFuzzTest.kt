// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.DocMerge
import com.intellij.openapi.editor.ex.experimental.TrimmedHistoryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * A differential fuzz of [DocumentBranch.trimmedTo]. Every replica has a SHADOW: the same branch with the
 * same ops and no trim of its own. A merge of two replicas must succeed exactly when the base version
 * of each one is a critical version of the history of the other. Both directions must agree. A merge
 * that succeeds must give the text of the merge of the two shadows.
 *
 * The oracle reads the history of a replica from its shadow. The shadow holds every event above the
 * base version of the start branch.
 */
internal class DocBranchTrimFuzzTest {

  @Test
  fun `merges of trimmed branches match their shadows in both directions`() {
    val start = { random: Random ->
      DocumentBranch.createBranch(randomText(random), Agent.createAgent("start"))
    }
    fuzz(ROUNDS, SEED, start)
  }

  /**
   * The same fuzz from [NEAR_LIMIT_BRANCH]. The seqs of its agent cross [INT_SEQ_LIMIT] in most rounds.
   * So the ids, the versions, and the merges hold seqs on both sides of it.
   */
  @Test
  fun `merges across the Int seq limit match their shadows in both directions`() {
    var crossings = 0
    fuzz(ROUNDS_NEAR_LIMIT, SEED_NEAR_LIMIT, { NEAR_LIMIT_BRANCH }) { replicas ->
      if (replicas.nextSeqOfStart() > INT_SEQ_LIMIT) {
        crossings++
      }
    }
    assertTrue(crossings > ROUNDS_NEAR_LIMIT / 2) { "only $crossings rounds cross the limit" }
  }

  /**
   * Runs [rounds] rounds. Each round starts from the branch that [start] gives, and draws its steps
   * with the seed [seed] plus the round number. [afterRound] gets the replicas at the end of each round.
   */
  private fun fuzz(
    rounds: Int,
    seed: Long,
    start: (Random) -> DocumentBranch,
    afterRound: (Replicas) -> Unit = {},
  ) {
    var merges = 0
    var rejections = 0
    repeat(rounds) { round ->
      val random = Random(seed + round)
      try {
        val replicas = Replicas(random, start(random))
        repeat(STEPS) {
          when (replicas.step()) {
            Outcome.MERGED -> merges++
            Outcome.REJECTED -> rejections++
            Outcome.OTHER -> {}
          }
        }
        afterRound(replicas)
      } catch (e: Throwable) {
        throw AssertionError("The fuzz round $round with the seed ${seed + round} failed: ${e.message}", e)
      }
    }
    // The fuzz reaches both outcomes of a merge.
    assertTrue(merges > rounds) { "only $merges merges" }
    assertTrue(rejections > rounds / 4) { "only $rejections rejections" }
  }

  private enum class Outcome {
    MERGED,
    REJECTED,
    OTHER,
  }

  /**
   * The replicas of one round. Each one is a branch and its shadow. The first replica is [start].
   */
  private class Replicas(private val random: Random, start: DocumentBranch) {
    private val actuals = ArrayList<DocumentBranch>()
    private val shadows = ArrayList<DocumentBranch>()

    init {
      actuals.add(start)
      shadows.add(start)
    }

    /**
     * The next seq of the agent of the start branch, in the first replica.
     */
    fun nextSeqOfStart(): Long {
      val first = actuals[0]
      return EventGraphImpl.implOf(first.graph()).nextSeqFor(first.agent())
    }

    fun step(): Outcome {
      val i = random.nextInt(actuals.size)
      val roll = random.nextInt(10)
      if (roll == 0) {
        actuals[i] = actuals[i].trimmedTo(actuals[i].version())
        return Outcome.OTHER
      }
      if (roll == 1 && actuals.size < MAX_REPLICAS) {
        val agent = Agent.createAgent("agent${actuals.size}")
        actuals.add(actuals[i].fork(agent))
        shadows.add(shadows[i].fork(agent))
        return Outcome.OTHER
      }
      if (roll < 4) {
        val j = random.nextInt(actuals.size)
        return if (i == j) Outcome.OTHER else merge(i, j)
      }
      val op = randomOp(random, actuals[i].text())
      actuals[i] = actuals[i].applyOp(op)
      shadows[i] = shadows[i].applyOp(op)
      return Outcome.OTHER
    }

    /**
     * Merges the replica [j] into the replica [i], checks the merge against the oracle and the
     * shadows, and keeps the result.
     */
    private fun merge(i: Int, j: Int): Outcome {
      val expected = isCritical(i, j) && isCritical(j, i)
      val forward = mergeOrNull(actuals[i], actuals[j])
      val backward = mergeOrNull(actuals[j], actuals[i])
      assertEquals(expected, forward != null) { "the merge of $j into $i" }
      assertEquals(expected, backward != null) { "the merge of $i into $j" }
      if (forward == null || backward == null) {
        return Outcome.REJECTED
      }
      val merged = forward.branch()
      val shadowMerged = shadows[i].merge(shadows[j])
      assertEquals(shadowMerged.text().string(), merged.text().string())
      assertEquals(merged.text().string(), backward.branch().text().string())
      assertEquals(merged.version(), backward.branch().version())
      val folded = forward.ops().fold(actuals[i].text()) { text, op -> text.applyOp(op) }
      assertEquals(merged.text().string(), folded.string())
      assertEquals(merged.text().string(), merged.graph().replay().string())
      EventGraphImpl.implOf(merged.graph()).checkInvariants()
      actuals[i] = merged
      shadows[i] = shadowMerged
      return Outcome.MERGED
    }

    /**
     * Whether the base version of the replica [of] is a critical version of the history of the
     * replica [inReplica]. That history must hold it. Every event there must be at or below it, or
     * follow all its heads.
     */
    private fun isCritical(of: Int, inReplica: Int): Boolean {
      val baseVersion = EventGraphImpl.implOf(actuals[of].graph()).baseVersion()
      if (baseVersion.isRoot()) {
        return true
      }
      val history = EventGraphImpl.implOf(shadows[inReplica].graph())
      // A history that lacks a head of the base version does not hold it.
      val heads = try {
        baseVersion.lvVersionIn(history)
      } catch (_: IllegalArgumentException) {
        return false
      }
      val atOrBelow = history.eventsOf(heads)
      for (lv in 0 until history.size()) {
        if (atOrBelow.get(lv)) {
          continue
        }
        val ancestors = history.eventsOf(LvVersion(intArrayOf(lv)))
        if (heads.lvs.any { head -> !ancestors.get(head) }) {
          return false
        }
      }
      return true
    }
  }

  private companion object {
    const val ROUNDS = 2000
    const val ROUNDS_NEAR_LIMIT = 500
    const val STEPS = 40
    const val MAX_REPLICAS = 4
    const val SEED = 20261016L
    const val SEED_NEAR_LIMIT = 20261020L
    const val ALPHABET = "abc\n"

    /**
     * The merge of [other] into [receiver], or `null` when it throws [TrimmedHistoryException].
     */
    fun mergeOrNull(receiver: DocumentBranch, other: DocumentBranch): DocMerge? {
      return try {
        receiver.mergeWithOps(other)
      } catch (_: TrimmedHistoryException) {
        null
      }
    }

    fun randomText(random: Random): String {
      val text = StringBuilder()
      repeat(random.nextInt(6)) {
        text.append(ALPHABET[random.nextInt(ALPHABET.length)])
      }
      return text.toString()
    }

    /**
     * An insert of one to three characters, or a delete of one or two, at a random place of [text].
     */
    fun randomOp(random: Random, text: DocumentText): DocumentOp.Text {
      val length = text.length()
      if (length == 0 || random.nextInt(3) > 0) {
        val fragment = randomText(random).ifEmpty { "x" }.take(3)
        return DocumentOp.insertOp(random.nextInt(length + 1), fragment)
      }
      val offset = random.nextInt(length)
      val count = minOf(1 + random.nextInt(2), length - offset)
      return DocumentOp.deleteOp(offset, count)
    }
  }
}

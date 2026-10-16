// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.EventIdClashException

/**
 * Fails when the two graphs of a merge disagree about an id they both hold.
 *
 * One (agent, seq) pair names one operation forever. A difference means two branches minted the
 * same id, which the agent contract of [com.intellij.openapi.editor.ex.experimental.DocBranch] forbids:
 * a branch that edits concurrently must come from `fork(agent)`. Without this check the merge keeps
 * one operation, drops the other, and reports nothing. The two branches then disagree, and a merge
 * stops giving the same text in both directions.
 *
 * A graph stores the seqs `[startSeq, endSeq)` of an agent, and a trim moves the start. So the shared
 * range of an agent runs from the greater start to the smaller end. The check SAMPLES the two ends of
 * that range. At each end it compares the kind, the offset, the move offset, the character, and the
 * parents. It compares the parents by event id, and an empty set there names the base version of its
 * graph. So it costs a few lookups per shared agent and nothing per run. It does not see a difference
 * strictly inside the shared range, nor in a unit that either graph trimmed.
 * A full compare would make every merge cost the whole shared history, and a merge must cost the
 * size of the change.
 */
internal class SharedIdCheck(private val graph: EventGraphImpl, private val other: EventGraphImpl) {

  /**
   * Samples every agent of [other]. [summary] says what [graph] knows, and [otherSummary] says what
   * [other] knows.
   */
  fun check(summary: VersionSummary, otherSummary: VersionSummary) {
    for (agent in otherSummary.agents()) {
      val sharedStart = maxOf(graph.startSeqOf(agent), other.startSeqOf(agent))
      val sharedEnd = minOf(summary.endSeq(agent), otherSummary.endSeq(agent))
      if (sharedEnd <= sharedStart) {
        continue
      }
      checkSameId(agent, sharedStart)
      if (sharedEnd - 1 > sharedStart) {
        checkSameId(agent, sharedEnd - 1)
      }
    }
  }

  /**
   * Fails when the unit ([agent], [seq]) is a different operation in the two graphs.
   */
  private fun checkSameId(agent: Agent, seq: Int) {
    val lv = graph.lvOfUnit(agent, seq)
    val otherLv = other.lvOfUnit(agent, seq)
    require(lv >= 0 && otherLv >= 0) {
      "The id ($agent, $seq) is inside the shared range, but one graph does not hold it"
    }
    val run = graph.runAt(lv)
    val otherRun = other.runAt(otherLv)
    checkNoClash(run.isDelete() == otherRun.isDelete(), agent, seq, "the operation kind")
    checkNoClash(run.offsetAt(lv) == otherRun.offsetAt(otherLv), agent, seq, "the offset")
    checkNoClash(run.moveDistance() == otherRun.moveDistance(), agent, seq, "the move offset")
    // The kind check passed, so a run that is not a delete is an insert in both graphs.
    if (!run.isDelete()) {
      checkNoClash(run.charAt(lv) == otherRun.charAt(otherLv), agent, seq, "the inserted character")
    }
    checkNoClash(hasSameParentIds(lv, otherLv), agent, seq, "the parents")
  }

  /**
   * Fails with a typed exception, so a caller can tell a broken agent contract from a bug.
   */
  private fun checkNoClash(
    same: Boolean,
    agent: Agent,
    seq: Int,
    difference: String,
  ) {
    if (!same) {
      throw EventIdClashException(agent, seq, clashMessage(agent, seq, difference))
    }
  }

  /**
   * Whether the unit [lv] of [graph] and the unit [otherLv] of [other] have the same parents. The two
   * graphs of a merge give one unit different lvs, and they can have different base versions. So the
   * parents compare as versions by event id, where an empty set names the base version.
   */
  private fun hasSameParentIds(lv: LV, otherLv: LV): Boolean {
    val parentIds = graph.parentVersionOf(lv)
    val otherParentIds = other.parentVersionOf(otherLv)
    return parentIds == otherParentIds
  }

  private fun clashMessage(agent: Agent, seq: Int, difference: String): String {
    return "Two events share the id ($agent, $seq) and differ in $difference. " +
           "One agent authored both, but a branch that edits concurrently must come from fork(agent)."
  }

  override fun toString(): String {
    return "SharedIdCheck(${graph.size()} units against ${other.size()} units)"
  }
}

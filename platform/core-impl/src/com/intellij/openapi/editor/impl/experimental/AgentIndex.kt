// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.experimental.Agent

/**
 * The closed runs of one graph value, by agent: a [RunTree] per agent, keyed by the seq of each run.
 * A merge asks it which ids a graph holds, and an append asks it for the next seq.
 *
 * Documents have few agents, so the agents sit in one array sorted by agent, and an append copies
 * that array. The tree of the agent shares every node it does not change. The index is immutable,
 * and every field is final.
 *
 * The seqs of one agent ascend and leave no gap, so the runs of an agent cover the seqs
 * `[startSeq(agent), nextSeq(agent))`. Every query here rests on that order, and [appended] checks
 * it. The start seq is 0 in a new graph. In a trimmed graph it is the next seq of the agent at the
 * base version, because the trim dropped the units below it. [startSeqs] holds those seqs.
 */
internal class AgentIndex private constructor(
  private val startSeqs: VersionSummary,
  private val agents: Array<Agent>,
  private val trees: Array<RunTree>,
) {

  /**
   * This index with [run] added to the tree of its agent.
   */
  fun appended(run: StoredRun): AgentIndex {
    val agent = run.event.agent()
    val slot = slotOf(agent)
    if (slot >= 0) {
      checkSeqContinues(endSeqOf(trees[slot]), run)
      val newTrees = trees.copyOf()
      newTrees[slot] = trees[slot].appended(run.event.seq(), run)
      return AgentIndex(startSeqs, agents, newTrees)
    }
    checkSeqContinues(startSeq(agent), run)
    val insertAt = -slot - 1
    val newAgents = insertedAt(agents, insertAt, agent)
    val newTrees = insertedAt(trees, insertAt, RunTree.EMPTY.appended(run.event.seq(), run))
    return AgentIndex(startSeqs, newAgents, newTrees)
  }

  /**
   * The first seq of [agent] that this graph stores. A trim dropped the units below it.
   */
  fun startSeq(agent: Agent): Int {
    return startSeqs.endSeq(agent)
  }

  /**
   * The start seq of every agent that a trim left one for.
   */
  fun startSeqs(): VersionSummary {
    return startSeqs
  }

  /**
   * The first seq of [agent] that no closed run holds, or its [startSeq] when [agent] has none.
   */
  fun nextSeq(agent: Agent): Int {
    val slot = slotOf(agent)
    return if (slot < 0) startSeq(agent) else endSeqOf(trees[slot])
  }

  /**
   * The lv of the unit ([agent], [seq]) when a closed run holds it, or -1.
   */
  fun lvOfSeq(agent: Agent, seq: Int): LV {
    val slot = slotOf(agent)
    if (slot < 0) {
      return -1
    }
    val run = trees[slot].floor(seq)
    return if (run != null && run.holdsUnit(agent, seq)) {
      run.lvOfSeq(seq)
    } else {
      -1
    }
  }

  /**
   * What the graph knows, as one end seq per agent: the start seqs that a trim left, then the closed
   * runs, then the [tail] of the graph.
   */
  fun summarize(tail: StoredRun?): VersionSummary {
    val baseAgents = startSeqs.agents()
    val endSeqs = HashMap<Agent, Int>(baseAgents.size + agents.size + 1)
    for (agent in baseAgents) {
      endSeqs[agent] = startSeq(agent)
    }
    for (slot in agents.indices) {
      endSeqs[agents[slot]] = endSeqOf(trees[slot])
    }
    if (tail != null) {
      endSeqs[tail.event.agent()] = tail.endSeq()
    }
    return VersionSummary(endSeqs)
  }

  /**
   * The [StoredRun.lvStart] of every closed run that holds a unit [summary] does not cover,
   * ascending. The runs of an agent cover its seqs without a gap. So the first such run holds the
   * first seq that the summary lacks, and every later run of the agent follows it.
   *
   * [summary] must cover the trimmed units of every agent, because no run here holds them. The graph
   * checks that.
   */
  fun newRunStarts(summary: VersionSummary): IntArray {
    var count = 0
    val firsts = IntArray(agents.size)
    for (slot in agents.indices) {
      val known = summary.endSeq(agents[slot])
      val tree = trees[slot]
      firsts[slot] = if (known >= endSeqOf(tree)) {
        tree.size()
      } else {
        tree.floorIndex(known)
      }
      count += tree.size() - firsts[slot]
    }
    val starts = IntArray(count)
    var next = 0
    for (slot in agents.indices) {
      val tree = trees[slot]
      for (index in firsts[slot] until tree.size()) {
        starts[next] = tree.get(index).lvStart
        next++
      }
    }
    // Each agent's starts ascend already, so the sort only merges them.
    starts.sort()
    return starts
  }

  /**
   * The slot of [agent], or `-(insertion point) - 1` when the index has no run of it.
   */
  private fun slotOf(agent: Agent): Int {
    var lo = 0
    var hi = agents.size - 1
    while (lo <= hi) {
      val mid = (lo + hi) ushr 1
      val order = agents[mid].compareTo(agent)
      when {
        order < 0 -> lo = mid + 1
        order > 0 -> hi = mid - 1
        else -> return mid
      }
    }
    return -lo - 1
  }

  /**
   * Fails when [run] does not start at the [expected] seq of its agent. `EventGraphImpl.checkNextSeq`
   * already rejects such an append. The index checks it anyway, because a break here would not throw
   * later: it would leave every search here quietly wrong.
   */
  private fun checkSeqContinues(expected: Int, run: StoredRun) {
    require(run.event.seq() == expected) {
      "The seq ${run.event.seq()} of ${run.event.agent()} does not continue the seqs of the closed runs: " +
      "expected $expected"
    }
  }

  override fun toString(): String {
    return "AgentIndex(${agents.indices.joinToString { "${agents[it]}:${trees[it].size()} runs" }})"
  }

  companion object {
    val EMPTY: AgentIndex = startingAt(VersionSummary(emptyMap()))

    /**
     * An index with no runs, whose agents start at the seqs of [startSeqs].
     */
    fun startingAt(startSeqs: VersionSummary): AgentIndex {
      return AgentIndex(startSeqs, emptyArray(), emptyArray())
    }

    private fun endSeqOf(tree: RunTree): Int {
      return tree.last()?.endSeq() ?: 0
    }

    private inline fun <reified T> insertedAt(array: Array<T>, index: Int, element: T): Array<T> {
      return Array(array.size + 1) { i ->
        when {
          i < index -> array[i]
          i == index -> element
          else -> array[i - 1]
        }
      }
    }
  }
}

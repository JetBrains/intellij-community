// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.experimental.EventGraphImpl

/**
 * The Eg-walker event graph (arXiv 2409.14252): an append-only DAG of original [Event]s.
 *
 * The graph is an immutable value. [append] and [mergeFrom] return a new graph and leave
 * this one untouched. An append is cheap on any graph, the newest one or an older one.
 *
 * A graph starts from a BASE TEXT at a BASE VERSION, and its first events edit that text. A new
 * graph starts at [Version.root]. A graph from [createGraph] has an empty base text. The graph of a
 * new [DocumentBranch] has the text of the branch as its base text. [DocumentBranch.trimmedTo] moves the base
 * to a later version and drops the events at or below it. An append and a merge keep the base.
 */
interface EventGraph {
  /**
   * The number of units in the graph, summed over all runs. See [Event] for a unit. The units at or
   * below the base version do not count.
   */
  fun size(): Int

  /**
   * The number of stored [Event] runs. `runCount() <= size()`.
   */
  fun runCount(): Int

  /**
   * The paper's `Version(G)`: the current frontier of the graph. A graph with no events has the base
   * version.
   */
  fun version(): Version

  /**
   * Returns a graph with the [event] run appended.
   *
   * This graph must hold every head of [parents], or the append fails. The heads must be
   * transitively reduced, as they are in every version that a graph returns. [parents] can be the
   * base version, and a version below it throws [TrimmedHistoryException].
   *
   * The seq of the run must be the next free seq of its agent in this graph. So the seqs of one
   * agent ascend with no gap, and one (agent, seq) pair names one unit forever. Each agent has its
   * own seq space.
   *
   * The append can extend the newest run instead of adding one, for example while a user types.
   * The units, their ids, and their parents are the same either way, so only [runCount] shows the
   * difference.
   */
  fun append(event: Event, parents: Version): EventGraph

  /**
   * Returns the union of this graph and [other], joined by event ids. A unit that both graphs hold
   * stays once.
   *
   * The cost is the size of the change, not the size of the history that the two graphs share.
   *
   * Throws [LineageMismatchException] when the two graphs do not grow from one [createGraph] or
   * [DocumentBranch.createBranch] call. Throws [TrimmedHistoryException] when the base version of one
   * graph is not a critical version of the other. See [DocumentBranch] for that rule. Throws
   * [EventIdClashException] when the two graphs give one id to two operations. `a.mergeFrom(b)`
   * throws exactly when `b.mergeFrom(a)` throws.
   */
  fun mergeFrom(other: EventGraph): EventGraph

  /**
   * The paper's `replay(G)`, generalized to the subgraph `Events(version)`:
   * builds the document at [version]. This graph must hold every head of [version],
   * or the replay fails. The replay of the base version returns the base text, and a version below
   * it throws [TrimmedHistoryException].
   *
   * Concurrent inserts follow the Fugue order, so every replica builds the same text.
   */
  fun replay(version: Version): DocumentText

  /**
   * The document at the [version] of this graph.
   */
  fun replay(): DocumentText = replay(version())

  companion object {
    /**
     * The longest insert run that [append] builds by extending runs. A longer [Event] stays as it
     * is, and no append extends it.
     */
    const val MAX_COALESCED_INSERT: Int = 256

    /**
     * A new graph with no events and an empty base text. A graph merges only with the graphs that
     * grow from the same call.
     */
    @JvmStatic
    fun createGraph(): EventGraph {
      return EventGraphImpl.empty()
    }
  }
}

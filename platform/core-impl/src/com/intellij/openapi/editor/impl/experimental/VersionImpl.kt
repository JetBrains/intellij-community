// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.TrimmedHistoryException
import com.intellij.openapi.editor.ex.experimental.Version

/**
 * See [Version]. The heads are event ids, sorted by agent and then by seq. So two versions with the
 * same heads hold equal arrays.
 *
 * A graph keeps its version as an [LvVersion] and converts it only at the API. [fromLvVersion]
 * names the lvs of a graph by event id, and [lvVersionIn] finds the event ids in a graph. Each
 * costs one run lookup per head, and a version with more than one head also sorts them.
 *
 * The empty [LvVersion] of a graph is its base version, which a trim can move past the root. So the
 * two conversions map the base version and the empty lv version to each other.
 *
 * Thread safety: the arrays are private, filled before the constructor runs, and never change. So
 * the value is immutable.
 */
internal class VersionImpl private constructor(
  private val agents: Array<Agent>,
  private val seqs: IntArray,
) : Version {

  override fun isRoot(): Boolean {
    return seqs.isEmpty()
  }

  /**
   * The heads as lvs of [graph]. Fails when [graph] does not hold one of them. Throws
   * [TrimmedHistoryException] when this version lies below the base version of [graph], or names a
   * unit that [graph] trimmed. Only the base version itself names trimmed units and maps.
   */
  fun lvVersionIn(graph: EventGraphImpl): LvVersion {
    val baseVersion = graph.baseVersion()
    if (this == baseVersion) {
      return LvVersion.ROOT
    }
    checkAboveBase(baseVersion)
    val lvs = IntArray(seqs.size) { index ->
      lvOfHead(graph, index)
    }
    lvs.sort()
    return LvVersion(lvs)
  }

  override fun equals(other: Any?): Boolean {
    return other is VersionImpl &&
           seqs.contentEquals(other.seqs) &&
           agents.contentEquals(other.agents)
  }

  override fun hashCode(): Int {
    return 31 * agents.contentHashCode() + seqs.contentHashCode()
  }

  override fun toString(): String {
    val heads = seqs.indices.map { index ->
      headText(index)
    }
    return "v${heads.listedForMessage("heads")}"
  }

  /**
   * The heads as text that is safe to put in a box or a message. An agent name is free text, so it
   * gets the quoting of a fragment.
   */
  fun quotedForMessage(): String {
    val heads = seqs.indices.map { index ->
      "(${agents[index].toString().quotedForMessage()}, ${seqs[index]})"
    }
    return "v${heads.listedForMessage("heads")}"
  }

  private fun lvOfHead(graph: EventGraphImpl, index: Int): LV {
    val agent = agents[index]
    val seq = seqs[index]
    if (graph.hasTrimmed(agent, seq)) {
      throw TrimmedHistoryException("A trim of the graph dropped the head ${headText(index)} of the version $this")
    }
    val lv = graph.lvOfUnit(agent, seq)
    checkHeld(graph, lv, index)
    return lv
  }

  /**
   * Throws [TrimmedHistoryException] when this version is the root and [baseVersion] is not. The
   * root then lies below the base version.
   */
  private fun checkAboveBase(baseVersion: VersionImpl) {
    if (isRoot() && !baseVersion.isRoot()) {
      throw TrimmedHistoryException("The root lies below the base version $baseVersion of the graph")
    }
  }

  /**
   * Fails when [EventGraphImpl.lvOfUnit] found no [lv] for the head at [index].
   */
  private fun checkHeld(graph: EventGraphImpl, lv: LV, index: Int) {
    require(lv != NO_UNIT) {
      val head = headText(index)
      "A graph of size ${graph.size()} does not hold the head $head of the version $this"
    }
  }

  private fun headText(index: Int): String {
    return "(${agents[index]}, ${seqs[index]})"
  }

  companion object {
    val ROOT: VersionImpl = VersionImpl(emptyArray(), IntArray(0))

    /**
     * The heads of [lvVersion], which belongs to [graph], as event ids. The empty lv version gives the
     * base version of [graph].
     */
    fun fromLvVersion(graph: EventGraphImpl, lvVersion: LvVersion): VersionImpl {
      val lvs = lvVersion.lvs
      if (lvs.isEmpty()) {
        return graph.baseVersion()
      }
      val runs = Array(lvs.size) { i ->
        graph.runAt(lvs[i])
      }
      val agents = Array(lvs.size) { i ->
        runs[i].event.agent()
      }
      val seqs = IntArray(lvs.size) { i ->
        runs[i].seqAt(lvs[i])
      }
      if (lvs.size < 2) {
        return VersionImpl(agents, seqs)
      }
      return sortedById(agents, seqs)
    }

    /**
     * The heads in the order of their event ids: by agent, then by seq. That is also the tie-break
     * order of concurrent inserts.
     */
    private fun sortedById(agents: Array<Agent>, seqs: IntArray): VersionImpl {
      val order = agents.indices.sortedWith(compareBy({ agents[it] }, { seqs[it] }))
      val sortedAgents = Array(order.size) { i ->
        agents[order[i]]
      }
      val sortedSeqs = IntArray(order.size) { i ->
        seqs[order[i]]
      }
      return VersionImpl(sortedAgents, sortedSeqs)
    }

    fun implOf(version: Version): VersionImpl {
      require(version is VersionImpl) {
        "Foreign Version implementation: ${version.javaClass.name}"
      }
      return version
    }
  }
}

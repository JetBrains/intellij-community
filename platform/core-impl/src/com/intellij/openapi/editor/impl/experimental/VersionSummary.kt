// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.experimental.Agent

/**
 * What one graph value knows, by agent. The reference implementation calls this a
 * `VersionSummary`, and builds it with `summarizeVersion`.
 *
 * One integer per agent is enough. The per-agent seqs of a graph ascend and leave no gap, which
 * [com.intellij.openapi.editor.ex.experimental.EventGraph.append] enforces. So a graph knows exactly
 * the seqs `[0, endSeq(agent))` of every agent. A trimmed graph stores them only from the start seq
 * of the agent, and its base stands for the rest.
 *
 * That is what makes a delta merge possible. A summary costs one entry per AGENT, so two
 * replicas work out what they do not share without either one walking its own history.
 */
internal class VersionSummary(private val endSeqs: Map<Agent, Long>) {

  /**
   * The agents that the graph knows anything about.
   */
  fun agents(): Set<Agent> {
    return endSeqs.keys
  }

  /**
   * The first seq of [agent] that the graph does NOT hold. Zero when it knows no [agent].
   */
  fun endSeq(agent: Agent): Long {
    return endSeqs[agent] ?: 0L
  }

  override fun toString(): String {
    val listed = endSeqs.keys.sorted().joinToString { "$it:${endSeqs[it]}" }
    return "summary[$listed]"
  }
}

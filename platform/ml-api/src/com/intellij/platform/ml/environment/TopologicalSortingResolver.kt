// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.environment

import com.intellij.platform.ml.Tier

private typealias Node = EnvironmentExtender<*>

/**
 * Resolves order using topological sort
 */
internal class TopologicalSortingResolver : EnvironmentResolver {

  override fun resolve(extenderPerTier: Map<Tier<*>, EnvironmentExtender<*>>): List<EnvironmentExtender<*>> {
    val graph: Map<Node, List<Node>> = extenderPerTier.values
      .associateWith { desiredExtender ->
        desiredExtender.requiredTiers.map { requirementForDesiredExtender -> extenderPerTier.getValue(requirementForDesiredExtender) }
      }

    val reverseTopologicalOrder: MutableList<Node> = mutableListOf()
    val resolveStatus: MutableMap<Node, ResolveState> = mutableMapOf()

    fun Node.resolve(path: List<Node>) {
      when (resolveStatus[this]) {
        ResolveState.STARTED -> throw CircularRequirementException(path + this)
        ResolveState.RESOLVED -> return
        null -> {
          resolveStatus[this] = ResolveState.STARTED
          for (nextNode in graph.getValue(this)) {
            nextNode.resolve(path + this)
          }
          resolveStatus[this] = ResolveState.RESOLVED
          reverseTopologicalOrder.add(this)
        }
      }
    }

    graph.keys.forEach { it.resolve(emptyList()) }

    return reverseTopologicalOrder
  }

  private enum class ResolveState {
    STARTED,
    RESOLVED
  }
}

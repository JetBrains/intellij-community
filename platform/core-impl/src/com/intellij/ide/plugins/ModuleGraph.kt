// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.graph.Graph
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * A graph which determines the order in which modules from the platform and the plugins are processed.
 * The graph has a node for each module, and there is an edge from a module to other modules that depend on it.
 */
@ApiStatus.Internal
class ModuleGraph internal constructor(
  private val modules: Collection<IdeaPluginDescriptorImpl>,
  private val directDependencies: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
  private val directDependents: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
) : Graph<IdeaPluginDescriptorImpl> {
  override fun getNodes(): Collection<IdeaPluginDescriptorImpl> = modules

  fun getDependencies(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl> = directDependencies.getOrDefault(descriptor, Collections.emptyList())

  override fun getIn(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = getDependencies(descriptor).iterator()

  override fun getOut(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = directDependents.getOrDefault(descriptor, Collections.emptyList()).iterator()

  internal fun sorted(topologicalComparator: Comparator<IdeaPluginDescriptorImpl>): ModuleGraph {
    return ModuleGraph(
      modules = modules.sortedWith(topologicalComparator),
      directDependencies = copySorted(directDependencies, topologicalComparator),
      directDependents = copySorted(directDependents, topologicalComparator)
    )
  }
}

private fun copySorted(
  map: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
  comparator: Comparator<IdeaPluginDescriptorImpl>,
): Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>> {
  val result = IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>(map.size)
  for (element in map.entries) {
    result.put(element.key, element.value.sortedWith(comparator))
  }
  return result
}

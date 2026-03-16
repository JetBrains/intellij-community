// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Don't use these utils for anything but plugin dependency analysis!!!
 */
@ApiStatus.Internal
object PluginDependencyAnalysis {

  @VisibleForTesting
  abstract class BFS<V> {
    private val visited = LinkedHashSet<V>()
    private val queue = ArrayDeque<V>()

    fun isVisited(node: V): Boolean = node in visited

    /**
     * Iterator follows the visit order
     */
    fun getVisitedSet(): Set<V> = visited

    fun schedule(node: V): Boolean {
      if (node !in visited) {
        queue.addLast(node)
        return true
      }
      return false
    }

    fun run() {
      while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        if (node !in visited) {
          visited.add(node)
          visit(node)
        }
      }
    }

    abstract fun visit(node: V)
  }

  @ApiStatus.Internal
  sealed class DependencyRef {
    class Plugin(val pluginId: PluginId) : DependencyRef()
    class ContentModule(val moduleId: PluginModuleId) : DependencyRef()
  }
}

/**
 * The resulting sequence includes the plugin descriptor module too ([plugin] itself).
 */
@ApiStatus.Internal
fun PluginDependencyAnalysis.sequenceRequiredModules(initContext: PluginInitializationContext, plugin: PluginMainDescriptor): Sequence<PluginModuleDescriptor> {
  return sequence {
    yield(plugin)
    for (module in plugin.contentModules) {
      // TODO the result of `requiredIfAvailable` is determined early now, but it should be determined later,
      //  this code should be adjusted when it happens (use `initContext.environmentConfiguredModules`)
      if (module.moduleLoadingRule.required) {
        yield(module)
      }
    }
  }
}

/**
 * Sequences strict dependencies for the [this] descriptor.
 */
@ApiStatus.Internal
fun PluginDependencyAnalysis.sequenceStrictDependencies(descriptor: IdeaPluginDescriptorImpl): Sequence<DependencyRef> {
  return sequence {
    for (depends in descriptor.dependencies) {
      if (depends.isOptional) continue
      yield(DependencyRef.Plugin(depends.pluginId))
    }
    for (id in descriptor.moduleDependencies.plugins) {
      yield(DependencyRef.Plugin(id))
    }
    for (id in descriptor.moduleDependencies.modules) {
      yield(DependencyRef.ContentModule(id))
    }
  }
}

/**
 * Sequences legacy `depends` style optional dependencies for the [this] descriptor.
 */
@ApiStatus.Internal
fun PluginDependencyAnalysis.sequenceOptionalDependsStatements(plugin: PluginMainDescriptor): Sequence<PluginDependency> {
  return sequence {
    for (depends in plugin.dependencies) {
      if (depends.isOptional) {
        yield(depends)
      }
    }
  }
}

/**
 * Calculates a set of all modules that are required for [plugins] to load.
 * If [ambiguousPluginSet] happens to resolve some ids to many modules, they all are included in the resulting sequence.
 * Resulting set includes [plugins].
 * 
 * @param unresolvedStrictDependenciesCollector optional mutable list that is populated with unresolved strict dependencies
 */
@ApiStatus.Internal
fun PluginDependencyAnalysis.getRequiredTransitiveModules(
  initContext: PluginInitializationContext,
  plugins: Collection<PluginModuleDescriptor>,
  ambiguousPluginSet: AmbiguousPluginSet,
  unresolvedStrictDependenciesCollector: MutableList<Pair<PluginModuleDescriptor, DependencyRef>>? = null,
): Set<PluginModuleDescriptor> {
  val bfs = object : PluginDependencyAnalysis.BFS<PluginModuleDescriptor>() {
    override fun visit(node: PluginModuleDescriptor) {
      for (dep in PluginDependencyAnalysis.sequenceStrictDependencies(node)) {
        val resolvedNodes = when (dep) {
          is DependencyRef.Plugin -> ambiguousPluginSet.resolvePluginId(dep.pluginId)
          is DependencyRef.ContentModule -> ambiguousPluginSet.resolveContentModuleId(dep.moduleId)
        }
        var resolvedAny = false
        for (depModule in resolvedNodes) {
          schedule(depModule)
          resolvedAny = true
        }
        if (!resolvedAny) {
          unresolvedStrictDependenciesCollector?.add(node to dep)
        }
      }
      if (node is PluginMainDescriptor) {
        for (reqModule in PluginDependencyAnalysis.sequenceRequiredModules(initContext, node)) {
          schedule(reqModule)
        }
      }
      if (node is ContentModuleDescriptor) {
        schedule(node.parent)
      }
    }
  }
  plugins.forEach(bfs::schedule)
  bfs.run()
  return bfs.getVisitedSet()
}
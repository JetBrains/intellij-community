// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Condition
import com.intellij.util.containers.MultiMap
import com.intellij.util.graph.GraphAlgorithms
import com.intellij.util.graph.GraphGenerator
import com.intellij.util.graph.InboundSemiGraph

public object JavaProjectDependenciesAnalyzer {
  /**
   * Returns order entries which are exported to `module` from its direct `dependency`, and which aren't available via other dependencies.
   * @return map from a direct or transitive dependency of `dependency` parameter to a corresponding direct dependency of `dependency` parameter.
   */
  @JvmStatic
  public fun findExportedDependenciesReachableViaThisDependencyOnly(module: Module,
                                                                    dependency: Module,
                                                                    rootModelProvider: RootModelProvider): Map<OrderEntry, OrderEntry> {
    val moduleOrderEntry = OrderEntryUtil.findModuleOrderEntry(rootModelProvider.getRootModel(module), dependency)
                           ?: throw IllegalArgumentException("Cannot find dependency from $module to $dependency")

    val withoutThisDependency = Condition { entry: OrderEntry ->
      !(entry is ModuleOrderEntry && entry.getOwnerModule() == module && dependency == entry.module)
    }
    var enumerator = rootModelProvider.getRootModel(module).orderEntries()
      .satisfying(withoutThisDependency)
      .using(rootModelProvider)
      .compileOnly()
      .recursively().exportedOnly()
    if (moduleOrderEntry.scope.isForProductionCompile) {
      enumerator = enumerator.productionOnly()
    }

    val reachableModules = LinkedHashSet<Module>()
    val reachableLibraries = LinkedHashSet<Library>()
    enumerator.forEach { entry ->
      when (entry) {
        is ModuleSourceOrderEntry -> reachableModules.add(entry.getOwnerModule())
        is ModuleOrderEntry -> entry.module?.let { reachableModules.add(it) }
        is LibraryOrderEntry -> entry.library?.let { reachableLibraries.add(it) }
      }
      true
    }

    val result = LinkedHashMap<OrderEntry, OrderEntry>()
    rootModelProvider.getRootModel(dependency)
      .orderEntries().using(rootModelProvider).exportedOnly().withoutSdk().withoutModuleSourceEntries()
      .forEach { direct ->
        when {
          direct is ModuleOrderEntry -> {
            val depModule = direct.module
            if (depModule != null && depModule !in reachableModules) {
              result[direct] = direct
              rootModelProvider.getRootModel(depModule).orderEntries().using(rootModelProvider).exportedOnly().withoutSdk().recursively()
                .forEach { transitive ->
                  if (transitive is ModuleSourceOrderEntry && transitive.ownerModule !in reachableModules && depModule != transitive.ownerModule
                      || transitive is LibraryOrderEntry && transitive.library != null && transitive.library !in reachableLibraries) {
                    if (transitive !in result) {
                      result[transitive] = direct
                    }
                  }
                  true
                }
            }
          }
          direct is LibraryOrderEntry && direct.library != null && direct.library !in reachableLibraries -> {
            result[direct] = direct
          }
        }
        true
      }
    return result
  }

  /**
   * Remove items which are exported by other items.
   */
  public fun removeDuplicatingDependencies(originalDependencies: Collection<Module>): List<Module> {
    val dependencies = originalDependencies.distinct()
    val moduleToDominatingDependency = MultiMap.createLinkedSet<Module, Module>()
    for (dependency in dependencies) {
      ModuleRootManager.getInstance(dependency).orderEntries()
        .exportedOnly().recursively().compileOnly().runtimeOnly().productionOnly()
        .forEachModule {
          moduleToDominatingDependency.putValue(it, dependency)
          true
        }
    }
    val dominationGraph = GraphGenerator.generate(object : InboundSemiGraph<Module> {
      override fun getNodes(): Collection<Module> = dependencies
      override fun getIn(n: Module): Iterator<Module> = moduleToDominatingDependency.get(n).iterator()
    })

    val sccGraph = GraphAlgorithms.getInstance().computeSCCGraph(dominationGraph)
    val toRemove = HashSet<Module>()
    for (scc in sccGraph.nodes) {
      if (sccGraph.getIn(scc).hasNext()) {
        toRemove.addAll(scc.nodes)
      }
      else if (scc.nodes.size > 1) {
        toRemove.addAll(scc.nodes.toList().drop(1))
      }
    }
    return dependencies - toRemove
  }
}
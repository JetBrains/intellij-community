// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import java.util.concurrent.ConcurrentHashMap

@Internal
fun interface RuntimeDependencyResolver {
  fun getRuntimeDependencies(moduleName: String): List<String>
}

internal class RuntimeDependencyIndex(
  private val helper: JarPackagerDependencyHelper,
) : RuntimeDependencyResolver {
  private val runtimeDependencies = ConcurrentHashMap<String, List<String>>()

  override fun getRuntimeDependencies(moduleName: String): List<String> {
    return runtimeDependencies.computeIfAbsent(moduleName) {
      helper.getModuleDependencies(moduleName).toList()
    }
  }
}

@Internal
fun collectTransitiveRuntimeDependencies(
  roots: List<Pair<String, PersistentList<String>>>,
  blockedOrSeen: HashSet<String>,
  omitFromResult: Set<String>,
  dependencyResolver: RuntimeDependencyResolver,
): List<Pair<String, PersistentList<String>>> {
  if (roots.isEmpty()) {
    return emptyList()
  }

  val result = ArrayList<Pair<String, PersistentList<String>>>()
  var frontier = roots
  while (frontier.isNotEmpty()) {
    val nextFrontier = ArrayList<Pair<String, PersistentList<String>>>()
    for ((moduleName, dependencyChain) in frontier) {
      val chain = dependencyChain.adding(moduleName)
      for (dependencyName in dependencyResolver.getRuntimeDependencies(moduleName)) {
        if (!blockedOrSeen.add(dependencyName)) {
          continue
        }

        val dependency = dependencyName to chain
        nextFrontier.add(dependency)
        if (!omitFromResult.contains(dependencyName)) {
          result.add(dependency)
        }
      }
    }

    nextFrontier.sortBy { it.first }
    frontier = nextFrontier
  }
  return result
}

@Internal
fun computeEmbeddedModuleDependenciesInOrder(
  embeddedModulesInProcessingOrder: List<ModuleItem>,
  excludedModuleNames: Set<String>,
  alreadyIncluded: HashSet<String>,
  dependencyResolver: RuntimeDependencyResolver,
): Set<ModuleItem> {
  val result = LinkedHashSet<ModuleItem>()
  val rootChain = persistentListOf<String>()

  for (embeddedModule in embeddedModulesInProcessingOrder) {
    val dependencies = collectTransitiveRuntimeDependencies(
      roots = listOf(embeddedModule.moduleName to rootChain),
      blockedOrSeen = alreadyIncluded,
      omitFromResult = emptySet(),
      dependencyResolver = dependencyResolver,
    )
    for ((depName, chain) in dependencies) {
      if (excludedModuleNames.contains(depName)) {
        continue
      }

      result.add(
        ModuleItem(
          moduleName = depName,
          relativeOutputFile = embeddedModule.relativeOutputFile,
          reason = ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES + " <- " + chain.asReversed().joinToString(separator = " <- "),
          moduleSet = embeddedModule.moduleSet,
        )
      )
    }
  }

  return result
}

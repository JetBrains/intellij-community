// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validation.rules

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache

/**
 * Finds missing transitive dependencies using parallel BFS traversal.
 *
 * ## Performance Characteristics
 *
 * - **Parallel validation**: Each module is validated concurrently via coroutines
 * - **Module analysis is cached**: Uses [descriptorCache] with thread-safe AsyncCache
 * - **BFS traversal stops at boundaries**: Does not traverse into cross-plugin/cross-product modules
 * - **Per-product context**: Result depends on [availableModules] and [allowedMissing]
 *
 * ## Traversal Logic
 *
 * For each dependency encountered:
 * 1. If in [availableModules] → valid, traverse into its dependencies
 * 2. If in [allowedMissing] → skip (explicitly allowed to be missing)
 * 3. If in [crossPluginModules] and source is NOT critical → valid, don't traverse (external responsibility)
 * 4. If in [crossProductModules] and source is NOT critical → valid, don't traverse (external responsibility)
 * 5. Otherwise → missing dependency error
 *
 * @param modules Modules to check dependencies for
 * @param availableModules Modules that are available within this product/context
 * @param descriptorCache Cache for module descriptor information (thread-safe, shared across all validations)
 * @param allowedMissing Dependencies explicitly allowed to be missing (per-product configuration)
 * @param crossPluginModules Modules from other plugins (valid for non-critical modules)
 * @param crossProductModules Modules from other products (valid for non-critical modules)
 * @param criticalModules Modules with EMBEDDED or REQUIRED loading that cannot depend on cross-plugin modules
 * @return Map of missing dependency → set of modules that need it
 */
internal suspend fun findMissingTransitiveDependencies(
  modules: Set<String>,
  availableModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  allowedMissing: Set<String> = emptySet(),
  crossPluginModules: Set<String> = emptySet(),
  crossProductModules: Set<String> = emptySet(),
  criticalModules: Set<String> = emptySet(),
): Map<String, Set<String>> {
  if (modules.isEmpty()) {
    return emptyMap()
  }

  // PARALLEL: Each module's BFS is independent, cache is thread-safe
  val perModuleResults = coroutineScope {
    modules.map { moduleName ->
      async {
        validateModuleDependencies(
          moduleName = moduleName,
          availableModules = availableModules,
          descriptorCache = descriptorCache,
          allowedMissing = allowedMissing,
          crossPluginModules = crossPluginModules,
          crossProductModules = crossProductModules,
          isCritical = criticalModules.contains(moduleName),
        )
      }
    }.awaitAll()
  }

  // Merge results
  val missingDeps = HashMap<String, MutableSet<String>>()
  for ((moduleName, moduleMissingDeps) in perModuleResults) {
    for (dep in moduleMissingDeps) {
      missingDeps.computeIfAbsent(dep) { HashSet() }.add(moduleName)
    }
  }
  return missingDeps
}

/**
 * Validates a single module's transitive dependencies via BFS.
 * Thread-safe: uses only thread-safe cache and local state.
 */
private suspend fun validateModuleDependencies(
  moduleName: String,
  availableModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  allowedMissing: Set<String>,
  crossPluginModules: Set<String>,
  crossProductModules: Set<String>,
  isCritical: Boolean,
): Pair<String, Set<String>> {
  val info = descriptorCache.getOrAnalyze(moduleName) ?: return moduleName to emptySet()

  val missingDeps = HashSet<String>()
  val visited = HashSet<String>()
  visited.add(moduleName)
  val queue = ArrayDeque(info.dependencies)

  while (queue.isNotEmpty()) {
    val dep = queue.removeFirst()
    if (!visited.add(dep)) {
      continue
    }

    when {
      availableModules.contains(dep) -> {
        // Present - traverse into its deps (cache handles deduplication)
        descriptorCache.getOrAnalyze(dep)?.dependencies?.let { queue.addAll(it) }
      }
      allowedMissing.contains(dep) -> {
        // Explicitly allowed to be missing - skip
      }
      !isCritical && crossPluginModules.contains(dep) -> {
        // Valid cross-plugin optional dependency - skip
      }
      !isCritical && crossProductModules.contains(dep) -> {
        // Module exists in another product - valid for optional deps
      }
      else -> {
        // Missing dependency
        missingDeps.add(dep)
      }
    }
  }

  return moduleName to missingDeps
}

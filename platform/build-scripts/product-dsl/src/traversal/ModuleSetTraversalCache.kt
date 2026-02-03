// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import java.util.concurrent.ConcurrentHashMap

/**
 * Cached module information including loading mode and source tracking.
 * Used by [ModuleSetTraversalCache.getModulesWithLoading] for rich module data.
 */
internal data class CachedModuleInfo(
  @JvmField val name: String,
  @JvmField val loading: ModuleLoadingRuleValue?,
  @JvmField val sourceModuleSet: String,
)

/**
 * Thread-safe cache for module set traversal operations.
 * Eliminates repeated O(n) lookups and traversals by pre-computing and caching results.
 *
 * Usage: Create once at the start of analysis, pass through all analysis functions.
 *
 * Performance impact:
 * - O(n) → O(1) for module set lookups via [getModuleSet]
 * - 96% reduction in graph traversals via [getNestedSets] and [getModuleNames]
 */
internal class ModuleSetTraversalCache(allModuleSets: List<ModuleSet>) {
  /** O(1) lookup by name instead of O(n) linear search */
  private val moduleSetsByName: Map<String, ModuleSet> = allModuleSets.associateBy { it.name }

  /** Cached nested set names (transitive) - thread-safe for concurrent coroutine access */
  private val nestedSetsCache = ConcurrentHashMap<String, Set<String>>()

  /** Cached module names (transitive) - thread-safe for concurrent coroutine access */
  private val moduleNamesCache = ConcurrentHashMap<String, Set<String>>()

  /** Cached modules with loading info (transitive) - thread-safe for concurrent coroutine access */
  private val modulesWithLoadingCache = ConcurrentHashMap<String, Map<String, CachedModuleInfo>>()

  /**
   * O(1) lookup of module set by name.
   * Replaces: `allModuleSets.firstOrNull { it.name == name }`
   */
  fun getModuleSet(name: String): ModuleSet? = moduleSetsByName.get(name)

  private fun getNestedSets(setName: String): Set<String> {
    return nestedSetsCache.computeIfAbsent(setName) {
      collectNestedSetsInternal(setName)
    }
  }

  /**
   * Collects all module names from a module set and its nested sets with caching.
   * Thread-safe via ConcurrentHashMap.computeIfAbsent.
   * Derives from [getModulesWithLoading] to avoid duplicate traversal.
   */
  fun getModuleNames(moduleSet: ModuleSet): Set<String> {
    return moduleNamesCache.computeIfAbsent(moduleSet.name) {
      getModulesWithLoading(moduleSet).keys
    }
  }

  /**
   * Collects all module names by set name with caching.
   */
  fun getModuleNames(setName: String): Set<String> {
    val moduleSet = moduleSetsByName.get(setName) ?: return emptySet()
    return getModuleNames(moduleSet)
  }

  /**
   * Collects all modules with their loading modes and source tracking.
   * Thread-safe via ConcurrentHashMap.computeIfAbsent.
   *
   * @return Map from module name to [CachedModuleInfo] with loading mode and source module set
   */
  fun getModulesWithLoading(moduleSet: ModuleSet): Map<String, CachedModuleInfo> {
    return modulesWithLoadingCache.computeIfAbsent(moduleSet.name) {
      collectModulesWithLoadingInternal(moduleSet)
    }
  }

  /**
   * Checks if one module set transitively includes another.
   * Uses cached nested sets for O(1) lookup after initial computation.
   */
  fun isTransitivelyNested(parentSetName: String, childSetName: String): Boolean {
    return getNestedSets(parentSetName).contains(childSetName)
  }

  /**
   * Collects all module names from a product's content spec.
   * Combines modules from all referenced module sets with additional modules.
   */
  internal fun collectProductModuleNames(contentSpec: ProductModulesContentSpec): Set<String> {
    val result = HashSet<String>()
    for (msRef in contentSpec.moduleSets) {
      result.addAll(getModuleNames(msRef.moduleSet.name))
    }
    for (module in contentSpec.additionalModules) {
      result.add(module.name)
    }
    return result
  }

  /**
   * Returns all module names from all module sets.
   * Useful for global validation across the entire codebase.
   */
  fun getAllModuleNames(): Set<String> {
    val result = HashSet<String>()
    for (moduleSet in moduleSetsByName.values) {
      result.addAll(getModuleNames(moduleSet))
    }
    return result
  }

  private fun collectModulesWithLoadingInternal(moduleSet: ModuleSet): Map<String, CachedModuleInfo> {
    val result = LinkedHashMap<String, CachedModuleInfo>()
    collectModulesRecursiveWithLoading(moduleSet, result)
    return result
  }

  private fun collectModulesRecursiveWithLoading(
    moduleSet: ModuleSet,
    result: MutableMap<String, CachedModuleInfo>,
  ) {
    for (module in moduleSet.modules) {
      if (!result.containsKey(module.name)) {  // First occurrence wins
        result.put(module.name, CachedModuleInfo(module.name, module.loading, moduleSet.name))
      }
    }
    for (nestedSet in moduleSet.nestedSets) {
      collectModulesRecursiveWithLoading(nestedSet, result)
    }
  }

  private fun collectNestedSetsInternal(setName: String): Set<String> {
    val result = LinkedHashSet<String>()
    collectNestedSetsRecursive(setName = setName, result = result, visited = HashSet(), chain = ArrayList())
    return result
  }

  private fun collectNestedSetsRecursive(
    setName: String,
    result: MutableSet<String>,
    visited: MutableSet<String>,
    chain: MutableList<String>
  ) {
    // Cycle detection
    if (setName in chain) {
      error("Cycle detected in module set hierarchy: ${chain.joinToString(" → ")} → $setName")
    }

    // Skip already fully processed sets
    if (setName in visited) return

    chain.add(setName)

    val moduleSet = moduleSetsByName.get(setName)
    if (moduleSet != null) {
      for (nestedSet in moduleSet.nestedSets) {
        result.add(nestedSet.name)
        collectNestedSetsRecursive(nestedSet.name, result, visited, chain)
      }
    }

    chain.removeLast()
    visited.add(setName)
  }
}

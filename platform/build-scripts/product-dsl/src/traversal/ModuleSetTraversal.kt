// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.traversal

import org.jetbrains.intellij.build.productLayout.ModuleSet

/**
 * Unified utilities for traversing module set hierarchies.
 * 
 * Provides BFS/DFS traversal with:
 * - Proper cycle detection (throws error instead of silent failure)
 * - Optional caching for repeated queries
 * - Consistent API across all analysis functions
 */
internal object ModuleSetTraversal {
  private fun collectNestedSetsRecursive(
    setName: String,
    allModuleSets: List<ModuleSet>,
    result: MutableSet<String>,
    visited: MutableSet<String>,
    chain: MutableList<String>
  ) {
    // Cycle detection: if setName is already in current chain, we have a cycle
    if (setName in chain) {
      error("Cycle detected in module set hierarchy: ${chain.joinToString(" → ")} → $setName")
    }
    
    // Skip already fully processed sets
    if (setName in visited) return
    
    chain.add(setName)
    
    val moduleSet = allModuleSets.firstOrNull { it.name == setName }
    if (moduleSet != null) {
      for (nestedSet in moduleSet.nestedSets) {
        result.add(nestedSet.name)
        collectNestedSetsRecursive(nestedSet.name, allModuleSets, result, visited, chain)
      }
    }
    
    chain.removeLast()
    visited.add(setName)
  }
  
  /**
   * Builds an inclusion chain from a top-level set to a target set.
   * 
   * Example: `buildInclusionChain("essential", "libraries.core", sets)` might return
   * `["essential", "libraries", "libraries.core"]`
   * 
   * @param fromSet Starting module set name
   * @param toSet Target module set name
   * @param allModuleSets All module sets to search in
   * @return List representing the chain, or null if no path exists
   */
  fun buildInclusionChain(
    fromSet: String,
    toSet: String,
    allModuleSets: List<ModuleSet>
  ): List<String>? {
    return buildChainRecursive(currentSet = fromSet, targetSet = toSet, allModuleSets = allModuleSets, visited = HashSet())
  }
  
  private fun buildChainRecursive(
    currentSet: String,
    targetSet: String,
    allModuleSets: List<ModuleSet>,
    visited: MutableSet<String>
  ): List<String>? {
    if (currentSet in visited) return null
    visited.add(currentSet)
    
    val moduleSet = allModuleSets.firstOrNull { it.name == currentSet } ?: return null
    
    // Check if target is directly nested
    if (moduleSet.nestedSets.any { it.name == targetSet }) {
      return listOf(currentSet, targetSet)
    }
    
    // Recursively search through nested sets
    for (nestedSet in moduleSet.nestedSets) {
      val chain = buildChainRecursive(nestedSet.name, targetSet, allModuleSets, visited)
      if (chain != null) {
        return listOf(currentSet) + chain
      }
    }
    
    return null
  }
  
  /**
   * Collects all module names from a module set and its nested sets.
   * Returns a Set (deduplicated).
   * 
   * @param moduleSet The module set to collect from
   * @param cache Optional cache for repeated queries
   * @return Set of all module names
   */
  fun collectAllModuleNames(
    moduleSet: ModuleSet,
    cache: MutableMap<String, Set<String>>? = null
  ): Set<String> {
    cache?.get(moduleSet.name)?.let { return it }
    
    val result = HashSet<String>()
    collectModulesRecursive(moduleSet, result)
    
    cache?.put(moduleSet.name, result)
    return result
  }
  
  private fun collectModulesRecursive(moduleSet: ModuleSet, result: MutableSet<String>) {
    for (module in moduleSet.modules) {
      result.add(module.name)
    }
    for (nestedSet in moduleSet.nestedSets) {
      collectModulesRecursive(nestedSet, result)
    }
  }

  private fun collectModulesIntoList(moduleSet: ModuleSet, result: MutableList<String>) {
    for (module in moduleSet.modules) {
      result.add(module.name)
    }
    for (nestedSet in moduleSet.nestedSets) {
      collectModulesIntoList(nestedSet, result)
    }
  }
  
  /**
   * Checks if a module set (recursively) contains a specific module.
   * 
   * @param moduleSet The module set to check
   * @param moduleName The module name to find
   * @return true if the module is in the set or any nested set
   */
  fun containsModule(moduleSet: ModuleSet, moduleName: String): Boolean {
    if (moduleSet.modules.any { it.name == moduleName }) {
      return true
    }
    return moduleSet.nestedSets.any { containsModule(it, moduleName) }
  }
}
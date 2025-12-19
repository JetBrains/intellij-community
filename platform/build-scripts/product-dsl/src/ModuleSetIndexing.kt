// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout

import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversal

/**
 * Index of all modules in module sets for dependency validation.
 * Includes reachability information to support scope-aware validation.
 *
 * @param allModules All modules found across all module sets
 * @param moduleToDirectSets Map from module name to module sets that directly contain it
 * @param moduleToReachableModules Map from module name to all modules reachable from its containing sets
 */
internal data class ModuleSetIndex(
  val allModules: Set<String>,
  val moduleToDirectSets: Map<String, Set<String>>,
  val moduleToReachableModules: Map<String, Set<String>>
)

/**
 * Builds an index of all modules across all module sets (recursively).
 * Used for validating that dependencies reference modules that actually exist in module sets.
 * Tracks direct containment (module in moduleSet.modules) and builds a reachability graph
 * to determine which modules are accessible from each module's perspective.
 *
 * @param allModuleSets List of all module sets to index
 * @return Module set index with containment and reachability information
 */
internal fun buildModuleSetIndex(allModuleSets: List<ModuleSet>): ModuleSetIndex {
  val allModules = HashSet<String>()
  val moduleToDirectSets = HashMap<String, MutableSet<String>>()
  
  // Build a map from module set name to the ModuleSet object for reachability computation
  val moduleSetsByName = HashMap<String, ModuleSet>()
  
  /**
   * Processes a single module set: records its modules and processes nested sets recursively.
   */
  fun processModuleSet(moduleSet: ModuleSet) {
    moduleSetsByName.put(moduleSet.name, moduleSet)
    
    // Record direct modules (those in this moduleSet.modules)
    for (module in moduleSet.modules) {
      allModules.add(module.name)
      moduleToDirectSets.computeIfAbsent(module.name) { HashSet() }.add(moduleSet.name)
    }
    
    // Process nested sets recursively
    for (nestedSet in moduleSet.nestedSets) {
      processModuleSet(nestedSet)
    }
  }
  
  // Process all module sets
  allModuleSets.forEach { processModuleSet(it) }
  
  // Build reachability graph
  val moduleToReachableModules = buildReachabilityGraph(
    moduleToDirectSets = moduleToDirectSets,
    moduleSetsByName = moduleSetsByName
  )
  
  return ModuleSetIndex(
    allModules = allModules,
    moduleToDirectSets = moduleToDirectSets.mapValues { it.value.toSet() },
    moduleToReachableModules = moduleToReachableModules
  )
}

/**
 * Builds a reachability graph showing which modules are accessible from each module's perspective.
 * For each module, collects all modules reachable through its containing module sets
 * (i.e., all modules in the same module set hierarchy).
 *
 * @param moduleToDirectSets Map from module to sets that directly contain it
 * @param moduleSetsByName Map from module set name to ModuleSet object
 * @return Map from module name to set of all reachable modules
 */
private fun buildReachabilityGraph(
  moduleToDirectSets: Map<String, MutableSet<String>>,
  moduleSetsByName: Map<String, ModuleSet>
): Map<String, Set<String>> {
  val moduleToReachableModules = HashMap<String, Set<String>>()
  // Cache for repeated traversals of the same module set
  val cache = HashMap<String, Set<String>>()
  
  // For each module, collect all modules reachable from its containing sets
  for ((moduleName, directSets) in moduleToDirectSets) {
    val reachable = HashSet<String>()
    
    for (setName in directSets) {
      val moduleSet = moduleSetsByName.get(setName)
      if (moduleSet != null) {
        reachable.addAll(ModuleSetTraversal.collectAllModuleNames(moduleSet, cache))
      }
    }
    
    moduleToReachableModules.put(moduleName, reachable)
  }
  
  return moduleToReachableModules
}

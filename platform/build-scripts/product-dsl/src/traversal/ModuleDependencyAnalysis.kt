// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.traversal

import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.buildModuleSetIndex
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies

/**
 * Result of getting module dependencies from JPS model.
 */
@Serializable
internal data class ModuleDependenciesResult(
  val moduleName: String,
  val dependencies: List<String>,
  val transitiveDependencies: List<String>? = null,
  val error: String? = null
)

/**
 * Result of checking module reachability within a module set.
 */
@Serializable
internal data class ModuleReachabilityResult(
  val moduleName: String,
  val moduleSetName: String,
  val satisfied: List<String>,
  val missing: List<MissingDependency>,
  val error: String? = null
)

/**
 * Information about a missing dependency.
 */
@Serializable
internal data class MissingDependency(
  val dependencyName: String,
  val existsGlobally: Boolean,
  val foundInModuleSets: List<String>
)

/**
 * Result of finding a dependency path between two modules.
 */
@Serializable
internal data class DependencyPathResult(
  val fromModule: String,
  val toModule: String,
  val path: List<String>?,
  val pathExists: Boolean,
  val error: String? = null
)

/**
 * Gets direct JPS module dependencies for a given module.
 * This queries the JPS model to find production runtime dependencies.
 *
 * @param moduleName The module name to query
 * @param outputProvider Provider for accessing JPS modules
 * @param includeTransitive If true, collects ALL transitive dependencies (BFS traversal)
 * @return Module dependencies result with direct deps, and optionally transitive deps
 */
internal fun getModuleDependencies(
  moduleName: String,
  outputProvider: ModuleOutputProvider,
  includeTransitive: Boolean = false
): ModuleDependenciesResult {
  try {
    val jpsModule = outputProvider.findModule(moduleName)
    if (jpsModule == null) {
      return ModuleDependenciesResult(
        moduleName = moduleName,
        dependencies = emptyList(),
        transitiveDependencies = null,
        error = "Module '$moduleName' not found in JPS model"
      )
    }

    val dependencies = jpsModule.getProductionModuleDependencies()
      .map { it.moduleReference.moduleName }
      .toList()

    val transitiveDeps = if (includeTransitive) {
      collectTransitiveDependencies(moduleName, outputProvider)
    } else null

    return ModuleDependenciesResult(
      moduleName = moduleName,
      dependencies = dependencies,
      transitiveDependencies = transitiveDeps
    )
  }
  catch (e: Exception) {
    return ModuleDependenciesResult(
      moduleName = moduleName,
      dependencies = emptyList(),
      transitiveDependencies = null,
      error = "Failed to get dependencies: ${e.message}"
    )
  }
}

/**
 * Collects ALL transitive dependencies of a module using BFS traversal.
 * Returns a sorted list of all reachable modules (excluding the start module itself).
 *
 * @param moduleName Starting module
 * @param outputProvider Provider for accessing JPS modules
 * @return Sorted list of all transitive dependencies
 */
private fun collectTransitiveDependencies(
  moduleName: String,
  outputProvider: ModuleOutputProvider
): List<String> {
  val queue = ArrayDeque<String>()
  val visited = mutableSetOf<String>()
  val allDeps = mutableSetOf<String>()

  queue.add(moduleName)
  visited.add(moduleName)

  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()

    // Get dependencies of current module
    val jpsModule = outputProvider.findModule(current) ?: continue
    val dependencies = jpsModule.getProductionModuleDependencies()
      .map { it.moduleReference.moduleName }
      .toList()

    for (dependency in dependencies) {
      // Add to result (exclude the starting module)
      if (dependency != moduleName) {
        allDeps.add(dependency)
      }

      // Continue BFS if not visited
      if (dependency !in visited) {
        visited.add(dependency)
        queue.add(dependency)
      }
    }
  }

  return allDeps.sorted()
}

/**
 * Checks which dependencies of a module are reachable within a module set hierarchy.
 * Uses the same reachability logic as the dependency validator to show which dependencies
 * are satisfied and which are missing.
 *
 * @param moduleName The module to check
 * @param moduleSetName The module set context
 * @param allModuleSets All available module sets
 * @param outputProvider Provider for accessing JPS modules
 * @return Module reachability result
 */
internal fun checkModuleReachability(
  moduleName: String,
  moduleSetName: String,
  allModuleSets: List<ModuleSetMetadata>,
  outputProvider: ModuleOutputProvider
): ModuleReachabilityResult {
  try {
    // Get JPS dependencies
    val dependenciesResult = getModuleDependencies(moduleName, outputProvider)
    if (dependenciesResult.error != null) {
      return ModuleReachabilityResult(
        moduleName = moduleName,
        moduleSetName = moduleSetName,
        satisfied = emptyList(),
        missing = emptyList(),
        error = dependenciesResult.error
      )
    }

    // Find the module set (O(1) lookup via map)
    val moduleSetsByName = allModuleSets.associateBy { it.moduleSet.name }
    val moduleSetEntry = moduleSetsByName.get(moduleSetName)
    if (moduleSetEntry == null) {
      return ModuleReachabilityResult(
        moduleName = moduleName,
        moduleSetName = moduleSetName,
        satisfied = emptyList(),
        missing = emptyList(),
        error = "Module set '$moduleSetName' not found"
      )
    }

    // Build reachability index
    val index = buildModuleSetIndex(allModuleSets.map { it.moduleSet })
    
    // Check if module is in this module set
    val moduleDirectSets = index.moduleToDirectSets[moduleName] ?: emptySet()
    if (moduleSetName !in moduleDirectSets) {
      return ModuleReachabilityResult(
        moduleName = moduleName,
        moduleSetName = moduleSetName,
        satisfied = emptyList(),
        missing = emptyList(),
        error = "Module '$moduleName' is not directly in module set '$moduleSetName'"
      )
    }

    // Get reachable modules for this module
    val reachableModules = index.moduleToReachableModules[moduleName] ?: emptySet()

    // Classify dependencies
    val satisfied = mutableListOf<String>()
    val missing = mutableListOf<MissingDependency>()

    for (dependency in dependenciesResult.dependencies) {
      if (dependency in reachableModules) {
        satisfied.add(dependency)
      }
      else {
        // Find which module sets contain this dependency
        val containingModuleSets = index.moduleToDirectSets[dependency] ?: emptySet()
        
        missing.add(MissingDependency(
          dependencyName = dependency,
          existsGlobally = dependency in index.allModules,
          foundInModuleSets = containingModuleSets.toList()
        ))
      }
    }

    return ModuleReachabilityResult(
      moduleName = moduleName,
      moduleSetName = moduleSetName,
      satisfied = satisfied,
      missing = missing
    )
  }
  catch (e: Exception) {
    return ModuleReachabilityResult(
      moduleName = moduleName,
      moduleSetName = moduleSetName,
      satisfied = emptyList(),
      missing = emptyList(),
      error = "Failed to check reachability: ${e.message}"
    )
  }
}

/**
 * Finds a transitive dependency path from one module to another.
 * Uses BFS to find the shortest path through JPS module dependencies.
 *
 * OPTIMIZED: Uses parent pointers instead of copying paths at each node,
 * reducing memory allocations from O(depth * nodes) to O(nodes).
 *
 * @param fromModule Starting module
 * @param toModule Target module
 * @param outputProvider Provider for accessing JPS modules
 * @return Dependency path result
 */
internal fun findDependencyPath(
  fromModule: String,
  toModule: String,
  outputProvider: ModuleOutputProvider
): DependencyPathResult {
  try {
    // Check if both modules exist
    if (outputProvider.findModule(fromModule) == null) {
      return DependencyPathResult(
        fromModule = fromModule,
        toModule = toModule,
        path = null,
        pathExists = false,
        error = "Module '$fromModule' not found in JPS model"
      )
    }

    if (outputProvider.findModule(toModule) == null) {
      return DependencyPathResult(
        fromModule = fromModule,
        toModule = toModule,
        path = null,
        pathExists = false,
        error = "Module '$toModule' not found in JPS model"
      )
    }

    // BFS using parent pointers (memory-efficient)
    val queue = ArrayDeque<String>()
    val parent = mutableMapOf<String, String?>()

    queue.add(fromModule)
    parent[fromModule] = null  // Root has no parent

    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()

      // Found target - reconstruct path
      if (current == toModule) {
        val path = reconstructPath(parent, fromModule, toModule)
        return DependencyPathResult(
          fromModule = fromModule,
          toModule = toModule,
          path = path,
          pathExists = true
        )
      }

      // Get dependencies of current module
      val jpsModule = outputProvider.findModule(current) ?: continue
      val dependencies = jpsModule.getProductionModuleDependencies()
        .map { it.moduleReference.moduleName }
        .toList()

      for (dependency in dependencies) {
        if (dependency !in parent) {  // Not visited
          parent[dependency] = current
          queue.add(dependency)
        }
      }
    }

    // No path found
    return DependencyPathResult(
      fromModule = fromModule,
      toModule = toModule,
      path = null,
      pathExists = false
    )
  }
  catch (e: Exception) {
    return DependencyPathResult(
      fromModule = fromModule,
      toModule = toModule,
      path = null,
      pathExists = false,
      error = "Failed to find path: ${e.message}"
    )
  }
}

/**
 * Reconstructs path from parent pointers.
 * Only called once when target is found, avoiding repeated list allocations.
 */
private fun reconstructPath(
  parent: Map<String, String?>,
  fromModule: String,
  toModule: String
): List<String> {
  val path = mutableListOf<String>()
  var current: String? = toModule
  while (current != null) {
    path.add(current)
    current = parent.get(current)
  }
  return path.reversed()
}



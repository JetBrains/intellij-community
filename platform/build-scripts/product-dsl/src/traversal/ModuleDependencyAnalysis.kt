// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_MODULE
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.TargetNode
import com.intellij.platform.pluginGraph.containsEdge
import kotlinx.serialization.Serializable

/**
 * Result of getting module dependencies from the plugin graph.
 */
@Serializable
internal data class ModuleDependenciesResult(
  val moduleName: TargetName,
  @JvmField val dependencies: List<TargetName>,
  @JvmField val dependencyDetails: List<TargetDependencyInfo> = emptyList(),
  @JvmField val transitiveDependencies: List<TargetName>? = null,
  @JvmField val error: String? = null
)

/**
 * Direct dependency entry with optional scope.
 */
@Serializable
internal data class TargetDependencyInfo(
  val name: TargetName,
  @JvmField val scope: TargetDependencyScope? = null,
)

/**
 * Result of resolving owning plugins for a module.
 */
@Serializable
internal data class ModuleOwnersResult(
  val moduleName: ContentModuleName,
  @JvmField val owners: List<OwningPlugin>,
  @JvmField val includeTestSources: Boolean,
  @JvmField val error: String? = null,
)

/**
 * Result of checking module reachability within a module set.
 */
@Serializable
internal data class ModuleReachabilityResult(
  val moduleName: ContentModuleName,
  @JvmField val moduleSetName: String,
  @JvmField val satisfied: List<TargetName>,
  @JvmField val missing: List<MissingDependency>,
  @JvmField val error: String? = null
)

/**
 * Information about a missing dependency.
 */
@Serializable
internal data class MissingDependency(
  val dependencyName: TargetName,
  @JvmField val existsGlobally: Boolean,
  @JvmField val foundInModuleSets: List<String>
)

/**
 * Result of finding a dependency path between two modules.
 */
@Serializable
internal data class DependencyPathResult(
  val fromModule: TargetName,
  val toModule: TargetName,
  @JvmField val path: List<TargetName>?,
  @JvmField val pathWithScopes: List<DependencyPathEntry>? = null,
  @JvmField val pathExists: Boolean,
  @JvmField val error: String? = null
)

/**
 * Entry in dependency path with optional edge scope from the previous node.
 */
@Serializable
internal data class DependencyPathEntry(
  val module: TargetName,
  @JvmField val scope: TargetDependencyScope? = null,
)

internal fun getModuleDependencies(
  moduleName: TargetName,
  graph: PluginGraph,
  includeTransitive: Boolean = false,
  includeTestDependencies: Boolean = false,
): ModuleDependenciesResult {
  return try {
    graph.query {
      val targetId = resolveTargetId(moduleName)
      if (targetId == null) {
        return@query ModuleDependenciesResult(
          moduleName = moduleName,
          dependencies = emptyList(),
          dependencyDetails = emptyList(),
          transitiveDependencies = null,
          error = "Module '${moduleName.value}' not found in graph"
        )
      }

      val dependencyDetails = collectDirectTargetDependencies(targetId, includeTestDependencies)
      val dependencies = dependencyDetails.map { it.name }
      val transitiveDeps = if (includeTransitive) {
        collectTransitiveTargetDependencies(targetId, includeTestDependencies)
      }
      else {
        null
      }

      ModuleDependenciesResult(
        moduleName = moduleName,
        dependencies = dependencies,
        dependencyDetails = dependencyDetails,
        transitiveDependencies = transitiveDeps
      )
    }
  }
  catch (e: Exception) {
    return ModuleDependenciesResult(
      moduleName = moduleName,
      dependencies = emptyList(),
      dependencyDetails = emptyList(),
      transitiveDependencies = null,
      error = "Failed to get dependencies: ${e.message}"
    )
  }
}

internal fun getModuleOwners(
  moduleName: ContentModuleName,
  graph: PluginGraph,
  includeTestSources: Boolean = false,
): ModuleOwnersResult {
  return try {
    val owners = collectOwningPlugins(graph, moduleName, includeTestSources)
      .sortedWith(compareBy({ it.pluginId.value }, { it.name.value }, { it.isTest }))
    ModuleOwnersResult(
      moduleName = moduleName,
      owners = owners,
      includeTestSources = includeTestSources,
      error = null,
    )
  }
  catch (e: Exception) {
    ModuleOwnersResult(
      moduleName = moduleName,
      owners = emptyList(),
      includeTestSources = includeTestSources,
      error = "Failed to resolve module owners: ${e.message}",
    )
  }
}

internal fun checkModuleReachability(
  moduleName: ContentModuleName,
  moduleSetName: String,
  graph: PluginGraph,
): ModuleReachabilityResult {
  return try {
    graph.query {
      val moduleSetNode = moduleSet(moduleSetName)
      if (moduleSetNode == null) {
        return@query ModuleReachabilityResult(
          moduleName = moduleName,
          moduleSetName = moduleSetName,
          satisfied = emptyList(),
          missing = emptyList(),
          error = "Module set '$moduleSetName' not found"
        )
      }

      val moduleNode = contentModule(moduleName)
      if (moduleNode == null) {
        return@query ModuleReachabilityResult(
          moduleName = moduleName,
          moduleSetName = moduleSetName,
          satisfied = emptyList(),
          missing = emptyList(),
          error = "Module '${moduleName.value}' not found in graph"
        )
      }

      if (!containsEdge(EDGE_CONTAINS_MODULE, moduleSetNode.id, moduleNode.id)) {
        return@query ModuleReachabilityResult(
          moduleName = moduleName,
          moduleSetName = moduleSetName,
          satisfied = emptyList(),
          missing = emptyList(),
          error = "Module '${moduleName.value}' is not directly in module set '$moduleSetName'"
        )
      }

      val targetId = resolveTargetId(TargetName(moduleName.value))
      if (targetId == null) {
        return@query ModuleReachabilityResult(
          moduleName = moduleName,
          moduleSetName = moduleSetName,
          satisfied = emptyList(),
          missing = emptyList(),
          error = "Module '${moduleName.value}' has no backing target in graph"
        )
      }

      val dependencies = collectDirectTargetDependencies(targetId, includeTestDependencies = false).map { it.name }
      val satisfied = mutableListOf<TargetName>()
      val missing = mutableListOf<MissingDependency>()

      for (dependency in dependencies) {
        val depModule = contentModule(ContentModuleName(dependency.value))
        if (depModule != null && moduleSetNode.containsModuleRecursive(depModule)) {
          satisfied.add(dependency)
        }
        else {
          val containingSets = if (depModule != null) collectDirectModuleSets(depModule) else emptyList()
          missing.add(MissingDependency(
            dependencyName = dependency,
            existsGlobally = containingSets.isNotEmpty(),
            foundInModuleSets = containingSets
          ))
        }
      }

      ModuleReachabilityResult(
        moduleName = moduleName,
        moduleSetName = moduleSetName,
        satisfied = satisfied,
        missing = missing
      )
    }
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

internal fun findDependencyPath(
  fromModule: TargetName,
  toModule: TargetName,
  graph: PluginGraph,
  includeTestDependencies: Boolean = false,
  includeScopes: Boolean = false,
): DependencyPathResult {
  return try {
    graph.query {
      val fromTargetId = resolveTargetId(fromModule)
      if (fromTargetId == null) {
        return@query DependencyPathResult(
          fromModule = fromModule,
          toModule = toModule,
          path = null,
          pathExists = false,
          error = "Module '${fromModule.value}' not found in graph"
        )
      }

      val toTargetId = resolveTargetId(toModule)
      if (toTargetId == null) {
        return@query DependencyPathResult(
          fromModule = fromModule,
          toModule = toModule,
          path = null,
          pathExists = false,
          error = "Module '${toModule.value}' not found in graph"
        )
      }

      val queue = ArrayDeque<Int>()
      val parent = HashMap<Int, Int>()
      val parentScope = if (includeScopes) HashMap<Int, TargetDependencyScope?>() else null

      queue.add(fromTargetId)
      parent.put(fromTargetId, -1)

      while (queue.isNotEmpty()) {
        val currentId = queue.removeFirst()

        if (currentId == toTargetId) {
          val path = reconstructTargetPath(parent, toTargetId)
          val pathWithScopes = if (includeScopes) {
            reconstructTargetPathWithScopes(parent, parentScope ?: emptyMap(), toTargetId)
          }
          else {
            null
          }
          return@query DependencyPathResult(
            fromModule = fromModule,
            toModule = toModule,
            path = path,
            pathWithScopes = pathWithScopes,
            pathExists = true
          )
        }

        TargetNode(currentId).dependsOn { dep ->
          val scope = dep.scope()
          if (!shouldIncludeDependency(scope, includeTestDependencies)) return@dependsOn
          val depId = dep.targetId
          if (!parent.containsKey(depId)) {
            parent.put(depId, currentId)
            if (parentScope != null) {
              parentScope.put(depId, scope)
            }
            queue.add(depId)
          }
        }
      }

      DependencyPathResult(
        fromModule = fromModule,
        toModule = toModule,
        path = null,
        pathWithScopes = null,
        pathExists = false
      )
    }
  }
  catch (e: Exception) {
    return DependencyPathResult(
      fromModule = fromModule,
      toModule = toModule,
      path = null,
      pathWithScopes = null,
      pathExists = false,
      error = "Failed to find path: ${e.message}"
    )
  }
}

private fun GraphScope.resolveTargetId(moduleName: TargetName): Int? {
  val moduleNode = contentModule(ContentModuleName(moduleName.value))
  if (moduleNode != null) {
    var targetId: Int? = null
    moduleNode.backedBy { target -> targetId = target.id }
    if (targetId != null) return targetId
  }
  return target(moduleName.value)?.id
}

private fun GraphScope.collectDirectTargetDependencies(
  targetId: Int,
  includeTestDependencies: Boolean,
): List<TargetDependencyInfo> {
  val result = ArrayList<TargetDependencyInfo>()
  TargetNode(targetId).dependsOn { dep ->
    val scope = dep.scope()
    if (!shouldIncludeDependency(scope, includeTestDependencies)) return@dependsOn
    result.add(TargetDependencyInfo(TargetName(dep.target().name()), scope))
  }
  return result
}

private fun GraphScope.collectTransitiveTargetDependencies(
  startTargetId: Int,
  includeTestDependencies: Boolean,
): List<TargetName> {
  val visited = HashSet<Int>()
  val queue = ArrayDeque<Int>()
  val allDeps = HashSet<TargetName>()

  queue.add(startTargetId)
  visited.add(startTargetId)

  while (queue.isNotEmpty()) {
    val currentId = queue.removeFirst()
    TargetNode(currentId).dependsOn { dep ->
      val scope = dep.scope()
      if (!shouldIncludeDependency(scope, includeTestDependencies)) return@dependsOn
      val depId = dep.targetId
      if (depId != startTargetId) {
        allDeps.add(TargetName(dep.target().name()))
      }
      if (visited.add(depId)) {
        queue.add(depId)
      }
    }
  }

  return allDeps.sortedBy { it.value }
}

private fun shouldIncludeDependency(scope: TargetDependencyScope?, includeTestDependencies: Boolean): Boolean {
  return when (scope) {
    TargetDependencyScope.TEST -> includeTestDependencies
    TargetDependencyScope.PROVIDED -> false
    else -> true
  }
}

private fun GraphScope.collectDirectModuleSets(module: ContentModuleNode): List<String> {
  val result = ArrayList<String>()
  module.contentProductionSources { source ->
    if (source.kind == ContentSourceKind.MODULE_SET) {
      result.add(source.name())
    }
  }
  return result
}

private fun GraphScope.reconstructTargetPath(parent: Map<Int, Int>, toTargetId: Int): List<TargetName> {
  val pathIds = mutableListOf<Int>()
  var current = toTargetId
  while (current >= 0) {
    pathIds.add(current)
    current = parent.get(current) ?: -1
  }
  pathIds.reverse()
  return pathIds.map { TargetName(TargetNode(it).name()) }
}

private fun GraphScope.reconstructTargetPathWithScopes(
  parent: Map<Int, Int>,
  parentScope: Map<Int, TargetDependencyScope?>,
  toTargetId: Int,
): List<DependencyPathEntry> {
  val pathIds = mutableListOf<Int>()
  var current = toTargetId
  while (current >= 0) {
    pathIds.add(current)
    current = parent.get(current) ?: -1
  }
  pathIds.reverse()
  return pathIds.mapIndexed { index, id ->
    val scope = if (index == 0) null else parentScope.get(id)
    DependencyPathEntry(TargetName(TargetNode(id).name()), scope)
  }
}

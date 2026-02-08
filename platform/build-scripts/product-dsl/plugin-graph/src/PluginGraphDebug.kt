// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.pluginGraph

import androidx.collection.mutableIntListOf

/**
 * Debug utilities for [PluginGraph] analysis and troubleshooting.
 *
 * These functions are intended for interactive debugging and investigation,
 * not for production code. They print to stdout for easy consumption.
 *
 * ## Usage
 *
 * ```kotlin
 * with(PluginGraphDebug) {
 *   // Trace why module A depends on module B
 *   pluginGraph.traceDependencyPath("intellij.platform.lang", "intellij.libraries.hamcrest")
 *
 *   // Print all deps of a module
 *   pluginGraph.printModuleDeps("intellij.platform.lang")
 *
 *   // Print modules in a product
 *   pluginGraph.printProductModules("IDEA")
 * }
 * ```
 */
object PluginGraphDebug {
  /**
   * Trace the dependency path from a source module to a target module.
   * Uses BFS and records parent links to reconstruct the shortest path.
   *
   * @param sourceName Name of the source module
   * @param targetName Name of the target module to find
   * @param edgeType Edge type to traverse (default: production content module deps)
   */
  internal fun PluginGraph.traceDependencyPath(
    sourceName: String,
    targetName: String,
    edgeType: Int = EDGE_CONTENT_MODULE_DEPENDS_ON,
  ) {
    query {
      val sourceId = nodeId(sourceName, NODE_CONTENT_MODULE)
      if (sourceId < 0) {
        println("DEBUG: Source module '$sourceName' not found in graph!")
        return@query
      }

      val visited = HashMap<Int, Int>() // nodeId -> parentId
      val queue = mutableIntListOf()
      queue.add(sourceId)
      visited.put(sourceId, -1) // -1 = no parent (start node)

      var targetId = -1
      var head = 0
      while (head < queue.size) {
        val currentId = queue[head++]
        val currentName = name(currentId)

        if (currentName == targetName) {
          targetId = currentId
          break
        }

        val needsUnpack = isPackedEdgeType(edgeType)
        store.forEachSuccessor(edgeType, currentId) { entry ->
          val nextId = if (needsUnpack) unpackNodeId(entry) else entry
          if (!visited.containsKey(nextId)) {
            visited.put(nextId, currentId)
            queue.add(nextId)
          }
        }
      }

      if (targetId == -1) {
        println("DEBUG: Target '$targetName' not reachable from '$sourceName'")
        return@query
      }

      // Reconstruct path
      val path = ArrayList<String>()
      var current = targetId
      while (current != -1) {
        path.add(name(current))
        current = visited.get(current) ?: -1
      }
      path.reverse()

      val edgeTypeName = when (edgeType) {
        EDGE_CONTENT_MODULE_DEPENDS_ON -> "CONTENT_MODULE_DEPENDS_ON (prod)"
        EDGE_CONTENT_MODULE_DEPENDS_ON_TEST -> "CONTENT_MODULE_DEPENDS_ON_TEST (test)"
        EDGE_TARGET_DEPENDS_ON -> "TARGET_DEPENDS_ON (build)"
        EDGE_CONTAINS_CONTENT -> "CONTAINS_CONTENT (prod)"
        EDGE_CONTAINS_CONTENT_TEST -> "CONTAINS_CONTENT (test)"
        else -> "edge type $edgeType"
      }

      println("DEBUG: Dependency path via $edgeTypeName (${path.size} steps):")
      for ((index, step) in path.withIndex()) {
        println("DEBUG:   ${"  ".repeat(index)}-> $step")
      }
    }
  }

  /**
   * Print all direct dependencies of a module.
   *
   * @param moduleName Name of the module
   * @param edgeType Edge type to query (default: production content module deps)
   */
  internal fun printModuleDeps(
    graph: PluginGraph,
    moduleName: String,
    edgeType: Int = EDGE_CONTENT_MODULE_DEPENDS_ON,
  ) {
    graph.query {
      val moduleId = nodeId(moduleName, NODE_CONTENT_MODULE)
      if (moduleId < 0) {
        println("DEBUG: Module '$moduleName' not found in graph!")
        return@query
      }

      val edgeTypeName = when (edgeType) {
        EDGE_CONTENT_MODULE_DEPENDS_ON -> "production"
        EDGE_CONTENT_MODULE_DEPENDS_ON_TEST -> "test"
        EDGE_TARGET_DEPENDS_ON -> "target"
        else -> "type $edgeType"
      }

      val depCount = store.successorCount(edgeType, moduleId)
      if (depCount == 0) {
        println("DEBUG: Module '$moduleName' has no $edgeTypeName dependencies")
        return@query
      }

      println("DEBUG: Module '$moduleName' $edgeTypeName dependencies ($depCount):")
      val needsUnpack = isPackedEdgeType(edgeType)
      store.forEachSuccessor(edgeType, moduleId) { entry ->
        val depId = if (needsUnpack) unpackNodeId(entry) else entry
        println("DEBUG:   -> ${name(depId)}")
      }
    }
  }

  /**
   * Print all modules available in a product (from module sets and plugin content).
   *
   * @param productName Name of the product
   */
  internal fun printProductModules(graph: PluginGraph, productName: String) {
    graph.query {
      val productNode = product(productName)
      if (productNode == null) {
        println("DEBUG: Product '$productName' not found in graph!")
        return@query
      }

      println("DEBUG: Modules in product '$productName':")

      // From module sets
      val moduleSetModules = ArrayList<ContentModuleNode>()
      productNode.includesModuleSet { moduleSet -> moduleSet.modulesRecursive { moduleSetModules.add(it) } }
      println("DEBUG:   From module sets (${moduleSetModules.size}):")
      for (module in moduleSetModules.sortedBy { it.name() }) {
        println("DEBUG:     - ${module.name()}")
      }

      // From bundled plugins
      val pluginModules = ArrayList<ContentModuleNode>()
      productNode.bundles { plugin -> plugin.containsContent { module, _ -> pluginModules.add(module) } }
      println("DEBUG:   From bundled plugins (${pluginModules.size}):")
      for (module in pluginModules.sortedBy { it.name() }) {
        println("DEBUG:     - ${module.name()}")
      }

      // From product content
      val productContent = ArrayList<ContentModuleNode>()
      productNode.containsContent { module, _ -> productContent.add(module) }
      if (productContent.isNotEmpty()) {
        println("DEBUG:   From product content (${productContent.size}):")
        for (module in productContent.sortedBy { it.name() }) {
          println("DEBUG:     - ${module.name()}")
        }
      }
    }
  }

  /**
   * Print plugins that contain a specific module.
   *
   * @param moduleName Name of the module
   */
  internal fun printContainingPlugins(graph: PluginGraph, moduleName: String) {
    graph.query {
      val moduleId = nodeId(moduleName, NODE_CONTENT_MODULE)
      if (moduleId < 0) {
        println("DEBUG: Module '$moduleName' not found in graph!")
        return@query
      }

      println("DEBUG: Module '$moduleName' is content of:")
      var found = false

      // Check production plugins
      predecessors(EDGE_CONTAINS_CONTENT, moduleId)?.forEach { packedEntry ->
        found = true
        val pluginId = unpackNodeId(packedEntry)
        if (kind(pluginId) == NODE_PLUGIN) {
          val loadingMode = packedToLoadingRule(unpackLoadingMode(packedEntry)).name
          println("DEBUG:   - ${name(pluginId)} [loading=$loadingMode] (prod)")
        }
      }

      // Check test plugins
      predecessors(EDGE_CONTAINS_CONTENT_TEST, moduleId)?.forEach { packedEntry ->
        found = true
        val pluginId = unpackNodeId(packedEntry)
        if (kind(pluginId) == NODE_PLUGIN) {
          val loadingMode = packedToLoadingRule(unpackLoadingMode(packedEntry)).name
          println("DEBUG:   - ${name(pluginId)} [loading=$loadingMode] (test)")
        }
      }

      if (!found) {
        println("DEBUG:   (none)")
      }
    }
  }

  /**
   * Compare production vs test dependencies for a module.
   * Useful for debugging TEST scope dependency leakage.
   *
   * @param moduleName Name of the module
   */
  internal fun compareProdVsTestDeps(graph: PluginGraph, moduleName: String) {
    graph.query {
      val moduleId = nodeId(moduleName, NODE_CONTENT_MODULE)
      if (moduleId < 0) {
        println("DEBUG: Module '$moduleName' not found in graph!")
        return@query
      }

      val prodDeps = successors(EDGE_CONTENT_MODULE_DEPENDS_ON, moduleId)
                       ?.let { list -> buildSet { list.forEach { add(name(it)) } } }
                     ?: emptySet()

      val testDeps = successors(EDGE_CONTENT_MODULE_DEPENDS_ON_TEST, moduleId)
                       ?.let { list -> buildSet { list.forEach { add(name(it)) } } }
                     ?: emptySet()

      val testOnly = testDeps - prodDeps

      println("DEBUG: Dependency comparison for '$moduleName':")
      println("DEBUG:   Production deps: ${prodDeps.size}")
      println("DEBUG:   Test deps: ${testDeps.size}")
      println("DEBUG:   Test-only deps (TEST scope in JPS): ${testOnly.size}")

      if (testOnly.isNotEmpty()) {
        println("DEBUG:   Test-only dependencies:")
        for (dep in testOnly.sorted()) {
          println("DEBUG:     - $dep")
        }
      }
    }
  }
}

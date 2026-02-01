// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ModuleSetNode
import com.intellij.platform.pluginGraph.PluginGraph

internal fun collectModuleSetModuleNames(graph: PluginGraph, moduleSetName: String): Set<ContentModuleName> {
  return graph.query {
    val moduleSetNode = moduleSet(moduleSetName) ?: return emptySet()
    val result = HashSet<ContentModuleName>()
    moduleSetNode.modulesRecursive { result.add(it.name()) }
    result
  }
}

internal fun collectModuleSetDirectModuleNames(graph: PluginGraph, moduleSetName: String): Set<ContentModuleName> {
  return graph.query {
    val moduleSetNode = moduleSet(moduleSetName) ?: return@query emptySet()
    val result = HashSet<ContentModuleName>()
    moduleSetNode.containsModule { module, _ -> result.add(module.name()) }
    result
  }
}

internal fun collectModuleSetDirectNestedNames(graph: PluginGraph, moduleSetName: String): Set<String> {
  return graph.query {
    val moduleSetNode = moduleSet(moduleSetName) ?: return@query emptySet()
    val result = HashSet<String>()
    moduleSetNode.nestedSet { nestedSet -> result.add(nestedSet.name()) }
    result
  }
}

internal fun isModuleSetTransitivelyNested(graph: PluginGraph, parentSetName: String, childSetName: String): Boolean {
  return graph.query {
    val parentNode = moduleSet(parentSetName) ?: return@query false
    val childNode = moduleSet(childSetName) ?: return@query false
    if (parentNode.id == childNode.id) return@query true
    parentNode.includesModuleSetRecursive(childNode)
  }
}

internal fun findModuleSetInclusionChain(
  graph: PluginGraph,
  parentSetName: String,
  childSetName: String,
): List<String>? {
  return graph.query {
    val parentNode = moduleSet(parentSetName) ?: return@query null
    val childNode = moduleSet(childSetName) ?: return@query null
    if (parentNode.id == childNode.id) return@query listOf(parentNode.name())

    val queue = ArrayDeque<Int>()
    val parentByNode = HashMap<Int, Int>()
    queue.add(parentNode.id)
    parentByNode.put(parentNode.id, -1)

    while (queue.isNotEmpty()) {
      val currentId = queue.removeFirst()
      if (currentId == childNode.id) {
        return@query buildModuleSetChain(parentByNode, childNode.id)
      }

      ModuleSetNode(currentId).nestedSet { nestedSet ->
        if (!parentByNode.containsKey(nestedSet.id)) {
          parentByNode.put(nestedSet.id, currentId)
          queue.add(nestedSet.id)
        }
      }
    }

    null
  }
}

private fun GraphScope.buildModuleSetChain(
  parentByNode: Map<Int, Int>,
  targetId: Int,
): List<String> {
  val chainIds = ArrayList<Int>()
  var current = targetId
  while (current >= 0) {
    chainIds.add(current)
    current = parentByNode.get(current) ?: -1
  }
  chainIds.reverse()
  return chainIds.map { ModuleSetNode(it).name() }
}

internal fun collectProductModuleSetNames(graph: PluginGraph, productName: String): Set<String> {
  return graph.query {
    val productNode = product(productName) ?: return@query emptySet()
    val result = HashSet<String>()
    productNode.includesModuleSet { moduleSet -> result.add(moduleSet.name()) }
    result
  }
}

internal fun collectDirectProductModuleNames(graph: PluginGraph, productName: String): Set<ContentModuleName> {
  return graph.query {
    val productNode = product(productName) ?: return@query emptySet()
    val result = HashSet<ContentModuleName>()
    productNode.containsContent { module, _ -> result.add(module.name()) }
    result
  }
}

internal fun collectProductModuleNames(graph: PluginGraph, productName: String): Set<ContentModuleName> {
  return graph.query {
    val productNode = product(productName) ?: return@query emptySet()
    val result = HashSet<ContentModuleName>()
    productNode.containsContent { module, _ -> result.add(module.name()) }
    productNode.includesModuleSet { moduleSet ->
      moduleSet.modulesRecursive { result.add(it.name()) }
    }
    result
  }
}

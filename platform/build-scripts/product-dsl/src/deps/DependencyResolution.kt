// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.deps

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.ContentBuildData
import org.jetbrains.intellij.build.productLayout.traversal.OwningPlugin
import org.jetbrains.intellij.build.productLayout.traversal.collectBundledPluginNames
import org.jetbrains.intellij.build.productLayout.traversal.collectOwningPlugins

/**
 * Shared resolution context for dependency planning.
 */
internal class DependencyResolutionContext(private val graph: PluginGraph) {
  private data class OwningKey(val module: ContentModuleName, val includeTestSources: Boolean)

  private val owningPluginsCache = HashMap<OwningKey, Set<OwningPlugin>>()
  private val bundledPluginsCache = HashMap<String, Set<TargetName>>()

  fun resolveOwningPlugins(module: ContentModuleName, includeTestSources: Boolean = false): Set<OwningPlugin> {
    val key = OwningKey(module, includeTestSources)
    return owningPluginsCache.getOrPut(key) {
      collectOwningPlugins(graph, module, includeTestSources = includeTestSources)
    }
  }

  fun resolveBundledPlugins(productName: String, additionalBundles: Set<TargetName> = emptySet()): Set<TargetName> {
    val bundled = bundledPluginsCache.getOrPut(productName) {
      collectBundledPluginNames(graph, productName)
    }
    return if (additionalBundles.isEmpty()) bundled else bundled + additionalBundles
  }
}

internal fun resolveAllowedMissingPluginIds(
  moduleName: ContentModuleName,
  allowedMissingByModule: Map<ContentModuleName, Set<PluginId>>,
  dependencyChains: Map<ContentModuleName, List<ContentModuleName>>,
  globalAllowedMissing: Set<PluginId>,
  extraAllowedMissing: Set<PluginId> = emptySet(),
): Set<PluginId> {
  val moduleAllowed = allowedMissingByModule[moduleName] ?: emptySet()
  val rootModule = dependencyChains[moduleName]?.firstOrNull()
  val rootAllowed = if (rootModule == null) emptySet() else allowedMissingByModule[rootModule] ?: emptySet()

  if (moduleAllowed.isEmpty() && rootAllowed.isEmpty() && globalAllowedMissing.isEmpty() && extraAllowedMissing.isEmpty()) {
    return emptySet()
  }

  val result = LinkedHashSet<PluginId>(
    moduleAllowed.size + rootAllowed.size + globalAllowedMissing.size + extraAllowedMissing.size
  )
  result.addAll(moduleAllowed)
  result.addAll(rootAllowed)
  result.addAll(globalAllowedMissing)
  result.addAll(extraAllowedMissing)
  return result
}

internal fun buildAllowedMissingByModule(
  contentData: ContentBuildData,
): Map<ContentModuleName, Set<PluginId>> {
  val result = HashMap<ContentModuleName, Set<PluginId>>()
  for (block in contentData.contentBlocks) {
    for (module in block.modules) {
      val allowed = module.allowedMissingPluginIds
      if (allowed.isNotEmpty()) {
        result[module.name] = allowed.toSet()
      }
    }
  }
  return result
}

internal fun collectResolvableModules(
  graph: PluginGraph,
  productName: String,
  additionalBundledPluginTargetNames: Set<TargetName> = emptySet(),
): Set<ContentModuleName> = graph.query {
  val result = LinkedHashSet<ContentModuleName>()
  val productNode = product(productName) ?: return@query result

  productNode.includesModuleSet { moduleSet ->
    moduleSet.modulesRecursive { result.add(it.contentName()) }
  }
  productNode.containsContent { module, _ -> result.add(module.contentName()) }
  productNode.bundles { plugin ->
    plugin.containsContent { module, _ -> result.add(module.contentName()) }
  }

  if (additionalBundledPluginTargetNames.isNotEmpty()) {
    for (target in additionalBundledPluginTargetNames) {
      val pluginNode = plugin(target.value) ?: continue
      pluginNode.containsContent { module, _ -> result.add(module.contentName()) }
    }
  }

  result
}

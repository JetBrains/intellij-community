// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import kotlinx.serialization.Serializable

@Serializable
internal data class OwningPlugin(
  val name: TargetName,
  val pluginId: PluginId,
  @JvmField val isTest: Boolean,
)

internal fun collectBundledPluginNames(graph: PluginGraph, productName: String): Set<TargetName> {
  return graph.query {
    val names = HashSet<TargetName>()
    product(productName)?.bundles { plugin -> names.add(plugin.name()) }
    names
  }
}

internal fun collectOwningPlugins(
  graph: PluginGraph,
  moduleName: ContentModuleName,
  includeTestSources: Boolean = false,
): Set<OwningPlugin> {
  return graph.query {
    val moduleNode = contentModule(moduleName) ?: return@query emptySet()
    val owners = LinkedHashSet<OwningPlugin>()
    moduleNode.owningPlugins(includeTestSources) { pluginNode ->
      val idValue = pluginNode.pluginIdOrNull ?: return@owningPlugins
      owners.add(OwningPlugin(pluginNode.name(), idValue, pluginNode.isTest))
    }
    owners
  }
}

internal fun collectPluginContentModules(
  graph: PluginGraph,
  pluginModules: Collection<TargetName>,
): Set<ContentModuleName> {
  if (pluginModules.isEmpty()) {
    return emptySet()
  }

  return graph.query {
    val result = LinkedHashSet<ContentModuleName>()
    for (pluginModule in pluginModules) {
      val pluginNode = plugin(pluginModule.value) ?: continue
      pluginNode.containsContent { module, _ -> result.add(module.contentName()) }
    }
    result
  }
}

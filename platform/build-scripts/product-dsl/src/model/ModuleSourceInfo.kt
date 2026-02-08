// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue

/**
 * Complete source info for a module - works for both plugin content modules and module set modules.
 * Single lookup provides all context for error messages.
 * Built once and shared across all validation lookups.
 */
data class ModuleSourceInfo(
  /** The loading mode (EMBEDDED, REQUIRED, OPTIONAL, ON_DEMAND) */
  @JvmField val loadingMode: ModuleLoadingRuleValue? = null,
  /** Plugin containing this module, or null if from module set directly */
  val sourcePlugin: TargetName? = null,
  /** Whether the source plugin is a test plugin (contains test framework modules) */
  @JvmField val isTestPlugin: Boolean = false,
  /** Module set containing this module, or null if from bundled plugin */
  @JvmField val sourceModuleSet: String? = null,
  /** ALL module sets containing this module (for fix suggestions) */
  @JvmField val containingModuleSets: Set<String> = emptySet(),
  /** Products where this module's plugin is bundled (ships with the product) */
  @JvmField val bundledInProducts: Set<String> = emptySet(),
  /** Products where this module's plugin is compatible (installable from marketplace) but not bundled */
  @JvmField val compatibleWithProducts: Set<String> = emptySet(),
)

/**
 * Get source info for a module on-demand by querying the graph.
 * Uses unified contentProductionSources API for type-safe traversal.
 * Used for error message formatting.
 */
fun getModuleSourceInfo(graph: PluginGraph, contentModuleName: ContentModuleName): ModuleSourceInfo? {
  val module = graph.getModule(contentModuleName) ?: return null

  var sourcePlugin: TargetName? = null
  var loadingMode: ModuleLoadingRuleValue? = null
  var isTest = false
  var sourceModuleSet: String? = null
  val bundledInProducts = HashSet<String>()
  val containingModuleSets = HashSet<String>()

  // Single traversal of all content sources using unified API
  graph.query {
    module.contentProductionSources { source ->
      when (source.kind) {
        ContentSourceKind.PLUGIN -> {
          val plugin = source.plugin()
          if (sourcePlugin == null) {
            sourcePlugin = TargetName(source.name())
            isTest = plugin.isTest
            var loading: ModuleLoadingRuleValue? = null
            plugin.containsContent { contentModule, mode ->
              if (contentModule.id == module.id) {
                loading = mode
              }
            }
            loadingMode = loading
          }
          // Get products bundling this plugin
          plugin.bundledByProducts { bundledInProducts.add(it.name()) }
        }
        ContentSourceKind.PRODUCT -> {
          bundledInProducts.add(source.name())
        }
        ContentSourceKind.MODULE_SET -> {
          if (sourceModuleSet == null) {
            sourceModuleSet = source.name()
          }
          // Collect module set hierarchy (for fix suggestions)
          containingModuleSets.addAll(source.moduleSet().hierarchyNames())
          // Get products including this module set
          source.moduleSet().includedByProduct { product ->
            bundledInProducts.add(product.name())
          }
        }
      }
    }
  }

  return ModuleSourceInfo(
    loadingMode = loadingMode,
    sourcePlugin = sourcePlugin,
    isTestPlugin = isTest,
    sourceModuleSet = sourceModuleSet,
    containingModuleSets = containingModuleSets,
    bundledInProducts = bundledInProducts,
  )
}

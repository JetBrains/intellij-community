// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue

/**
 * Check if a module has any plugin as content source.
 *
 * Used to determine if EMBEDDED filtering should be applied.
 * Content modules in plugins should skip EMBEDDED deps, but content
 * modules directly in products should not.
 */
internal fun GraphScope.hasPluginSource(moduleId: Int): Boolean {
  val module = ContentModuleNode(moduleId)
  var hasPlugin = false
  module.contentProductionSources { source ->
    if (source.kind == ContentSourceKind.PLUGIN) {
      hasPlugin = true
    }
  }
  return hasPlugin
}

/**
 * Check if a module is globally embedded (always loaded with product).
 *
 * Returns true only if:
 * - Module is contained by at least one product or module set
 * - Module has EMBEDDED loading in ALL product/module-set sources
 *
 * Plugin content sources do not disqualify a module from being globally embedded.
 * They may declare the module, but product/module-set embedding still makes it
 * always available at runtime.
 *
 * Globally embedded modules don't need explicit XML dependencies because
 * they are always loaded with the product.
 */
internal fun GraphScope.isGloballyEmbedded(moduleId: Int): Boolean {
  val module = ContentModuleNode(moduleId)
  var hasNonPluginSource = false
  var allEmbedded = true

  module.contentProductionSources { source ->
    when (source.kind) {
      ContentSourceKind.PLUGIN -> {
        // Ignore plugin sources for global embedding; product/module-set loading wins.
      }
      ContentSourceKind.PRODUCT -> {
        hasNonPluginSource = true
        var loading: ModuleLoadingRuleValue? = null
        source.product().containsContent { module, mode ->
          if (module.id == moduleId) {
            loading = mode
          }
        }
        if (loading != ModuleLoadingRuleValue.EMBEDDED) {
          allEmbedded = false
        }
      }
      ContentSourceKind.MODULE_SET -> {
        hasNonPluginSource = true
        var loading: ModuleLoadingRuleValue? = null
        source.moduleSet().containsModule { module, mode ->
          if (module.id == moduleId) {
            loading = mode
          }
        }
        if (loading != ModuleLoadingRuleValue.EMBEDDED) {
          allEmbedded = false
        }
      }
    }
  }

  return hasNonPluginSource && allEmbedded
}

/**
 * Returns true when a plugin.xml module dependency should be skipped
 * because the target module is globally embedded.
 */
internal fun GraphScope.shouldSkipEmbeddedPluginDependency(depModuleId: Int): Boolean {
  return isGloballyEmbedded(depModuleId)
}

/**
 * Check if a module has any non-plugin content source (product or module set).
 */
internal fun GraphScope.hasNonPluginSource(moduleId: Int): Boolean {
  val module = ContentModuleNode(moduleId)
  var hasNonPlugin = false
  module.contentProductionSources { source ->
    when (source.kind) {
      ContentSourceKind.PLUGIN -> {}
      ContentSourceKind.PRODUCT, ContentSourceKind.MODULE_SET -> hasNonPlugin = true
    }
  }
  return hasNonPlugin
}

/**
 * Returns true when a content-module dependency should be skipped
 * because the source module belongs only to a plugin and the target is globally embedded.
 */
internal fun GraphScope.shouldSkipEmbeddedContentDependency(sourceModuleId: Int, depModuleId: Int): Boolean {
  return hasPluginSource(sourceModuleId) && !hasNonPluginSource(sourceModuleId) && isGloballyEmbedded(depModuleId)
}

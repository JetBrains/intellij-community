// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue

private val EMBEDDED_CHECK_EXCLUDED_PRODUCTS = setOf(
  // Analysis-only product; not part of runtime embedding contract for plugin dependency filtering.
  "CodeServer",
)

/**
 * Product names used for global embedded checks.
 *
 * This scope excludes products that intentionally do not represent runtime embedding
 * guarantees for plugin/content dependency generation.
 */
internal fun embeddedCheckProductNames(discoveredProductNames: Collection<String>): Set<String> {
  return discoveredProductNames
    .asSequence()
    .filterNot { it in EMBEDDED_CHECK_EXCLUDED_PRODUCTS }
    .toCollection(linkedSetOf())
}

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
 * Check whether module is embedded in a specific product scope.
 *
 * Returns true only if the module has at least one source reachable from this product
 * (product/module-set or bundled plugin content) and every such source has EMBEDDED loading.
 */
internal fun GraphScope.isEmbeddedInProduct(moduleId: Int, productName: String): Boolean {
  val module = ContentModuleNode(moduleId)
  val product = product(productName) ?: return false
  var hasSourceInProduct = false
  var allEmbedded = true

  module.contentProductionSources { source ->
    when (source.kind) {
      ContentSourceKind.PLUGIN -> {
        val sourcePlugin = source.plugin()
        var bundledInProduct = false
        product.bundles { bundledPlugin ->
          if (bundledPlugin.id == sourcePlugin.id) {
            bundledInProduct = true
          }
        }

        if (!bundledInProduct) {
          return@contentProductionSources
        }

        hasSourceInProduct = true
        var loading: ModuleLoadingRuleValue? = null
        sourcePlugin.containsContent { contentModule, mode ->
          if (contentModule.id == moduleId) {
            loading = mode
          }
        }
        if (loading != ModuleLoadingRuleValue.EMBEDDED) {
          allEmbedded = false
        }
      }
      ContentSourceKind.PRODUCT -> {
        val sourceProduct = source.product()
        if (sourceProduct.id != product.id) return@contentProductionSources

        hasSourceInProduct = true
        var loading: ModuleLoadingRuleValue? = null
        sourceProduct.containsContent { contentModule, mode ->
          if (contentModule.id == moduleId) {
            loading = mode
          }
        }
        if (loading != ModuleLoadingRuleValue.EMBEDDED) {
          allEmbedded = false
        }
      }
      ContentSourceKind.MODULE_SET -> {
        val sourceModuleSet = source.moduleSet()
        if (!product.includesModuleSetRecursive(sourceModuleSet)) return@contentProductionSources

        hasSourceInProduct = true
        var loading: ModuleLoadingRuleValue? = null
        sourceModuleSet.containsModule { contentModule, mode ->
          if (contentModule.id == moduleId) {
            loading = mode
          }
        }
        if (loading != ModuleLoadingRuleValue.EMBEDDED) {
          allEmbedded = false
        }
      }
    }
  }
  return hasSourceInProduct && allEmbedded
}

internal fun GraphScope.isGloballyEmbeddedInAllProducts(moduleId: Int, allRealProductNames: Set<String>): Boolean {
  if (allRealProductNames.isEmpty()) {
    return false
  }

  for (productName in allRealProductNames) {
    if (!isEmbeddedInProduct(moduleId, productName)) {
      return false
    }
  }

  return true
}

/**
 * Returns true when a plugin.xml module dependency should be skipped
 * because the target module is globally embedded in the provided product scope.
 */
internal fun GraphScope.shouldSkipEmbeddedPluginDependency(depModuleId: Int, allRealProductNames: Set<String>): Boolean {
  return isGloballyEmbeddedInAllProducts(depModuleId, allRealProductNames)
}

/**
 * Returns products where the plugin is bundled (restricted to discovered real products).
 * Falls back to [allRealProductNames] for non-bundled plugins.
 */
internal fun GraphScope.embeddedCheckProductsForPlugin(pluginId: Int, allRealProductNames: Set<String>): Set<String> {
  val bundledProducts = linkedSetOf<String>()
  products { product ->
    val productName = product.name()
    if (productName !in allRealProductNames) return@products

    var isBundled = false
    product.bundles { bundledPlugin ->
      if (bundledPlugin.id == pluginId) {
        isBundled = true
      }
    }

    if (isBundled) {
      bundledProducts.add(productName)
    }
  }
  return if (bundledProducts.isEmpty()) allRealProductNames else bundledProducts
}

/**
 * Returns products where any bundled production plugin owns this content module.
 * Falls back to [allRealProductNames] when owners are non-bundled everywhere.
 */
internal fun GraphScope.embeddedCheckProductsForPluginOnlyContentModule(moduleId: Int, allRealProductNames: Set<String>): Set<String> {
  val bundledProducts = linkedSetOf<String>()
  products { product ->
    val productName = product.name()
    if (productName !in allRealProductNames) return@products

    var hasBundledOwnerPlugin = false
    product.bundles { bundledPlugin ->
      if (hasBundledOwnerPlugin) return@bundles
      bundledPlugin.containsContent { contentModule, _ ->
        if (contentModule.id == moduleId) {
          hasBundledOwnerPlugin = true
        }
      }
    }

    if (hasBundledOwnerPlugin) {
      bundledProducts.add(productName)
    }
  }
  return if (bundledProducts.isEmpty()) allRealProductNames else bundledProducts
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

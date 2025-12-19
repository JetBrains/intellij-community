// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validation.rules

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache
import org.jetbrains.intellij.build.productLayout.validation.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.validation.ProductModuleIndex

/**
 * Builds product module indices for all products in parallel.
 * Used for sharing computed indices between TIER 2 validation and TIER 3 plugin dep validation.
 */
internal suspend fun buildAllProductIndices(
  productSpecs: List<Pair<String, ProductModulesContentSpec?>>,
  cache: ModuleSetTraversalCache,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>,
): Map<String, ProductModuleIndex> = coroutineScope {
  productSpecs
    .mapNotNull { (productName, spec) ->
      val spec = spec ?: return@mapNotNull null
      async {
        productName to buildProductModuleIndex(
          productName = productName,
          spec = spec,
          cache = cache,
          pluginContentJobs = pluginContentJobs,
        )
      }
    }
    .awaitAll()
    .toMap(HashMap(productSpecs.size))
}

internal suspend fun buildProductModuleIndex(
  productName: String,
  spec: ProductModulesContentSpec,
  cache: ModuleSetTraversalCache,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = emptyMap(),
): ProductModuleIndex {
  val allModules = HashSet<String>()
  val referencedModuleSets = HashSet<String>()
  val moduleLoadings = HashMap<String, com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue?>()
  val moduleToSourcePlugin = HashMap<String, String>()
  val moduleToSourceModuleSet = HashMap<String, String>()

  for (moduleSetWithOverrides in spec.moduleSets) {
    val moduleSet = moduleSetWithOverrides.moduleSet
    referencedModuleSets.add(moduleSet.name)
    // O(1) lookup from cache instead of re-traversal
    for ((moduleName, info) in cache.getModulesWithLoading(moduleSet)) {
      allModules.add(moduleName)
      moduleLoadings.put(moduleName, info.loading)
      moduleToSourceModuleSet.put(moduleName, info.sourceModuleSet)
    }
  }

  for (module in spec.additionalModules) {
    allModules.add(module.name)
    moduleLoadings.put(module.name, module.loading)
  }

  for (pluginName in spec.bundledPlugins) {
    val pluginInfo = pluginContentJobs.get(pluginName)?.await() ?: continue
    for (moduleName in pluginInfo.contentModules) {
      allModules.add(moduleName)
      moduleToSourcePlugin.put(moduleName, pluginName)
      pluginInfo.contentModuleLoadings?.get(moduleName)?.let { moduleLoadings.put(moduleName, it) }
    }
  }

  return ProductModuleIndex(
    productName = productName,
    allModules = allModules,
    referencedModuleSets = referencedModuleSets,
    moduleLoadings = moduleLoadings,
    moduleToSourcePlugin = moduleToSourcePlugin,
    moduleToSourceModuleSet = moduleToSourceModuleSet,
  )
}

/**
 * Builds unified module source info for error message formatting.
 *
 * Three-step process:
 * 1. Track ALL plugin modules from [pluginContentJobs] (ensures non-bundled plugins are covered)
 * 2. Enrich with product-specific bundled info from [productIndices] (adds module sets, bundledInProducts)
 * 3. Compute compatibleWithProducts by inverting [nonBundledPlugins] map
 *
 * This ensures that validation errors for ANY module (bundled or not) will have source info.
 */
internal suspend fun buildModuleSourceInfo(
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>,
  productIndices: Map<String, ProductModuleIndex>,
  nonBundledPlugins: Map<String, Set<String>>,
  @Suppress("UNUSED_PARAMETER") knownPlugins: Set<String>,
): Map<String, ModuleSourceInfo> {
  val result = HashMap<String, ModuleSourceInfo>()

  // Step A: ALL plugin modules (including non-bundled plugins from knownPlugins)
  for ((pluginName, job) in pluginContentJobs) {
    val pluginInfo = job.await() ?: continue
    for (moduleName in pluginInfo.contentModules) {
      result.put(moduleName, ModuleSourceInfo(
        loadingMode = pluginInfo.contentModuleLoadings?.get(moduleName),
        sourcePlugin = pluginName,
      ))
    }
  }

  // Step B: product-specific bundled info (module sets, products where bundled)
  for ((productName, productIndex) in productIndices) {
    for (moduleName in productIndex.allModules) {
      val existing = result.get(moduleName)
      result.put(moduleName, ModuleSourceInfo(
        loadingMode = existing?.loadingMode ?: productIndex.moduleLoadings.get(moduleName),
        sourcePlugin = existing?.sourcePlugin ?: productIndex.moduleToSourcePlugin.get(moduleName),
        sourceModuleSet = existing?.sourceModuleSet ?: productIndex.moduleToSourceModuleSet.get(moduleName),
        bundledInProducts = (existing?.bundledInProducts ?: emptySet()) + productName,
        compatibleWithProducts = existing?.compatibleWithProducts ?: emptySet(),
      ))
    }
  }

  // Step C: Compute compatibleWithProducts by inverting nonBundledPlugins map
  // nonBundledPlugins: product -> set of plugins available in that product
  // We need: plugin -> set of products where plugin is compatible (but not bundled)
  val pluginToCompatibleProducts = HashMap<String, MutableSet<String>>()
  for ((productName, plugins) in nonBundledPlugins) {
    for (pluginName in plugins) {
      pluginToCompatibleProducts.computeIfAbsent(pluginName) { HashSet() }.add(productName)
    }
  }

  // Update modules with compatible products info
  for ((moduleName, info) in result) {
    val pluginName = info.sourcePlugin ?: continue
    val compatibleProducts = pluginToCompatibleProducts.get(pluginName) ?: continue
    // Only add products that are NOT already in bundledInProducts
    val actuallyCompatible = compatibleProducts - info.bundledInProducts
    if (actuallyCompatible.isNotEmpty()) {
      result.put(moduleName, info.copy(compatibleWithProducts = actuallyCompatible))
    }
  }

  return result
}

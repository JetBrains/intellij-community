// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.DerivedPlugin
import com.intellij.platform.buildScripts.pluginModelTool.ProductDerivation
import com.intellij.platform.buildScripts.pluginModelTool.derivePluginPopulation
import com.intellij.platform.distributionContent.DevDistPlatformJars
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct

/**
 * The derived plugin jars, in main module order, and the source-derived platform rows.
 *
 * The dev-distribution build sections and the plan read one derivation, so the two state one packing per plugin.
 */
internal class PluginPackingDerivation(
  @JvmField val plugins: List<DerivedPlugin>,
  @JvmField val platformJars: DevDistPlatformJars,
  /** The population [plugins] was derived from, sorted. See [deriveContentPluginPopulation]. */
  @JvmField val population: Set<String>,
  /** The run configurations the population read. The dev sections and the plan read the same ones. */
  @JvmField val runConfigurations: DevDistRunConfigurations,
)

/**
 * The plugins the dev distribution states content for: the population of the dev sections.
 *
 * [derivePluginPopulation] uses the discovered products, the extra plugins of [DevDistHalf.extraPopulation] of the half
 * of [root], and source-derived platform rows.
 */
@ApiStatus.Internal
fun deriveContentPluginPopulation(
  root: DevDistGenerationRoot,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  platformTable: DevDistPlatformJars,
): Set<String> {
  return derivePluginPopulation(
    products = products.mapNotNull { it.properties as? ProductProperties },
    extraPopulation = root.half.extraPopulation(DevDistRunConfigurations.read(root, outputProvider)),
    platformJars = platformTable,
    outputProvider = outputProvider,
  )
}

/**
 * Derives the packing of every plugin of the population; see [deriveContentPluginPopulation].
 *
 * [ProductDerivation.pluginJars] over the products of [derivation]. No input is a Bazel label, so the derivation runs
 * on every checkout. The dev sections and the plan add the labels. [root] names the run configurations of the half.
 */
internal fun derivePluginPackings(
  root: DevDistGenerationRoot,
  outputProvider: ModuleOutputProvider,
  derivation: ProductDerivation,
): PluginPackingDerivation {
  val runConfigurations = DevDistRunConfigurations.read(root, outputProvider)
  val platformRows = derivation.platformJars
  val splitProducts = root.splitProducts(derivation.products.map { it.name })
  val derived = derivation.pluginJars(root.half.extraPopulation(runConfigurations, splitProducts))
  return PluginPackingDerivation(
    plugins = derived.plugins,
    platformJars = platformRows,
    population = derived.population,
    runConfigurations = runConfigurations,
  )
}

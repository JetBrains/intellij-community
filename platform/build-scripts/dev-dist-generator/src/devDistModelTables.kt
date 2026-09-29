// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.DerivedPlugin
import com.intellij.platform.buildScripts.pluginModelTool.DerivedPluginCandidacy
import com.intellij.platform.buildScripts.pluginModelTool.ProductDerivation
import com.intellij.platform.buildScripts.pluginModelTool.derivePluginPopulation
import com.intellij.platform.distributionContent.DevDistPlatformJars
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import java.util.TreeSet

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
  /**
   * The `content_module_jar` candidacy of every registry layout outside [population], in main module order. Only the
   * community half has any, see [derivePluginPackings].
   */
  @JvmField val registryCandidacies: List<DerivedPluginCandidacy> = emptyList(),
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
 *
 * The community half writes the `content_module_jar` call of every community module that a layout of its registry
 * packs, whether a community product plans the layout or not. The ultimate half then reuses the call. So the community
 * half also derives each registry layout outside the population, and keeps only its candidacy, see
 * [PluginPackingDerivation.registryCandidacies]. The registry is the plugin layouts of the split products of the half.
 */
internal fun derivePluginPackings(
  root: DevDistGenerationRoot,
  outputProvider: ModuleOutputProvider,
  derivation: ProductDerivation,
): PluginPackingDerivation {
  val runConfigurations = DevDistRunConfigurations.read(root, outputProvider)
  val platformRows = derivation.platformJars
  val splitProducts = root.splitProducts(derivation.products.map { it.name })
  val extraPopulation = root.half.extraPopulation(runConfigurations, splitProducts)
  val registryLayouts = if (root.dependentIsCommunity) {
    val population = derivePluginPopulation(
      products = derivation.properties,
      extraPopulation = extraPopulation,
      platformJars = platformRows,
      outputProvider = outputProvider,
    )
    derivation.products
      .filter { it.name in splitProducts }
      .flatMap { (it.properties as? ProductProperties)?.productLayout?.pluginLayouts?.value.orEmpty() }
      .mapTo(TreeSet()) { it.mainModule }
      .filterTo(TreeSet()) { it !in population && outputProvider.findModule(it) != null }
  }
  else {
    emptySet()
  }
  val derived = derivation.pluginJars(if (registryLayouts.isEmpty()) extraPopulation else extraPopulation + registryLayouts)
  return PluginPackingDerivation(
    plugins = derived.plugins.filter { it.mainModule !in registryLayouts },
    platformJars = platformRows,
    population = derived.population.filterTo(TreeSet()) { it !in registryLayouts },
    runConfigurations = runConfigurations,
    registryCandidacies = derived.plugins.filter { it.mainModule in registryLayouts }.map { it.packing.candidacy },
  )
}

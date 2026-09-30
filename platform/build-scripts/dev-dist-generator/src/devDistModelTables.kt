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
import java.nio.file.Path
import java.util.TreeSet

/**
 * The derived plugin jars, in main module order, and the source-derived platform rows.
 *
 * The dev-distribution build sections and the plan read one derivation, so the two state one packing per plugin.
 */
internal class PluginPackingDerivation(
  @JvmField val plugins: List<DerivedPlugin>,
  @JvmField val platformJars: DevDistPlatformJars,
  /**
   * The product population, sorted. See [deriveContentPluginPopulation]. [plugins] also holds each layout of
   * [registryLayouts] outside it.
   */
  @JvmField val population: Set<String>,
  /** The run configurations the population read. The dev sections and the plan read the same ones. */
  @JvmField val runConfigurations: DevDistRunConfigurations,
  /**
   * The main modules of the registry layouts that have a JPS module, sorted. [plugins] holds their packings. A split
   * product can request such a layout too. Only the community half has any, see [derivePluginPackings].
   */
  @JvmField val registryLayouts: Set<String> = emptySet(),
)

/**
 * The plugins the dev distribution states content for: the population of the dev sections.
 *
 * [derivePluginPopulation] uses the discovered products, the extra plugins of [DevDistHalf.extraPopulation] of [half],
 * and source-derived platform rows. [root] is the root of [half], whose run configurations the population reads.
 */
@ApiStatus.Internal
fun deriveContentPluginPopulation(
  half: DevDistHalf,
  root: Path,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  platformTable: DevDistPlatformJars,
): Set<String> {
  return derivePluginPopulation(
    products = products.mapNotNull { it.properties as? ProductProperties },
    extraPopulation = half.extraPopulation(DevDistRunConfigurations.read(half, root, outputProvider)),
    platformJars = platformTable,
    outputProvider = outputProvider,
  )
}

/**
 * Derives the packing of every plugin of the population; see [deriveContentPluginPopulation].
 *
 * [ProductDerivation.pluginJars] over the products of [derivation]. No input is a Bazel label, so the derivation runs
 * on every checkout. The dev sections and the plan add the labels. [root] is the root of [half], whose run
 * configurations the derivation reads.
 *
 * The community half plans every layout of its registry, whether a split product composes the layout or not. The
 * ultimate half then reuses the `content_module_jar` calls and the sections. So the community half also derives each
 * registry layout outside the population, see [PluginPackingDerivation.registryLayouts]. The registry is the plugin
 * layouts of the split products of the half. A product that is not a split product can bundle a registry layout, so
 * such a layout is in the population.
 */
internal fun derivePluginPackings(
  half: DevDistHalf,
  root: Path,
  outputProvider: ModuleOutputProvider,
  derivation: ProductDerivation,
): PluginPackingDerivation {
  val runConfigurations = DevDistRunConfigurations.read(half, root, outputProvider)
  val platformRows = derivation.platformJars
  val splitProducts = half.registrySplitProducts(derivation.products.map { it.name })
  val extraPopulation = half.extraPopulation(runConfigurations, splitProducts)
  val registryLayouts: Set<String>
  val outsidePopulation: Set<String>
  if (half.writesCommunityPackages) {
    val population = derivePluginPopulation(
      products = derivation.properties,
      extraPopulation = extraPopulation,
      platformJars = platformRows,
      outputProvider = outputProvider,
    )
    registryLayouts = derivation.products
      .filter { it.name in splitProducts }
      .flatMap { (it.properties as? ProductProperties)?.productLayout?.pluginLayouts?.value.orEmpty() }
      .mapTo(TreeSet()) { it.mainModule }
      .filterTo(TreeSet()) { outputProvider.findModule(it) != null }
    outsidePopulation = registryLayouts.filterTo(TreeSet()) { it !in population }
  }
  else {
    registryLayouts = emptySet()
    outsidePopulation = emptySet()
  }
  val derived = derivation.pluginJars(if (outsidePopulation.isEmpty()) extraPopulation else extraPopulation + outsidePopulation)
  return PluginPackingDerivation(
    plugins = derived.plugins,
    platformJars = platformRows,
    population = derived.population.filterTo(TreeSet()) { it !in outsidePopulation },
    runConfigurations = runConfigurations,
    registryLayouts = registryLayouts,
  )
}

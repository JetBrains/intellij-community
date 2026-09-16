// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import com.intellij.platform.distributionContent.DevDistPlatformJars
import com.intellij.platform.distributionContent.NonBundledPluginRow
import com.intellij.platform.distributionContent.PlatformContentModuleRow
import com.intellij.platform.distributionContent.PlatformJarRow
import com.intellij.platform.distributionContent.PlatformLibraryRow
import com.intellij.platform.distributionContent.PlatformMergedLibraryRow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.collectCompatiblePluginsToPublish
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.frontendIncompatibleRootModuleNames
import org.jetbrains.intellij.build.impl.getBundledPluginModules
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import java.util.TreeMap
import java.util.TreeSet

/**
 * The plugins the split dev distribution of IDEA Ultimate offers on demand without bundling them.
 *
 * The plan generator states the same list in its split-distribution config. The list is here too, so that a reader
 * without the tool's module derives the same population.
 */
val DEV_DIST_ON_DEMAND_PLUGIN_MODULES: Set<String> = setOf("intellij.devkit")

/** Derives the platform jars and published plugins once for each discovered product. */
@ApiStatus.Internal
fun deriveDevDistPlatformJars(
  products: List<DiscoveredProduct>,
  outputProvider: ModuleOutputProvider,
): DevDistPlatformJars {
  return deriveDevDistPlatformJars(
    products = products.mapNotNull { product -> (product.properties as? ProductProperties)?.let { product.name to it } }.toMap(),
    outputProvider = outputProvider,
  )
}

/** Derives the platform jars and published plugins from product declarations and source descriptors. */
@ApiStatus.Internal
fun deriveDevDistPlatformJars(
  products: Map<String, ProductProperties>,
  outputProvider: ModuleOutputProvider,
): DevDistPlatformJars {
  val platformJars = ArrayList<PlatformJarRow>()
  val platformLibraries = ArrayList<PlatformLibraryRow>()
  val platformMergedLibraries = ArrayList<PlatformMergedLibraryRow>()
  val platformContentModules = ArrayList<PlatformContentModuleRow>()
  val nonBundledPlugins = ArrayList<NonBundledPluginRow>()
  for ((product, properties) in products) {
    val layout = createPlatformLayout(productProperties = properties, outputProvider = outputProvider)
    val rows = derivePlatformJars(product = product, layout = layout, findModule = outputProvider::findRequiredModule)
    platformJars.addAll(rows.jars)
    platformLibraries.addAll(rows.libraries)
    platformMergedLibraries.addAll(rows.mergedLibraries)
    platformContentModules.addAll(rows.contentModules)

    val productLayout = properties.productLayout
    val pluginsToPublish = getPluginLayoutsByJpsModuleNames(
      modules = productLayout.pluginModulesToPublish,
      productLayout = productLayout,
      toPublish = true,
    )
    if (productLayout.buildAllCompatiblePlugins) {
      collectCompatiblePluginsToPublish(
        pluginsToPublish = pluginsToPublish,
        platformLayout = layout,
        productProperties = properties,
        outputProvider = outputProvider,
      )
    }
    pluginsToPublish.map { it.mainModule }.distinct().sorted().mapTo(nonBundledPlugins) {
      NonBundledPluginRow(product = product, mainModule = it)
    }
  }
  return DevDistPlatformJars(
    platformJars = platformJars,
    platformLibraries = platformLibraries,
    platformMergedLibraries = platformMergedLibraries,
    platformContentModules = platformContentModules,
    nonBundledPlugins = nonBundledPlugins,
  )
}

/** One plugin of the population, with the layout facts its derivation read and the derivation itself. */
@ApiStatus.Internal
class DerivedPlugin(
  @JvmField val mainModule: String,
  @JvmField val facts: PluginLayoutFacts,
  @JvmField val packing: DerivedPluginPacking,
)

/** The bundled plugins of one product, restricted to the population. */
@ApiStatus.Internal
class DerivedProductPlugins(
  @JvmField val properties: ProductProperties,
  @JvmField val bundledPluginModules: Set<String>,
)

/**
 * The derivation of every plugin of the population, in main module order.
 *
 * Every reader of a plugin's jars reads this one derivation, so the tool's tables, the packaging suite and the
 * project-structure tests state one packing per plugin.
 */
@ApiStatus.Internal
class DerivedPluginJars(
  @JvmField val plugins: List<DerivedPlugin>,
  /** The population [plugins] was derived from, sorted. See [derivePluginPopulation]. */
  @JvmField val population: Set<String>,
  /** One entry per product of the derivation, in the order the products were given. */
  @JvmField val products: List<DerivedProductPlugins>,
) {
  val pluginsByMainModule: Map<String, DerivedPlugin> by lazy { plugins.associateBy { it.mainModule } }

  /**
   * The bundled plugins of [properties] that the project holds a module for.
   *
   * [properties] must be one of the instances the derivation was given. The product is matched by identity, because
   * two products can share one class and still state two bundled sets.
   */
  fun bundledPluginModules(properties: ProductProperties): Set<String> {
    return products.firstOrNull { it.properties === properties }?.bundledPluginModules
           ?: error("The product `${properties.javaClass.name}` is not one of the products this derivation was given")
  }
}

/**
 * The plugins the dev distribution states content for: the population of the derivation.
 *
 * The population includes bundled plugins, published plugins, and [extraPopulation].
 * [platformJars] supplies compatible published plugins from source derivation. Names absent from the project are excluded.
 */
@ApiStatus.Internal
fun derivePluginPopulation(
  products: List<ProductProperties>,
  extraPopulation: Set<String>,
  platformJars: DevDistPlatformJars,
  outputProvider: ModuleOutputProvider,
): Set<String> {
  val population = TreeSet<String>()
  for (properties in products) {
    val productLayout = properties.productLayout
    population.addAll(getBundledPluginModules(properties, outputProvider))
    population.addAll(productLayout.pluginModulesToPublish)
  }
  population.addAll(extraPopulation)
  platformJars.nonBundledPlugins.mapTo(population) { it.mainModule }
  population.retainAll { outputProvider.findModule(it) != null }
  return population
}

/**
 * Derives the packing of every plugin of the population; see [derivePluginPopulation].
 *
 * The layout facts of a plugin are the union over every layout of its main module in [products]. What the platform or
 * another plugin's layout packs, an `auto` layout leaves out. [platformJars] supplies the source-derived platform members.
 *
 * Only a product with an embedded frontend root module evaluates the frontend module filter. Without such a product
 * the filter is empty, and then no member is frontend-compatible.
 */
@ApiStatus.Internal
fun derivePluginJars(
  products: List<ProductProperties>,
  extraPopulation: Set<String>,
  platformJars: DevDistPlatformJars,
  outputProvider: ModuleOutputProvider,
): DerivedPluginJars {
  val layoutsByMainModule = TreeMap<String, MutableList<PluginLayout>>()
  var frontendProduct = false
  for (properties in products) {
    if (properties.embeddedFrontendRootModule != null) {
      frontendProduct = true
    }
    for (layout in properties.productLayout.pluginLayouts.value) {
      layoutsByMainModule.computeIfAbsent(layout.mainModule) { ArrayList() }.add(layout)
    }
  }
  val population = derivePluginPopulation(
    products = products,
    extraPopulation = extraPopulation,
    platformJars = platformJars,
    outputProvider = outputProvider,
  )

  val frontendRoots = if (frontendProduct) frontendIncompatibleRootModuleNames() else emptyList()
  val platformMembers = platformJars.platformJars.flatMapTo(HashSet()) { it.members }
  val layoutMemberOwners = HashMap<String, MutableSet<String>>()
  for ((owner, layouts) in layoutsByMainModule) {
    for (layout in layouts) {
      for (item in layout.includedModules) {
        layoutMemberOwners.computeIfAbsent(item.moduleName) { HashSet() }.add(owner)
      }
    }
  }

  val plugins = ArrayList<DerivedPlugin>()
  for (mainModule in population) {
    val facts = pluginLayoutFacts(mainModule = mainModule, layouts = layoutsByMainModule.get(mainModule).orEmpty())
    val packing = derivePluginPacking(
      mainModule = mainModule,
      facts = facts,
      project = outputProvider.findRequiredModule(mainModule).project,
      outputProvider = outputProvider,
      frontendRoots = frontendRoots,
      isPrepackedContentModule = { true },
      isPackedElsewhere = { name -> name in platformMembers || layoutMemberOwners.get(name)?.any { it != mainModule } == true },
    ) ?: continue
    plugins.add(DerivedPlugin(mainModule = mainModule, facts = facts, packing = packing))
  }
  val productPlugins = products.map { properties ->
    DerivedProductPlugins(
      properties = properties,
      bundledPluginModules = getBundledPluginModules(properties, outputProvider).filterTo(LinkedHashSet()) { it in population },
    )
  }
  return DerivedPluginJars(plugins = plugins, population = population, products = productPlugins)
}

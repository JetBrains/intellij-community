// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.pluginModelTool

import com.intellij.platform.distributionContent.DevDistPlatformJars
import com.intellij.platform.distributionContent.NonBundledPluginRow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.PluginDescriptorFileCache
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.collectCompatiblePluginsToPublish
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.frontendIncompatibleRootModuleNames
import org.jetbrains.intellij.build.impl.getBundledPluginModules
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.model.JpsProject
import java.time.Instant
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

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

/**
 * The products a derivation walks at once. Each walk reads every plugin descriptor of the project on its own
 * workers, so a small bound here keeps the fan-out near the core count.
 */
private const val PRODUCT_DERIVATION_CONCURRENCY = 6

/**
 * Derives the platform jars and published plugins from product declarations and source descriptors.
 *
 * The products are derived beside each other, and the rows keep the order of [products].
 */
@ApiStatus.Internal
fun deriveDevDistPlatformJars(
  products: Map<String, ProductProperties>,
  outputProvider: ModuleOutputProvider,
): DevDistPlatformJars {
  // Every product walks the whole project for a plugin descriptor, and the answer of that walk is the same for each
  // of them. One cache for the derivation turns 22 walks over the file system into one.
  val descriptorFiles = PluginDescriptorFileCache(outputProvider)
  val rowsByProduct = products.entries.toList().mapConcurrent(concurrency = PRODUCT_DERIVATION_CONCURRENCY) { (product, properties) ->
    spanBuilder("derive platform jars: $product").use {
      deriveProductPlatformJars(
        product = product,
        properties = properties,
        outputProvider = outputProvider,
        descriptorFiles = descriptorFiles,
      )
    }
  }
  return DevDistPlatformJars(
    platformJars = rowsByProduct.flatMap { it.rows.jars },
    platformLibraries = rowsByProduct.flatMap { it.rows.libraries },
    platformMergedLibraries = rowsByProduct.flatMap { it.rows.mergedLibraries },
    platformContentModules = rowsByProduct.flatMap { it.rows.contentModules },
    nonBundledPlugins = rowsByProduct.flatMap { it.nonBundledPlugins },
  )
}

/** The platform rows and the published plugins of one product. */
private class ProductPlatformJars(
  @JvmField val rows: PlatformJarRows,
  @JvmField val nonBundledPlugins: List<NonBundledPluginRow>,
)

private fun deriveProductPlatformJars(
  product: String,
  properties: ProductProperties,
  outputProvider: ModuleOutputProvider,
  descriptorFiles: PluginDescriptorFileCache,
): ProductPlatformJars {
  // the bundled plugin list is read once, and the layout and the compatible-plugin walk both take it
  val bundledPluginModules = getBundledPluginModules(properties, outputProvider)
  val layout = createPlatformLayout(productProperties = properties, outputProvider = outputProvider, bundledPluginModules = bundledPluginModules)
  val rows = derivePlatformJars(product = product, layout = layout, findModule = outputProvider::findRequiredModule)

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
      bundledPluginModules = bundledPluginModules,
      descriptorFiles = descriptorFiles,
    )
  }
  val nonBundledPlugins = pluginsToPublish.map { it.mainModule }.distinct().sorted().map {
    NonBundledPluginRow(product = product, mainModule = it)
  }
  return ProductPlatformJars(rows = rows, nonBundledPlugins = nonBundledPlugins)
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
 * Puts the phase times of [stats] on [span] as attributes, and adds one child span per phase.
 *
 * A child span starts at [start] and lasts the phase time summed over the workers. So a child span can last longer than [span].
 */
private fun reportPluginPackingStats(span: Span, start: Instant, stats: PluginPackingStats, autoPlugins: Long, closurePlugins: Long) {
  val phases = listOf(
    "closure" to stats.closureNanos,
    "residue" to stats.residueNanos,
    "auto layout" to stats.autoLayoutNanos,
    "candidacy" to stats.candidacyNanos,
    "content" to stats.contentNanos,
    "layout variants" to stats.variantNanos,
    "compose" to stats.composeNanos,
  )
  for ((phase, nanos) in phases) {
    val total = nanos.sum()
    span.setAttribute("${phase.replace(' ', '_')}Ms", total / 1_000_000)
    spanBuilder("plugin jars: $phase")
      .setStartTimestamp(start)
      .setAttribute("sumOverWorkers", true)
      .startSpan()
      .end(start.plusNanos(total))
  }
  span.setAttribute("variantPackings", stats.variantCount.sum())
  span.setAttribute("packedVariantPackings", stats.packedVariantCount.sum())
  span.setAttribute("autoPlugins", autoPlugins)
  span.setAttribute("closurePlugins", closurePlugins)
  span.setAttribute("pluginSumMs", stats.pluginNanos.values.sum() / 1_000_000)
  val slowest = stats.pluginNanos.entries
    .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    .take(5)
    .joinToString(separator = ", ") { "${it.key}=${it.value / 1_000_000}ms" }
  span.setAttribute("slowestPlugins", slowest)
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

  // Each plugin is derived on its own, and the list keeps the population order. Every input the workers read is
  // complete before the first one starts.
  // One frontend filter per project serves every plugin, because its answers depend on the project alone.
  val frontends = ConcurrentHashMap<JpsProject, FrontendCompatibility>()
  val cache = PluginPackingCache()
  val plugins = spanBuilder("derive plugin jars").setAttribute("populationSize", population.size.toLong()).use { span ->
    // Only a recorded span gets the phase times.
    val stats = if (span.isRecording) PluginPackingStats() else null
    val autoPlugins = LongAdder()
    val closurePlugins = LongAdder()
    val start = Instant.now()
    val result = population.toList().mapConcurrent { mainModule ->
      val pluginStart = System.nanoTime()
      val facts = pluginLayoutFacts(mainModule = mainModule, layouts = layoutsByMainModule.get(mainModule).orEmpty())
      val project = outputProvider.findRequiredModule(mainModule).project
      val packing = derivePluginPacking(
        mainModule = mainModule,
        facts = facts,
        project = project,
        outputProvider = outputProvider,
        frontendRoots = frontendRoots,
        isPackedElsewhere = { name -> name in platformMembers || layoutMemberOwners.get(name)?.any { it != mainModule } == true },
        frontend = frontends.computeIfAbsent(project) { FrontendCompatibility(roots = frontendRoots.toSet(), findModule = it::findModuleByName) },
        stats = stats,
        cache = cache,
      )
      if (stats != null) {
        stats.pluginNanos.put(mainModule, System.nanoTime() - pluginStart)
        if (facts.auto) {
          autoPlugins.increment()
        }
        if (packing != null && packing.content.closureMembers.isNotEmpty()) {
          closurePlugins.increment()
        }
      }
      packing?.let { DerivedPlugin(mainModule = mainModule, facts = facts, packing = it) }
    }.filterNotNull()
    if (stats != null) {
      reportPluginPackingStats(span = span, start = start, stats = stats, autoPlugins = autoPlugins.sum(), closurePlugins = closurePlugins.sum())
    }
    result
  }
  val productPlugins = products.map { properties ->
    DerivedProductPlugins(
      properties = properties,
      bundledPluginModules = getBundledPluginModules(properties, outputProvider).filterTo(LinkedHashSet()) { it in population },
    )
  }
  return DerivedPluginJars(plugins = plugins, population = population, products = productPlugins)
}

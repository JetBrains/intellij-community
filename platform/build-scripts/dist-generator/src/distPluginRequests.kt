@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.distGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dev.devModePluginCandidates
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.getBundledPluginModules
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct

/**
 * The tier of a plugin request. A distribution composes every bundled component and the additional components its
 * `additional_modules` name. [key] is the Starlark key of the tier in `DEV_DIST_PLUGIN_COMPONENTS`. Only the tiers of
 * [DIST_COMPONENT_TIERS] get a component.
 */
@ApiStatus.Internal
enum class DistPluginTier(@JvmField val key: String) {
  BUNDLED("bundled"),
  ADDITIONAL("additional"),

  /**
   * A plugin of the population or a layout of the registry of the half that no split product bundles or names. The
   * half plans it, so the other half can reuse its section. It gets no component.
   */
  REGISTRY("registry"),
}

/** The tiers that get a component in `DEV_DIST_PLUGIN_COMPONENTS`, in the order of the Starlark keys. */
@ApiStatus.Internal
val DIST_COMPONENT_TIERS: List<DistPluginTier> = listOf(DistPluginTier.BUNDLED, DistPluginTier.ADDITIONAL)

/** One original layout selected for later registration with the owner from the same run. */
@ApiStatus.Internal
data class DistPluginRequest(
  @JvmField val product: String,
  @JvmField val properties: ProductProperties,
  @JvmField val tier: DistPluginTier,
  @JvmField val variant: PluginSymbolicVariant,
  @JvmField val layout: PluginLayout,
  @JvmField val testPlugin: TestPluginSpec? = null,
  /**
   * Whether only the update set of the product names the plugin, see [DistHalf.updatePluginModules]. A refusal of such
   * a request does not stop the run at once. The run collects it, see [DistUpdateRefusals].
   */
  @JvmField val updateOnly: Boolean = false,
) {
  val key: DistPluginPlanKey
    get() = DistPluginPlanKey(product, layout.mainModule, variant.id)
}

/**
 * Enumerates the requests of one split product. This phase neither registers plans nor reads plugin payloads.
 * [extraPluginModules] are the configured extra modules of the product, see [DistHalf.extraPluginModules].
 * [additionalModules] are the modules the run configurations of the product name, see [distRunConfigurationModules].
 * [testPlugins] are the Product DSL test plugins that such a module can be. [registryModules] are the registry layouts
 * that the product plans in the registry tier. [updateModules] are the update plugins of the product, and
 * [refuseUpdate] takes the refusal of each one that only the update set names.
 */
internal fun enumerateDistPluginRequests(
  product: DiscoveredProduct,
  outputProvider: ModuleOutputProvider,
  variants: List<PluginSymbolicVariant>,
  extraPluginModules: List<String>,
  testPlugins: List<TestPluginSpec>,
  additionalModules: List<String> = emptyList(),
  bundledPluginDirectoriesToSkip: Collection<String> = emptyList(),
  registryModules: Collection<String> = emptyList(),
  updateModules: Collection<String> = emptyList(),
  refuseUpdate: (mainModule: String, reason: String) -> Unit = ::throwUpdateRefusal,
): List<DistPluginRequest> {
  val properties = requireNotNull(product.properties as? ProductProperties) { "Split product '${product.name}' has no ProductProperties" }
  val configuredAdditionalModules = LinkedHashSet(extraPluginModules)
  configuredAdditionalModules.addAll(additionalModules)
  configuredAdditionalModules.addAll(updateModules)
  return enumerateDistPluginRequests(
    product = product.name,
    properties = properties,
    bundledPluginModules = getBundledPluginModules(properties, outputProvider),
    variants = variants,
    additionalModules = additionalModules,
    extraPluginModules = extraPluginModules,
    bundledPluginDirectoriesToSkip = bundledPluginDirectoriesToSkip,
    testPluginsByMainModule = resolveDistTestPlugins(configuredAdditionalModules, testPlugins, outputProvider),
    registryModules = registryModules,
    updateModules = updateModules,
    refuseUpdate = refuseUpdate,
  )
}

/** The default refusal of an update plugin: it stops the run, as the refusal of every other plugin does. */
private fun throwUpdateRefusal(@Suppress("UNUSED_PARAMETER") mainModule: String, reason: String): Nothing = throw IllegalStateException(reason)

/**
 * Uses the supplied original product and an explicit configuration snapshot, also for synthetic model tests.
 * [extraPluginModules] are the configured extra modules of the product, see [DistHalf.extraPluginModules]. An additional
 * module of [extraPluginModules] or [additionalModules] must have a plugin layout, and a variant must select it. A
 * module that breaks this rule stops the run, like a bundled plugin the plan cannot state.
 *
 * [registryModules] are the plugins that the product plans in the registry tier. Such a module must be neither bundled
 * nor additional. A module without an explicit layout gets an automatic layout, as a bundled module does. A variant
 * selects it where its restrictions admit it. A registry module that no variant selects gets no request and does not
 * stop the run.
 *
 * [updateModules] are the update plugins of the product, see [DistHalf.updatePluginModules]. Such a module is in the
 * `additional` tier, and it must be neither bundled nor a registry module. It takes the layouts that the product
 * publishes, so a marketplace layout is selectable, see [devModePluginCandidates]. A run configuration or the hand list
 * can name it too, and then its refusal stops the run. [refuseUpdate] takes the refusal of each module that only the
 * update set names, and the module gets no request.
 */
@ApiStatus.Internal
fun enumerateDistPluginRequests(
  product: String,
  properties: ProductProperties,
  bundledPluginModules: List<String>,
  variants: List<PluginSymbolicVariant>,
  additionalModules: List<String>,
  extraPluginModules: List<String>,
  bundledPluginDirectoriesToSkip: Collection<String> = emptyList(),
  testPluginsByMainModule: Map<String, TestPluginSpec> = emptyMap(),
  registryModules: Collection<String> = emptyList(),
  updateModules: Collection<String> = emptyList(),
  refuseUpdate: (mainModule: String, reason: String) -> Unit = ::throwUpdateRefusal,
): List<DistPluginRequest> {
  require(product.isNotBlank()) { "A plugin request requires a product identity" }
  require(variants.isNotEmpty()) { "Split product '$product' requires at least one platform variant" }
  val variantIds = HashSet<String>()
  val distributions = HashSet<SupportedDistribution>()
  for (variant in variants) {
    require(variant.id.isNotBlank() && variantIds.add(variant.id)) { "Duplicate or empty plugin variant '${variant.id}'" }
    require(variant.distribution in SUPPORTED_DISTRIBUTIONS) { "Unsupported plugin variant '${variant.id}': ${variant.distribution}" }
    require(distributions.add(checkNotNull(variant.distribution))) { "Multiple plugin variants select ${variant.distribution}" }
  }

  val bundledNames = LinkedHashSet(bundledPluginModules)
  val skippedDirectories = java.util.List.copyOf(bundledPluginDirectoriesToSkip)
  val update = LinkedHashSet(updateModules)
  val additional = LinkedHashSet(extraPluginModules)
  additional.addAll(additionalModules)
  // A configured module of the update set takes the published layouts, so the bundled selection leaves it out.
  val configured = additional.filterNotTo(LinkedHashSet(), update::contains)
  val updateOnly = update.filterNotTo(LinkedHashSet(), additional::contains)
  val additionalOnly = additional.filterNotTo(LinkedHashSet(), bundledNames::contains)
  additionalOnly.addAll(update)
  val registry = LinkedHashSet(registryModules)
  for (mainModule in registry) {
    val tier = if (mainModule in bundledNames) DistPluginTier.BUNDLED else if (mainModule in additionalOnly) DistPluginTier.ADDITIONAL else continue
    throw IllegalArgumentException("Plugin '$mainModule' of '$product' is in the tiers [${tier.key}, ${DistPluginTier.REGISTRY.key}]")
  }
  for (mainModule in update) {
    require(mainModule !in bundledNames) {
      "Plugin '$mainModule' of '$product' is in the tiers [${DistPluginTier.BUNDLED.key}, ${DistPluginTier.ADDITIONAL.key}]"
    }
  }
  // A registry module joins the names, so its layouts are found and a variant is selected. It is not demanded, so a
  // variant that its restrictions keep out is no failure.
  val allNames = LinkedHashSet(bundledNames)
  allNames.addAll(configured)
  allNames.addAll(registry)
  val originalLayouts = getPluginLayoutsByJpsModuleNames(allNames, properties.productLayout).toList()
  val layoutNames = originalLayouts.mapTo(HashSet()) { it.mainModule }
  val withoutLayout = additionalOnly.filter { it !in layoutNames && it !in update }
  check(withoutLayout.isEmpty()) { "Additional plugin modules of '$product' have no plugin layout: $withoutLayout" }

  // The layouts that the product publishes, as the old update path takes them: the marketplace layout of a pair.
  val updateLayouts = getPluginLayoutsByJpsModuleNames(update, properties.productLayout, toPublish = true).groupBy { it.mainModule }
  val refused = LinkedHashMap<String, String>()
  fun refuse(mainModule: String, reason: String) {
    check(mainModule in updateOnly) { reason }
    refused.putIfAbsent(mainModule, reason)
  }
  for (mainModule in update) {
    if (mainModule !in updateLayouts) {
      refuse(mainModule, "The update plugin '$mainModule' of '$product' has no plugin layout")
    }
  }

  // The requests keep the order of the names: a configured module of the update set keeps its place, and a module that
  // only the update set names comes last.
  val nameOrder = LinkedHashSet(bundledNames)
  nameOrder.addAll(additional)
  nameOrder.addAll(registry)
  nameOrder.addAll(update)
  val rank = nameOrder.withIndex().associate { (index, mainModule) -> mainModule to index }
  val result = ArrayList<DistPluginRequest>()
  val requested = HashSet<String>()
  for (variant in variants) {
    val distribution = checkNotNull(variant.distribution)
    val selected = ArrayList(devModePluginCandidates(
      owned = originalLayouts,
      bundledMainModuleNames = allNames,
      demanded = configured,
      fragmentName = "all",
      platformPrefix = product,
      os = distribution.os,
      arch = distribution.arch,
      bundledPluginDirectoriesToSkip = skippedDirectories,
    ))
    // Each update plugin is selected on its own, so the failure of one names it and leaves the others.
    for ((mainModule, layouts) in updateLayouts) {
      if (mainModule in refused) {
        continue
      }
      try {
        selected.addAll(devModePluginCandidates(
          owned = layouts,
          bundledMainModuleNames = update,
          demanded = setOf(mainModule),
          fragmentName = "all",
          platformPrefix = product,
          os = distribution.os,
          arch = distribution.arch,
          bundledPluginDirectoriesToSkip = skippedDirectories,
          updateModules = update,
        ))
      }
      catch (e: IllegalStateException) {
        refuse(mainModule, e.message ?: e.toString())
      }
      catch (e: IllegalArgumentException) {
        refuse(mainModule, e.message ?: e.toString())
      }
    }
    if (update.isNotEmpty()) {
      selected.sortBy { rank.getValue(it.mainModule) }
    }

    val tiers = LinkedHashMap<String, DistPluginTier>()
    for (layout in selected) {
      val tier = distPluginTier(layout.mainModule, bundledNames, additionalOnly, registry, product)
      check(tiers.put(layout.mainModule, tier) == null) { "Plugin '${layout.mainModule}' is selected twice for '${variant.id}'" }
    }
    val requests = selected.map { layout ->
      DistPluginRequest(
        product = product,
        properties = properties,
        tier = tiers.getValue(layout.mainModule),
        variant = variant,
        layout = layout,
        testPlugin = testPluginsByMainModule.get(layout.mainModule),
        updateOnly = layout.mainModule in updateOnly,
      )
    }
    checkDistPluginRequestCompleteness(requests, product, properties, variant, selected, tiers)
    result.addAll(requests)
    requested.addAll(tiers.keys)
  }
  val withoutVariant = additionalOnly.filter { it !in requested && it !in update }
  check(withoutVariant.isEmpty()) { "No platform variant selects the additional plugin modules of '$product': $withoutVariant" }
  for (mainModule in update) {
    if (mainModule !in requested && mainModule !in refused) {
      refuse(mainModule, "No platform variant selects the update plugin '$mainModule' of '$product'")
    }
  }
  for ((mainModule, reason) in refused) {
    refuseUpdate(mainModule, reason)
  }
  return java.util.List.copyOf(if (refused.isEmpty()) result else result.filter { it.layout.mainModule !in refused })
}

/** The one tier of a selected plugin. A plugin in two of the populations, or in none, has no tier. */
private fun distPluginTier(
  mainModule: String,
  bundledNames: Set<String>,
  additionalOnly: Set<String>,
  registry: Set<String>,
  product: String,
): DistPluginTier {
  val tiers = ArrayList<DistPluginTier>(1)
  if (mainModule in bundledNames) tiers.add(DistPluginTier.BUNDLED)
  if (mainModule in additionalOnly) tiers.add(DistPluginTier.ADDITIONAL)
  if (mainModule in registry) tiers.add(DistPluginTier.REGISTRY)
  check(tiers.size == 1) { "Plugin '$mainModule' of '$product' is in the tiers ${tiers.map { it.key }}" }
  return tiers.single()
}

/**
 * Resolves configured test-plugin owner modules to the Product DSL specifications that generate their descriptors.
 * [specs] are the test plugins of the test product specifications of the run.
 */
internal fun resolveDistTestPlugins(
  mainModules: Collection<String>,
  specs: List<TestPluginSpec>,
  outputProvider: ModuleOutputProvider,
): Map<String, TestPluginSpec> {
  val result = LinkedHashMap<String, TestPluginSpec>()
  for (mainModule in mainModules) {
    val module = outputProvider.findModule(mainModule) ?: continue
    val descriptor = outputProvider.findFileInModuleSources(module, PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = false) ?: continue
    val matches = specs.filter { descriptor.endsWith(it.pluginXmlPath) }
    check(matches.size <= 1) {
      "Test plugin '$mainModule' matches multiple Product DSL descriptors: ${matches.map { it.pluginXmlPath }}"
    }
    matches.singleOrNull()?.let { result.put(mainModule, it) }
  }
  return result
}

/** Checks exact membership, original layout identity, order, and tier for one target platform. */
internal fun checkDistPluginRequestCompleteness(
  requests: List<DistPluginRequest>,
  product: String,
  properties: ProductProperties,
  variant: PluginSymbolicVariant,
  selected: List<PluginLayout>,
  tiers: Map<String, DistPluginTier>,
) {
  check(requests.size == selected.size && tiers.keys == selected.mapTo(LinkedHashSet()) { it.mainModule } &&
        requests.map { it.key }.toSet().size == requests.size &&
        requests.zip(selected).all { (request, layout) ->
          request.product == product && request.properties === properties && request.variant == variant &&
          request.layout === layout && request.tier == tiers.get(layout.mainModule)
        }) {
    "Incomplete plugin requests for '$product' variant '${variant.id}': expected ${selected.map { it.mainModule }}, " +
    "got ${requests.map { it.layout.mainModule to it.tier }}"
  }
}

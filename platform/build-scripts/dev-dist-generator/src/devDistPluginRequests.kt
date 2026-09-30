@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

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
 * [DEV_DIST_COMPONENT_TIERS] get a component.
 */
@ApiStatus.Internal
enum class DevDistPluginTier(@JvmField val key: String) {
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
val DEV_DIST_COMPONENT_TIERS: List<DevDistPluginTier> = listOf(DevDistPluginTier.BUNDLED, DevDistPluginTier.ADDITIONAL)

/** One original layout selected for later registration with the owner from the same run. */
@ApiStatus.Internal
data class DevDistPluginRequest(
  @JvmField val product: String,
  @JvmField val properties: ProductProperties,
  @JvmField val tier: DevDistPluginTier,
  @JvmField val variant: PluginSymbolicVariant,
  @JvmField val layout: PluginLayout,
  @JvmField val testPlugin: TestPluginSpec? = null,
) {
  val key: DevDistPluginPlanKey
    get() = DevDistPluginPlanKey(product, layout.mainModule, variant.id)
}

/**
 * Enumerates the requests of one split product. This phase neither registers plans nor reads plugin payloads.
 * [extraPluginModules] are the configured extra modules of the product, see [DevDistHalf.extraPluginModules].
 * [additionalModules] are the modules the run configurations of the product name, see [devDistRunConfigurationModules].
 * [testPlugins] are the Product DSL test plugins that such a module can be. [registryModules] are the registry layouts
 * that the product plans in the registry tier.
 */
internal fun enumerateDevDistPluginRequests(
  product: DiscoveredProduct,
  outputProvider: ModuleOutputProvider,
  variants: List<PluginSymbolicVariant>,
  extraPluginModules: List<String>,
  testPlugins: List<TestPluginSpec>,
  additionalModules: List<String> = emptyList(),
  bundledPluginDirectoriesToSkip: Collection<String> = emptyList(),
  registryModules: Collection<String> = emptyList(),
): List<DevDistPluginRequest> {
  val properties = requireNotNull(product.properties as? ProductProperties) { "Split product '${product.name}' has no ProductProperties" }
  val configuredAdditionalModules = LinkedHashSet(extraPluginModules)
  configuredAdditionalModules.addAll(additionalModules)
  return enumerateDevDistPluginRequests(
    product = product.name,
    properties = properties,
    bundledPluginModules = getBundledPluginModules(properties, outputProvider),
    variants = variants,
    additionalModules = additionalModules,
    extraPluginModules = extraPluginModules,
    bundledPluginDirectoriesToSkip = bundledPluginDirectoriesToSkip,
    testPluginsByMainModule = resolveDevDistTestPlugins(configuredAdditionalModules, testPlugins, outputProvider),
    registryModules = registryModules,
  )
}

/**
 * Uses the supplied original product and an explicit configuration snapshot, also for synthetic model tests.
 * [extraPluginModules] are the configured extra modules of the product, see [DevDistHalf.extraPluginModules]. An additional
 * module of [extraPluginModules] or [additionalModules] must have a plugin layout, and a variant must select it. A
 * module that breaks this rule stops the run, like a bundled plugin the plan cannot state.
 *
 * [registryModules] are the plugins that the product plans in the registry tier. Such a module must be neither bundled
 * nor additional. A module without an explicit layout gets an automatic layout, as a bundled module does. A variant
 * selects it where its restrictions admit it. A registry module that no variant selects gets no request and does not
 * stop the run.
 */
@ApiStatus.Internal
fun enumerateDevDistPluginRequests(
  product: String,
  properties: ProductProperties,
  bundledPluginModules: List<String>,
  variants: List<PluginSymbolicVariant>,
  additionalModules: List<String>,
  extraPluginModules: List<String>,
  bundledPluginDirectoriesToSkip: Collection<String> = emptyList(),
  testPluginsByMainModule: Map<String, TestPluginSpec> = emptyMap(),
  registryModules: Collection<String> = emptyList(),
): List<DevDistPluginRequest> {
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
  val additional = LinkedHashSet(extraPluginModules)
  additional.addAll(additionalModules)
  val additionalOnly = additional.filterNotTo(LinkedHashSet(), bundledNames::contains)
  val registry = LinkedHashSet(registryModules)
  for (mainModule in registry) {
    val tier = if (mainModule in bundledNames) DevDistPluginTier.BUNDLED else if (mainModule in additional) DevDistPluginTier.ADDITIONAL else continue
    throw IllegalArgumentException("Plugin '$mainModule' of '$product' is in the tiers [${tier.key}, ${DevDistPluginTier.REGISTRY.key}]")
  }
  // A registry module joins the names, so its layouts are found and a variant is selected. It is not demanded, so a
  // variant that its restrictions keep out is no failure.
  val allNames = LinkedHashSet(bundledNames)
  allNames.addAll(additional)
  allNames.addAll(registry)
  val originalLayouts = getPluginLayoutsByJpsModuleNames(allNames, properties.productLayout).toList()
  val layoutNames = originalLayouts.mapTo(HashSet()) { it.mainModule }
  val withoutLayout = additionalOnly.filter { it !in layoutNames }
  check(withoutLayout.isEmpty()) { "Additional plugin modules of '$product' have no plugin layout: $withoutLayout" }

  val result = ArrayList<DevDistPluginRequest>()
  val requested = HashSet<String>()
  for (variant in variants) {
    val distribution = checkNotNull(variant.distribution)
    val selected = devModePluginCandidates(
      owned = originalLayouts,
      bundledMainModuleNames = allNames,
      demanded = additional,
      fragmentName = "all",
      platformPrefix = product,
      os = distribution.os,
      arch = distribution.arch,
      bundledPluginDirectoriesToSkip = skippedDirectories,
    )

    val tiers = LinkedHashMap<String, DevDistPluginTier>()
    for (layout in selected) {
      val tier = devDistPluginTier(layout.mainModule, bundledNames, additionalOnly, registry, product)
      check(tiers.put(layout.mainModule, tier) == null) { "Plugin '${layout.mainModule}' is selected twice for '${variant.id}'" }
    }
    val requests = selected.map { layout ->
      DevDistPluginRequest(
        product = product,
        properties = properties,
        tier = tiers.getValue(layout.mainModule),
        variant = variant,
        layout = layout,
        testPlugin = testPluginsByMainModule.get(layout.mainModule),
      )
    }
    checkDevDistPluginRequestCompleteness(requests, product, properties, variant, selected, tiers)
    result.addAll(requests)
    requested.addAll(tiers.keys)
  }
  val withoutVariant = additionalOnly.filter { it !in requested }
  check(withoutVariant.isEmpty()) { "No platform variant selects the additional plugin modules of '$product': $withoutVariant" }
  return java.util.List.copyOf(result)
}

/** The one tier of a selected plugin. A plugin in two of the populations, or in none, has no tier. */
private fun devDistPluginTier(
  mainModule: String,
  bundledNames: Set<String>,
  additionalOnly: Set<String>,
  registry: Set<String>,
  product: String,
): DevDistPluginTier {
  val tiers = ArrayList<DevDistPluginTier>(1)
  if (mainModule in bundledNames) tiers.add(DevDistPluginTier.BUNDLED)
  if (mainModule in additionalOnly) tiers.add(DevDistPluginTier.ADDITIONAL)
  if (mainModule in registry) tiers.add(DevDistPluginTier.REGISTRY)
  check(tiers.size == 1) { "Plugin '$mainModule' of '$product' is in the tiers ${tiers.map { it.key }}" }
  return tiers.single()
}

/**
 * Resolves configured test-plugin owner modules to the Product DSL specifications that generate their descriptors.
 * [specs] are the test plugins of the test product specifications of the run.
 */
internal fun resolveDevDistTestPlugins(
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
internal fun checkDevDistPluginRequestCompleteness(
  requests: List<DevDistPluginRequest>,
  product: String,
  properties: ProductProperties,
  variant: PluginSymbolicVariant,
  selected: List<PluginLayout>,
  tiers: Map<String, DevDistPluginTier>,
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

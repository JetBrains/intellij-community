// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.devBuildPathIdentity
import org.jetbrains.intellij.build.impl.DevPlatformEntryPatch
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import java.nio.file.Path

/**
 * The facts of one repository half that the dev-distribution generator reads.
 *
 * The generator code is the same for both halves. A half states its root, its tables, the sources that its binder can
 * bind, and what it can plan beyond the common facts. The generator refuses a fact that the half cannot plan, and the
 * message names the half. A half holds data and small functions only. It adds no step to the plan algorithm.
 */
@ApiStatus.Internal
interface DevDistHalf {
  /** The name of the half in a census line and in an error message. */
  val name: String

  /** The directory of the half, relative to the monorepo root. The empty string is the monorepo root itself. */
  val rootDirectory: String

  /** The `.bzl` file that exports `intellij_dev_run_configurations` to the generated rows file of the half. */
  val macrosBzl: String

  /** The JPS bridge extension of the half, which resolves a module name of a generated file to a label. */
  val jpsBridge: String

  /**
   * The split products of the half, keyed by the `build/dev-build.json` key.
   *
   * The map order is the baseline order of the divergence rule, see [DescriptorResidueClasses]. A plugin that two
   * products state differently keeps its home with the first product of this order that plans it.
   */
  val splitDistributions: Map<String, SplitDevDistribution>

  /** What the half can plan beyond the common facts. The community half has no capability. */
  val capabilities: Set<DevDistCapability>

  /** The binder of the closed layout asset sources, see [DevDistAssetBinder]. */
  val assetBinder: DevDistAssetBinder

  /** The embedded frontend of the half, or `null` when the half cannot plan a frontend product. */
  val embeddedFrontend: DevDistEmbeddedFrontendSupport?

  /** The platform patches of the half, or `null` when the half cannot plan a platform patch. */
  val platformPatches: DevDistPlatformPatchSupport?

  /**
   * The directories of the generated module-set descriptors, relative to the root of the half, each with the module that
   * owns it. The descriptor walk lists every file of a directory whose module the project model holds.
   */
  val generatedModuleSetDescriptors: Map<String, String>

  /** The `-D` properties that a row states as a boolean field, keyed by property and valued by the field name. */
  val rowFieldProperties: Map<String, String>

  /** The `-D` properties that a row cannot set, keyed by property and valued by the text that says what to do instead. */
  val refusedRowProperties: Map<String, String>

  /**
   * The label of the base `idea.properties` of [product]. [languageServerBase] is the fact of the launch model that
   * selects the second base. A half without a second base fails for it.
   */
  fun baseIdeaProperties(product: String, languageServerBase: Boolean): String

  /**
   * Whether the half writes the generated files of the package in [directory], a path relative to the root of the half:
   * a `dev` section, a `content_module_jar` call, a plan file or a generated package.
   */
  fun ownsPackage(directory: String): Boolean
}

/**
 * The root of the half below the monorepo root [monorepoRoot], see [DevDistHalf.rootDirectory].
 *
 * The half reads `build/dev-build.json`, `.idea/runConfigurations` and its JPS model there. Every project-relative path
 * of a run of the half is relative to it, and the half writes every output there.
 */
@ApiStatus.Internal
fun DevDistHalf.root(monorepoRoot: Path): Path = if (rootDirectory.isEmpty()) monorepoRoot else monorepoRoot.resolve(rootDirectory)

/**
 * Whether the packages of the half are community packages, so that a generated file spells a label for a community
 * dependent. A half below the monorepo root is the community half.
 */
internal val DevDistHalf.writesCommunityPackages: Boolean
  get() = rootDirectory.isNotEmpty()

/** The community checkout, for the half whose root is [root]: [root] itself for the community half. */
internal fun DevDistHalf.communityRoot(root: Path): Path = if (writesCommunityPackages) root else root.resolve(COMMUNITY_ROOT_DIRECTORY)

/**
 * The split products of the half that a run renders: every split product for the ultimate half, and the split products
 * of [registryProducts] for the community half. [registryProducts] are the keys of `build/dev-build.json` of the half.
 */
internal fun DevDistHalf.registrySplitProducts(registryProducts: Collection<String>): Set<String> {
  if (!writesCommunityPackages) {
    return splitProducts
  }
  val registry = registryProducts.toHashSet()
  return splitProducts.filterTo(LinkedHashSet()) { it in registry }
}

/**
 * Fails when the half writes [relativePath], a path relative to the root of the half, into a package that it does not
 * own, see [DevDistHalf.ownsPackage]. The message names the path.
 */
internal fun DevDistHalf.requireWritable(relativePath: String) {
  val directory = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
  check(ownsPackage(directory)) {
    "The $name half cannot write '$relativePath', because the package '$directory' belongs to the other half"
  }
}

/** What a half can plan beyond the common facts. */
@ApiStatus.Internal
enum class DevDistCapability {
  /** The reference plan of the gates, `build/dev_dist_reference_plan.bzl`. */
  REFERENCE_PLAN,

  /** A platform patch of a layout patcher, see [DevDistPlatformPatchSupport]. */
  PLATFORM_PATCHES,

  /** An embedded frontend and a frontend product, see [DevDistEmbeddedFrontendSupport]. */
  EMBEDDED_FRONTENDS,

  /** The runtime module repository component of a product. */
  RUNTIME_MODULE_REPOSITORY,
}

/**
 * One split product of a half.
 *
 * A split product has a plan: its distribution is assembled from components. A product outside the map keeps no plan.
 */
@ApiStatus.Internal
data class SplitDevDistribution(
  /**
   * The case-safe name of a product whose `dev-build.json` key collides with another key on a case-insensitive file
   * system: the case-folded key, an underscore and a suffix, the shape `product_name_error` of
   * `dev_plugin_remainder.bzl` accepts. It names the divergent plugin-plan file and the chain stem of the product, so
   * no output path of the product folds to a path of the other product. An empty value uses the key.
   */
  @JvmField val caseSafeName: String = "",
  /**
   * The bundled plugins the composer writes first, in this order. The order is a byte contract of
   * `plugins/plugin-classpath.txt`, so a change here is a fingerprint change. An empty order keeps the registration
   * order. Only a product with a byte contract lists one.
   */
  @JvmField val compositionOrder: List<String> = emptyList(),
  /** The plugins the product loads on demand, which a run configuration of the product adds. */
  @JvmField val onDemandPluginModules: List<String> = emptyList(),
  /** The plugins that every distribution of the product adds, beside the modules of its run configurations. */
  @JvmField val additionalPluginModules: List<String> = emptyList(),
  /**
   * The modules whose runtime classpath a platform patch of the product loads. A Kotlin fragment that applies the
   * patch, the reference fragment among them, declares the raw outputs and the libraries of each module and of its
   * dependency closure, whichever jars the payload packs.
   */
  @JvmField val runtimeClasspathModules: List<String> = emptyList(),
)

/** The `build/dev-build.json` keys of the split products of the half, in map order. */
@get:ApiStatus.Internal
val DevDistHalf.splitProducts: Set<String>
  get() = splitDistributions.keys

/**
 * The name of [product] in a divergent plugin-plan file name and in a chain stem: its [SplitDevDistribution.caseSafeName],
 * or the product itself when it has none.
 */
@ApiStatus.Internal
fun DevDistHalf.caseSafeProductName(product: String): String {
  return caseSafeProductName(product, splitDistributions.get(product)?.caseSafeName ?: "")
}

/** The extra plugin modules of a split product, the on-demand plugins first. `null` for a product that is not split. */
internal fun DevDistHalf.extraPluginModules(product: String): List<String>? = splitDistributions.get(product)?.extraPluginModules()

/**
 * The bundled plugins of a split product that the composer writes first, the frozen
 * [SplitDevDistribution.compositionOrder]. Empty for a product that is not split. The bundled tier of
 * `DEV_DIST_PLUGIN_COMPONENTS` starts with this list.
 */
internal fun DevDistHalf.compositionOrder(product: String): List<String> = splitDistributions.get(product)?.compositionOrder ?: emptyList()

/** The hand-listed extra plugin modules of every split product of [splitProducts], in map order. */
internal fun DevDistHalf.handExtraPopulation(splitProducts: Set<String> = this.splitProducts): Set<String> {
  val result = LinkedHashSet<String>()
  for ((product, config) in splitDistributions) {
    if (product in splitProducts) {
      result.addAll(config.extraPluginModules())
    }
  }
  return java.util.Collections.unmodifiableSet(result)
}

/**
 * The extra plugin modules of every split product of [splitProducts]: [handExtraPopulation], then the modules the
 * dev-server run configurations name. See [DevDistRunConfigurations].
 */
internal fun DevDistHalf.extraPopulation(
  runConfigurations: DevDistRunConfigurations,
  splitProducts: Set<String> = this.splitProducts,
): Set<String> {
  val result = LinkedHashSet(handExtraPopulation(splitProducts))
  for (modules in runConfigurations.modulesByProduct.values) {
    result.addAll(modules)
  }
  return java.util.Collections.unmodifiableSet(result)
}

private fun SplitDevDistribution.extraPluginModules(): List<String> {
  val result = LinkedHashSet(onDemandPluginModules)
  result.addAll(additionalPluginModules)
  return java.util.List.copyOf(result)
}

/**
 * Fails when two split products fold to one name on a case-insensitive file system. [caseSafeNames] maps each
 * `dev-build.json` key to its [SplitDevDistribution.caseSafeName], empty for the key itself. The message names both
 * products, so the later one gets a case-safe name.
 */
@ApiStatus.Internal
fun checkSplitProductNamesAreCaseSafe(caseSafeNames: Map<String, String>) {
  val seen = HashMap<String, String>()
  for ((product, caseSafeName) in caseSafeNames) {
    val name = caseSafeProductName(product, caseSafeName)
    val earlier = seen.putIfAbsent(devBuildPathIdentity(name), product) ?: continue
    error("Split products '$earlier' and '$product' fold to one name '$name'; give '$product' a caseSafeName")
  }
}

internal fun caseSafeProductName(product: String, caseSafeName: String): String {
  if (caseSafeName.isEmpty()) return product
  require(caseSafeName.startsWith(product.lowercase() + "_")) {
    "The case-safe name '$caseSafeName' of '$product' must start with '${product.lowercase()}_'"
  }
  return caseSafeName
}

/**
 * Fails when [half] does not extend [upstream]: every split product of [upstream] must be a split product of [half],
 * with an equal value and in the same relative order. So one key of both registries states one product.
 */
@ApiStatus.Internal
fun checkSplitDistributionsExtend(upstream: DevDistHalf, half: DevDistHalf) {
  val order = half.splitDistributions.keys.withIndex().associate { (index, product) -> product to index }
  var previous = -1
  for ((product, value) in upstream.splitDistributions) {
    val own = half.splitDistributions.get(product)
    check(own == value) {
      "The ${half.name} half states the split product '$product' as $own, and the ${upstream.name} half states it as $value"
    }
    val position = order.getValue(product)
    check(position > previous) {
      "The ${half.name} half states the split product '$product' in another order than the ${upstream.name} half"
    }
    previous = position
  }
}

/**
 * The binder of the closed layout asset sources of a half.
 *
 * A closed source is a [DevPluginLayoutAssetSource] that only a product of one half reads: the debugger egg, the Jupyter
 * frontend, a native dependency archive, the native helper archive and the script SDK archive. The binder binds each
 * one to a raw input of the plan, and it writes the targets that produce the input. A binder that cannot bind a source
 * fails, and the message names the source.
 */
@ApiStatus.Internal
interface DevDistAssetBinder {
  /** The raw input that [source] reads. The plan refers to it by its ID, its file name and its kind. */
  fun bind(source: DevPluginLayoutAssetSource, index: DevDistBazelIndex): DevDistPluginRawInput

  /** The statements that the targets of the sources of [requests] add to the `dev` sections. */
  fun sectionStatements(requests: List<DevDistPluginRequest>, index: DevDistBazelIndex): DevDistHalfSectionStatements
}

/** The statements that a half adds to the `dev` sections of the modules that its targets live in. */
@ApiStatus.Internal
class DevDistHalfSectionStatements(
  /** The statements at the end of the `dev` section of a module, keyed by module and in the order of the section. */
  @JvmField val statements: Map<String, List<String>> = emptyMap(),
  /** The load lines that the statements of a module need, keyed by module. */
  @JvmField val loadStatements: Map<String, List<LoadStatement>> = emptyMap(),
  /** The source directories that a resource filegroup declares, each as an absolute package and a package-relative path. */
  @JvmField val resourceDirectories: List<Pair<String, String>> = emptyList(),
)

/** A binder that binds no closed source. The half of such a binder has no product that reads one. */
internal class RefusingDevDistAssetBinder(private val halfName: String) : DevDistAssetBinder {
  override fun bind(source: DevPluginLayoutAssetSource, index: DevDistBazelIndex): DevDistPluginRawInput {
    throw DevDistUnplannableLayoutException("The $halfName half cannot bind the layout source $source")
  }

  override fun sectionStatements(requests: List<DevDistPluginRequest>, index: DevDistBazelIndex): DevDistHalfSectionStatements {
    return DevDistHalfSectionStatements()
  }
}

/**
 * The embedded frontend of a half: the plugin that packs it, its descriptor, its application info, its icons and its
 * branding.
 *
 * A product that embeds the frontend packs the layout of [pluginMainModule] with the [descriptorModule]. A frontend
 * product packs the embedded descriptor of the product that embeds its frontend as its root descriptor. The products
 * that render one descriptor form a class, see [DevDistEmbeddedFrontendClasses].
 */
@ApiStatus.Internal
interface DevDistEmbeddedFrontendSupport {
  /** The main module of the plugin that packs the embedded frontend. */
  val pluginMainModule: String

  /** The package of that plugin. It holds the helper targets and the generated embedded descriptors. */
  val pluginPackage: String

  /** The module whose jar packs the embedded descriptor and the client application info. */
  val descriptorModule: String

  /** The entry of the embedded descriptor in the jar of [descriptorModule]. */
  val descriptorLoadPath: String

  /** The label of the client application-info template that every embedded frontend stamps. */
  val clientApplicationInfo: String

  /** The entry of the stamped client application info in the jar of [descriptorModule]. */
  val clientApplicationInfoPath: String

  /** The label of the build-number file that every embedded frontend stamps. */
  val frontendBuildNumber: String

  /** The module whose jar packs the frontend product icons. */
  val iconsModule: String

  /** The frontend product icons, as the images directory of a product names them and as [iconsModule] packs them. */
  val iconPatches: List<Pair<String, String>>

  /** The index of the frontend patcher in the layout of [pluginMainModule]. The embedded descriptor patcher precedes it. */
  val frontendPatcherIndex: Int

  /** The prefix of the raw input IDs of the embedded frontend in a plan file. */
  val inputIdPrefix: String

  /** The product whose helper targets keep the unsuffixed names. */
  val baselineProduct: String

  /** The label of the branding filegroup of [baselineProduct]. Every other product declares one beside its images. */
  val baselineBrandingLabel: String

  /** Whether [layout] packs the embedded frontend. A frontend product packs the plugin without it. */
  fun packsEmbeddedFrontend(layout: PluginLayout): Boolean

  /** The images directory of [product], relative to the monorepo root. A product without a known path fails. */
  fun imagesDirectory(product: String): String

  /** The file name of the embedded descriptor that [home] writes into [pluginPackage]. */
  fun descriptorFileName(home: String): String

  /** The header of the embedded descriptor of one class, see [DevDistEmbeddedFrontendClasses]. */
  fun descriptorHeader(source: String, embeddingProducts: List<String>, frontendProducts: List<String>): String

  /**
   * The classes of [products]. [embeddingProducts] names the products whose layouts pack the embedded frontend. The
   * walk follows [productOrder], so the first product of a class is its home.
   */
  fun collectClasses(
    products: List<DiscoveredProduct>,
    embeddingProducts: Set<String>,
    productOrder: Collection<String>,
    outputProvider: ModuleOutputProvider,
  ): DevDistEmbeddedFrontendClasses

  /** The embedded descriptors in the packages of [generated] that the run does not write, relative to [projectRoot]. */
  fun staleDescriptors(projectRoot: Path, generated: Collection<String>): List<String>

  /** The properties of the host product of the frontend product [properties], or `null` when it has no host. */
  fun hostProperties(properties: ProductProperties): ProductProperties?
}

/**
 * The classes of the embedded descriptor: the products that embed the frontend and the frontend products whose content
 * renders one text. The home of a class is its first product that embeds the frontend. Only the home writes the
 * descriptor. The other members of the class read it.
 */
@ApiStatus.Internal
interface DevDistEmbeddedFrontendClasses {
  /** The home of the class of [product]. A product that no class states is its own home. */
  fun home(product: String): String

  /** The products of [home] that embed the frontend, in the product order. */
  fun embeddingMembers(home: String): List<String>

  /** The frontend products of [home], in the product order. */
  fun frontendMembers(home: String): List<String>

  /** The label of the jar that packs the root descriptor of every frontend product, keyed by the frontend product. */
  fun frontendRootDescriptorJars(): Map<String, String>

  /**
   * The `content_module_jar` calls of the root descriptor jars. The `dev` section of
   * [DevDistEmbeddedFrontendSupport.descriptorModule] declares them.
   */
  fun renderFrontendRootDescriptorJars(): List<Target>
}

/** The classes of a half without an embedded frontend: every product is its own home, and no product reads a class. */
internal object NoDevDistEmbeddedFrontendClasses : DevDistEmbeddedFrontendClasses {
  override fun home(product: String): String = product

  override fun embeddingMembers(home: String): List<String> = emptyList()

  override fun frontendMembers(home: String): List<String> = emptyList()

  override fun frontendRootDescriptorJars(): Map<String, String> = emptyMap()

  override fun renderFrontendRootDescriptorJars(): List<Target> = emptyList()
}

/** The platform patches of a half. */
@ApiStatus.Internal
interface DevDistPlatformPatchSupport {
  /** The empty target table of one run. */
  fun newTargets(): DevDistPlatformPatchTargets
}

/** The `dev_dist_platform_patch` targets of the split products of one run. */
@ApiStatus.Internal
interface DevDistPlatformPatchTargets {
  /** The label of the target that writes [entry] of [product]. [moduleLabel] gives the label of a module by its JPS name. */
  fun label(product: String, entry: DevPlatformEntryPatch, moduleLabel: (String) -> String): String

  /** Whether no product states a platform patch. */
  val isEmpty: Boolean

  /** The files of the generated patch package, keyed by the project-relative path. */
  fun renderPackage(): Map<String, String>
}

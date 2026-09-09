// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.descriptorFiles
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.findApplicationInfoInSources
import org.jetbrains.intellij.build.getProductionLibraryDependencies
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.intellij.build.impl.DescriptorMarker
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SupportedDistribution
import com.intellij.platform.productMode.ProductMode
import org.jetbrains.intellij.build.impl.createContentModuleFilter
import org.jetbrains.intellij.build.impl.createFrontendModuleFilter
import org.jetbrains.intellij.build.impl.createProductModeContentModuleFilter
import org.jetbrains.intellij.build.impl.devDistHostPlatform
import org.jetbrains.intellij.build.impl.devDistHostPlatformArch
import org.jetbrains.intellij.build.impl.devDistHostPlatformOs
import org.jetbrains.intellij.build.impl.emptyFrontendModuleFilter
import org.jetbrains.intellij.build.impl.isProductContentModuleScrambled
import org.jetbrains.intellij.build.impl.osArchDescriptorMarker
import org.jetbrains.intellij.build.isPluginModulePackedIntoSeparateJar
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildProductContentXml
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.util.TreeMap
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Which plugins a `dev_dist_plugin_descriptor` action can patch, and everything one plugin's patch needs that the
 * Bazel side cannot derive.
 *
 * The action is the one producer of a bundled plugin's patched `META-INF/plugin.xml`. Its request is generated and
 * never hand-written, so the leaf and the plan cannot drift. The descriptor writer's curated cases are the committed gate,
 * and the snapshot diff of a composed dist is the regression guard.
 *
 * One population has one reader. [plugins] holds every selected bundled plugin variant. An unsupported variant stops
 * generation. A fragment reads the produced descriptor of every listed plugin.
 */
internal class PluginDescriptorPlan(
  @JvmField val platformPrefix: String,
  /** The [com.intellij.platform.productMode.ProductMode] id, such as `monolith` or `frontend`, or empty when a test states none. */
  @JvmField val mode: String = "",
  /** The generator sorts entries by main module. Explicit source configuration retains captured request order. */
  @JvmField val plugins: List<PluginDescriptorEntry>,
  /** Generated source files, keyed by their project-relative paths. */
  @JvmField val generatedFiles: Map<String, String> = emptyMap(),
  /** The actions of the two generated entries of the application-info module jar, or `null` when the product plans none. */
  @JvmField val productDescriptor: ProductDescriptorPlan? = null,
)

/**
 * The platforms a dev distribution is ever assembled for, as `dev_launch_dependencies.bzl`'s `HOST_PLATFORMS` spells
 * them.
 *
 * A plugin whose descriptor differs by OS or architecture gets one plan entry per layout variant, and every entry
 * states which of these platforms it serves. The set target of a fragment is per platform, and `//build` selects one.
 */
internal val HOST_PLATFORMS: List<String> = listOf(
  "darwin_aarch64",
  "darwin_x64",
  "linux_aarch64",
  "linux_x64",
  "windows_aarch64",
  "windows_x64",
)

/** The operating-system tokens [HOST_PLATFORMS] spells. Derived from that list, so no second spelling can drift. */
private val HOST_PLATFORM_OPERATING_SYSTEMS: Set<String> = HOST_PLATFORMS.mapTo(LinkedHashSet()) { it.substringBeforeLast('_') }

/** The architecture tokens [HOST_PLATFORMS] spells. */
private val HOST_PLATFORM_ARCHITECTURES: Set<String> = HOST_PLATFORMS.mapTo(LinkedHashSet()) { it.substringAfterLast('_') }

/**
 * Which [HOST_PLATFORMS] entries one layout variant serves.
 *
 * A variant token has four shapes, and each one gives its platforms. The empty token serves every platform. An
 * operating-system token serves that operating system on every architecture. An architecture token serves that
 * architecture on every operating system. A [HOST_PLATFORMS] entry serves itself alone.
 *
 * So the platform set follows from the variant, and the plan states no platform list.
 * `dev_dist_plugin_descriptor_platforms` of `dev_dist_plugin_descriptor.bzl` mirrors this function. A drift between the
 * two is loud on the Bazel side: every variant of one plugin is in one set target's list, and that rule fails when two
 * of them survive its platform filter.
 */
private fun hostPlatformsOfVariant(variant: String): List<String> = when {
  variant.isEmpty() -> HOST_PLATFORMS
  variant in HOST_PLATFORM_OPERATING_SYSTEMS -> HOST_PLATFORMS.filter { it.startsWith("${variant}_") }
  variant in HOST_PLATFORM_ARCHITECTURES -> HOST_PLATFORMS.filter { it.endsWith("_$variant") }
  variant in HOST_PLATFORMS -> listOf(variant)
  else -> error("'$variant' is no layout variant, so no platform list follows from it")
}

/**
 * `OsFamily.osId` and `JvmArchitecture.marketplaceName`, keyed by the token [HOST_PLATFORMS] spells.
 *
 * The generated plan carries this map, because the marker row and the version suffix of a one-platform variant are
 * built from these two spellings and no Bazel rule can read an enum. Generated and never hand-written, so a renamed
 * `osId` reaches the macro through a regeneration.
 */
private val MARKETPLACE_NAMES: Map<String, String> = buildMap {
  for (os in OsFamily.entries) {
    put(hostPlatformOs(os), os.osId)
  }
  for (arch in JvmArchitecture.entries) {
    put(hostPlatformArch(arch), arch.marketplaceName)
  }
}

/** How `HOST_PLATFORMS` spells an [OsFamily] - `platform_parts` of `dev_launch_dependencies.bzl` splits it back. */
private fun hostPlatformOs(os: OsFamily): String = devDistHostPlatformOs(os)

/** How `HOST_PLATFORMS` spells a [JvmArchitecture]. */
private fun hostPlatformArch(arch: JvmArchitecture): String = devDistHostPlatformArch(arch)

/** The [HOST_PLATFORMS] entry of [distribution], which is also the id of its plugin plan variant. */
internal fun devDistHostPlatform(distribution: SupportedDistribution): String = devDistHostPlatform(distribution.os, distribution.arch)

/** One bundled plugin, as both halves of the plan need it. */
internal class PluginDescriptorEntry(
  @JvmField val mainModule: String,
  /**
   * The layout variant, or the empty string for a plugin whose one layout serves every platform.
   *
   * `darwin_aarch64` for a variant restricted to one OS and one architecture, `windows` for one restricted to an OS
   * alone. It names the target and the output file's directory, so two variants of one plugin never collide.
   */
  @JvmField val variant: String,
  /**
   * The main module's `jvm_library` target, which is the package the descriptor label comes from.
   *
   * The entry does not carry the `dev_dist_plugin_descriptor` target of that package. The dev section of the plugin
   * decides the target, and it reads the entry first. So [DevDistToolVerdicts.ownDescriptorTarget] answers it.
   */
  @JvmField val moduleTarget: String,
  /** `META-INF/plugin.xml`'s path inside [moduleTarget]'s Bazel package. */
  @JvmField val descriptor: String,
  /** Whether [descriptor] is an entry in [moduleTarget]'s test output jar. */
  @JvmField val descriptorInTestOutput: Boolean = false,
  /**
   * The content modules the product's [ContentModuleFilter] refuses, in descriptor order.
   *
   * The survivors are [descriptor]'s own `<content>`, which the action already declares as an input. So only a refusal
   * is stated, and this list is empty for every entry of this product today.
   *
   * It reaches the leaf through the plugin's own report; see `collectPluginDescriptorReports`. The leaf is where every
   * other fact of an entry is stated, and a refusal the leaf cannot express would stop the build with no way out.
   */
  @JvmField val refusedContentModules: List<String>,
  /** Which surviving content module takes `separate-jar="true"`. A deviation, normally empty. */
  @JvmField val separateJar: List<String>,
  /**
   * Every descriptor beyond the plugin's own `META-INF/plugin.xml` that the patch reads, as `(load path, label)`.
   *
   * All of them, and not only the ones the flat `dev_dist_descriptors.bzl` carries. That file exists because a Bazel
   * probe finds `<module>.xml` and `META-INF/plugin.xml` without being told; this action declares its own inputs, so
   * a conventional path needs a declaration here exactly as much as any other.
   */
  @JvmField val descriptors: List<DeclaredDescriptor>,
  /**
   * The rows of [descriptors] the convention does not give, as `(load path, project-relative path)`.
   *
   * A row the convention gives is the descriptor of one `<module/>` of this plugin's own `<content>`, at
   * `<content module name>.xml` inside a production resource root of the module that declares it. The converter reads
   * the plugin's `<content>` and derives such a row itself. This list is the remainder, which is every row an
   * `xi:include` reaches, and it is what the per-plugin report states.
   *
   * A path and not a label, because the report is checked in beside the plugin and read by both repository halves. A
   * path outside `community/` is a path a community package cannot name, and that is the whole rule the community half
   * applies.
   */
  @JvmField val includeDescriptors: List<DescriptorPathRow>,
  /**
   * [libraryDescriptors] by name rather than by label, for the per-plugin report.
   *
   * A label states a Bazel package, and the report states none. The converter resolves the library to its container
   * label itself, which is what lets the community half decide alone.
   */
  @JvmField val libraryDescriptorRows: List<LibraryDescriptorRow>,
  /**
   * The descriptors of this plugin's closure that only a library jar answers, as `(load path, container label)`.
   *
   * The load path is also the zip entry, because `toLoadPath` strips the leading `/`. Normally empty: one plugin of
   * this product reads a descriptor the Kotlin compiler ships inside a library jar.
   */
  @JvmField val libraryDescriptors: List<DeclaredLibraryDescriptor>,
  /**
   * The layout's raw text patch as marker-table rows, in the order it applies them. Empty for a layout that patches
   * nothing - see [renderDescriptorMarker] for the two row shapes.
   */
  @JvmField val markers: List<String>,
  /** What the layout appends to the IDE build version. Empty for a layout that stamps it unchanged. */
  @JvmField val versionSuffix: String,
  @JvmField val compatibleBuildRange: String?,
  /**
   * Whether [markers] and [versionSuffix] follow from [variant], so the plan states neither - see [osArchStamps].
   *
   * True for a variant restricted to one operating system and one architecture. The plan verifies both against the
   * layout. Generation fails when the values differ, so the macro can derive both.
   */
  @JvmField val derivesOsArchStamps: Boolean,
  @JvmField val embedsContentModules: Boolean,
  @JvmField val exactVersion: Boolean,
  @JvmField val retainProductDescriptor: Boolean,
  /** The plugin directory, or `null` when the layout takes the derived name. */
  @JvmField val directoryName: String?,
  /** The selected original layout. It is retained only for the packing plan. */
  @JvmField val layout: PluginLayout? = null,
  /** The filtered descriptor content in its original order. */
  @JvmField val contentModules: List<DeclaredContentModule> = emptyList(),
  /** The embedded product descriptor that this plugin packs, if it has one. */
  @JvmField val embeddedProductDescriptor: EmbeddedProductDescriptorPlan? = null,
  /**
   * The content modules each product mode refuses at run time, keyed by the mode id, such as `frontend`.
   *
   * A fact of the plugin's modules, from the JPS walk of `ProductModeLoadingRules`, so every product's entry of one
   * plugin states the same map. The descriptor keeps every module. Under a product of that mode, the rules place no jar
   * of a refused module, and the run time excludes the module.
   */
  @JvmField val modeRefusedContentModules: Map<String, List<String>> = emptyMap(),
)

/** The declared inputs and model facts for one embedded product descriptor action. */
internal data class EmbeddedProductDescriptorPlan(
  /**
   * The product that declares the action and writes [source]: the first product of its class, see
   * [DevDistEmbeddedFrontendClasses]. The other products of the class read both.
   */
  @JvmField val home: String,
  /** The direct Bazel label of the generated base descriptor. */
  @JvmField val source: String,
  /** Descriptor file label to resolver load path, in label order. */
  @JvmField val descriptors: Map<String, String>,
  /** Java container label to space-separated resolver load paths, in label order. */
  @JvmField val libraryDescriptors: Map<String, String>,
  @JvmField val modules: List<String>,
  @JvmField val separateJar: List<String>,
  /** The application info action of the embedded frontend, or `null` when the plugin packs no frontend of its own. */
  @JvmField val frontendApplicationInfo: FrontendApplicationInfoPlan? = null,
)

/**
 * The actions that write the generated entries of the application-info module jar of one product.
 *
 * `dev_dist_product_descriptor` resolves the product descriptor, the text `processAndGetProductPluginContentModules`
 * writes. `dev_dist_product_application_info` replaces the markers of [replacements] in the application info, the text
 * `computeAppInfoXml` writes. Only a product with [replacements] has this action, see [hasApplicationInfo]. The
 * generator writes the targets and [source] into [PRODUCT_DESCRIPTOR_PACKAGE], and `dev_dist_platform_jar` patches the
 * outputs into the jar, see [applicationInfoPatchLabel].
 */
internal data class ProductDescriptorPlan(
  /** The case-safe name of the product. It names the targets and [source]. */
  @JvmField val name: String,
  /** The application-info module. */
  @JvmField val mainModule: String,
  /** The label of the generated Product DSL content, with the module sets and the deprecated includes inlined. */
  @JvmField val source: String,
  /** The project-relative path of [source]. */
  @JvmField val sourceRelativePath: String,
  /** The text of [source], with its header. */
  @JvmField val content: String,
  /** The entry of the product descriptor in the jar: `META-INF/plugin.xml` or `META-INF/<prefix>Plugin.xml`. */
  @JvmField val descriptorPath: String,
  /** Descriptor file label to resolver load path, in label order. */
  @JvmField val descriptors: Map<String, String>,
  /** Java container label to space-separated resolver load paths, in label order. */
  @JvmField val libraryDescriptors: Map<String, String>,
  /** The optional content modules the content module filter of the product refuses, in descriptor order. */
  @JvmField val refusedContentModules: List<String>,
  /** The content modules the product scrambles, in descriptor order. Their `<module/>` elements get no descriptor. */
  @JvmField val scrambledContentModules: List<String>,
  /** The label of the `idea/<prefix>ApplicationInfo.xml` source, with its markers. */
  @JvmField val applicationInfo: String,
  /** The entry of the application info in the jar, `idea/<prefix>ApplicationInfo.xml`. */
  @JvmField val applicationInfoPath: String,
  /** `ProductProperties.appInfoXmlReplacements` as `<key>=<value>`, in their order. */
  @JvmField val replacements: List<String>,
) {
  /** This plan with no name and no source header, so the plans of two products that render the same content are equal. */
  fun withoutIdentity(): ProductDescriptorPlan {
    return copy(name = "", source = "", sourceRelativePath = "", content = content.substringAfter(PRODUCT_DESCRIPTOR_HEADER_END))
  }

  /** The label of the product descriptor action. */
  val descriptorLabel: String
    get() = "//$PRODUCT_DESCRIPTOR_PACKAGE:${name}_product_descriptor"

  /**
   * Whether the product has the application info action. A product without [replacements] patches its application info
   * source as it is, because a dev distribution stamps no build number.
   */
  val hasApplicationInfo: Boolean
    get() = replacements.isNotEmpty()

  /** The label of the application info action. Only a plan with [hasApplicationInfo] declares it. */
  val applicationInfoLabel: String
    get() = "//$PRODUCT_DESCRIPTOR_PACKAGE:${name}_application_info"

  /** The label of the prefix of `plugins/plugin-classpath.txt`, which the product descriptor action writes too. */
  val pluginClasspathPrefixLabel: String
    get() = "$descriptorLabel.plugin-classpath-prefix"

  /**
   * The label that `dev_dist_platform_jar` patches as [applicationInfoPath], spelled for a plan package of [index]: the
   * application info action, or the [applicationInfo] source of a plan without [hasApplicationInfo].
   *
   * The packer writes the patches first, in their order. `layoutPlatformDistribution` patches the application info
   * before `layoutDistribution` applies the patch of the product descriptor, so `JarPackager` writes the application info
   * first and the product descriptor last. A source file as a patch keeps that order and runs no action.
   */
  fun applicationInfoPatchLabel(index: DevDistBazelIndex): String {
    return if (hasApplicationInfo) applicationInfoLabel else index.planLabel(applicationInfo)
  }
}

/** The end of the header of a generated product descriptor source. The content starts after it. */
private const val PRODUCT_DESCRIPTOR_HEADER_END: String = "processAndGetProductPluginContentModules loads the same text -->\n"

/** The generated package of every [ProductDescriptorPlan]: the targets, and the Product DSL content of each product. */
internal const val PRODUCT_DESCRIPTOR_PACKAGE: String = "build/dev-dist-product-descriptors"

/** The declared file inputs of one `dev_dist_frontend_application_info` action, as labels. */
internal data class FrontendApplicationInfoPlan(
  @JvmField val clientApplicationInfo: String,
  @JvmField val productApplicationInfo: String,
)

/** One descriptor the action declares: the load path its request is keyed by, and the label that names the file. */
internal class DeclaredDescriptor(
  @JvmField val loadPath: String,
  @JvmField val label: String,
  @JvmField val testOutput: Boolean = false,
  @JvmField val moduleName: String? = null,
)

/** One descriptor of the per-plugin report: the load path, and the project-relative path of the file that answers it. */
internal class DescriptorPathRow(@JvmField val loadPath: String, @JvmField val relativePath: String)

/**
 * One library descriptor of the per-plugin report, by name.
 *
 * The two names are what the converter looks the library up by: the module whose dependency list holds it, and its JPS
 * name. No name of a jar, because a jar file name carries the artifact version.
 */
internal class LibraryDescriptorRow(
  @JvmField val loadPath: String,
  @JvmField val moduleName: String,
  @JvmField val libraryName: String,
)

/**
 * One descriptor the action reads out of a declared library: the load path, which is also the entry, and the container.
 *
 * The container groups the library's jars, and its label carries no artifact version - see [libraryContainerLabel]. The
 * action expands it and reads the entry out of the first jar that has it.
 */
internal class DeclaredLibraryDescriptor(@JvmField val loadPath: String, @JvmField val containerLabel: String)

/**
 * The build date `.SNAPSHOT` becomes, and therefore the date an EAP product's `majorReleaseDate` is formatted from.
 *
 * `DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS` of `dev_dist_build_date.bzl` pins the same date for the actions. The
 * descriptor writer formats the release date of an EAP product from it. Keep the two equal.
 */
internal const val PINNED_BUILD_DATE_IN_SECONDS: Long = 1767225600 // 2026-01-01T00:00:00Z

/**
 * The plan of one product, from the product properties, the JPS model and the per-plugin descriptor closures.
 *
 * [closureOf] is the walk of [DescriptorCollector.collectPluginClosure], passed in so that the collector stays the one
 * owner of the closure and this function stays the one owner of the deviations.
 *
 * A plugin with a layout fact the plan cannot state stops the run, see [statedDescriptorFacts].
 */
internal fun collectPluginDescriptorPlan(
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  properties: ProductProperties,
  platformPrefix: String,
  bazelTargets: BazelTargetsInfo.TargetsFile,
  layouts: List<PluginLayout>,
  testPluginsByMainModule: Map<String, TestPluginSpec>,
  closureOf: (
    mainModule: String,
    embedsContentModules: Boolean,
    testPlugin: TestPluginSpec?,
    rootLoadPath: String,
    isContentModuleIncluded: (DeclaredContentModule) -> Boolean,
  ) -> PluginDescriptorClosure,
  generatedClosureOf: (
    mainModule: String,
    rootLoadPath: String,
    sourceRelativePath: String,
    xml: String,
    isContentModuleIncluded: (DeclaredContentModule) -> Boolean,
  ) -> PluginDescriptorClosure,
  /** The half of the run. Its embedded frontend plans the embedded descriptor, and its split products name the product descriptor. */
  half: DevDistHalf,
  embeddedClasses: DevDistEmbeddedFrontendClasses,
  /** Every mode a split product uses, except the monolith. Each plugin entry states the modules each of them refuses. */
  refusingModes: List<ProductMode> = emptyList(),
): PluginDescriptorPlan {
  val bundledPluginModules = layouts.map(PluginLayout::mainModule).distinct().sorted()
  val project = outputProvider.findRequiredModule(properties.applicationInfoModule).project
  val contentModuleFilter = createContentModuleFilter(
    project = project,
    productProperties = properties,
    outputProvider = outputProvider,
    bundledPluginModules = { bundledPluginModules },
  )
  // A plugin is planned once for every product. A product of another mode than the monolith refuses no plugin module at
  // build time: its entry states the modules of each mode in `modeRefusedContentModules`, the rules place no jar of
  // them under that mode, and the run time excludes them. The product filter stays for the product descriptor.
  val pluginContentModuleFilter = pluginContentModuleFilter(properties, contentModuleFilter)
  val modeContentModuleFilters = refusingModes.associateTo(TreeMap()) { mode -> mode.id to createProductModeContentModuleFilter(project, mode) }
  // A dev distribution keeps `BuildOptions.enableEmbeddedFrontend` at its default `true`. So the only question left
  // is whether the product declares an embedded frontend root module, and `BuildOptions` is never read here. A frontend
  // product takes the same filter, so it plans every plugin as its monolith does.
  val frontendModuleFilter = when {
    properties.embeddedFrontendRootModule != null || properties.productMode == ProductMode.FRONTEND -> createFrontendModuleFilter(project)
    else -> {
      check(properties.frontendModuleFilter == null) {
        "Product '$platformPrefix' declares no embeddedFrontendRootModule yet states a frontendModuleFilter factory," +
        " which takes a BuildContext this generator does not have"
      }
      emptyFrontendModuleFilter()
    }
  }
  // A frontend product writes no root descriptor of its own. It packs the embedded descriptor of the product that embeds
  // its frontend, see `DevDistEmbeddedFrontendClasses`.
  val embeddedFrontend = half.embeddedFrontend
  val embeddedDescriptor = when {
    embeddedFrontend == null || layouts.none(embeddedFrontend::packsEmbeddedFrontend) -> null
    else -> collectEmbeddedProductDescriptor(
      support = embeddedFrontend,
      index = index,
      properties = properties,
      platformPrefix = platformPrefix,
      outputProvider = outputProvider,
      bazelTargets = bazelTargets,
      frontendModuleFilter = frontendModuleFilter,
      embeddedClasses = embeddedClasses,
      generatedClosureOf = generatedClosureOf,
    )
  }

  val layoutsByMainModule = layouts.groupBy(PluginLayout::mainModule)
  val plugins = ArrayList<PluginDescriptorEntry>()
  for (mainModule in bundledPluginModules) {
    // One plugin reaches the assembly as one layout variant per (os, arch) or per marketplace restriction, and the
    // target platform then selects one - `devModePluginCandidates`. So a plan entry is one (plugin, variant), and it
    // states which platforms the variant serves. A release-cycle restriction such as `NOT_FOR_RELEASE` is not a
    // platform restriction and does not split a plugin.
    val variants = layoutsByMainModule.getValue(mainModule)
    val entries = ArrayList<PluginDescriptorEntry>()
    val claimant = HashMap<String, String>()
    for (layout in variants) {
      val restriction = when (val answer = layoutVariant(layout)) {
        is VariantResult.Selected -> answer.variant
        VariantResult.NotSelected -> continue
        VariantResult.Unstatable -> error(
          "Plugin '$mainModule' has a layout variant that restricts the platform to" +
          " ${layout.bundlingRestrictions.supportedOs} and ${layout.bundlingRestrictions.supportedArch}." +
          " No platform list states this restriction."
        )
      }
      for (platform in restriction.platforms) {
        val earlier = claimant.put(platform, restriction.variant)
        check(earlier == null) {
          "Plugin '$mainModule' has layout variants '$earlier' and '${restriction.variant}' that both serve '$platform'." +
          " No single descriptor answers this platform."
        }
      }
      entries.add(planEntry(
        index = index,
        mainModule = mainModule,
        layout = layout,
        variant = restriction,
        outputProvider = outputProvider,
        bazelTargets = bazelTargets,
        contentModuleFilter = pluginContentModuleFilter,
        modeContentModuleFilters = modeContentModuleFilters,
        frontendModuleFilter = frontendModuleFilter,
        testPlugin = testPluginsByMainModule.get(mainModule),
        embeddedProductDescriptor = embeddedDescriptor?.plan.takeIf { mainModule == embeddedFrontend?.pluginMainModule },
        closureOf = closureOf,
      ))
    }
    entries.sortBy { it.variant }
    plugins.addAll(entries)
  }

  // Every plugin must reach an entry. A plugin whose every layout variant is `NotSelected` reaches none.
  val accounted = plugins.mapTo(HashSet()) { it.mainModule }
  val unaccounted = bundledPluginModules.filterNot { it in accounted }
  check(unaccounted.isEmpty()) {
    "The descriptor plan of '$platformPrefix' has no entry for $unaccounted. Every bundled plugin must reach an entry."
  }
  return PluginDescriptorPlan(
    platformPrefix = platformPrefix,
    mode = properties.productMode.id,
    plugins = plugins,
    generatedFiles = buildMap {
      embeddedDescriptor?.let { descriptor -> descriptor.content?.let { put(descriptor.relativePath, it) } }
    },
    productDescriptor = collectProductDescriptor(
      index = index,
      project = project,
      properties = properties,
      platformPrefix = platformPrefix,
      outputProvider = outputProvider,
      contentModuleFilter = contentModuleFilter,
      generatedClosureOf = generatedClosureOf,
      name = half.caseSafeProductName(platformPrefix),
      generatorCommand = half.generatorCommand,
    ),
  )
}

/**
 * Plans the actions that write the generated entries of the application-info module jar, see [ProductDescriptorPlan].
 *
 * The source is the text `processAndGetProductPluginContentModules` loads: the Product DSL content with the module sets
 * and the deprecated includes inlined. A split product with no Product DSL content fails the generator, because
 * `platform_lib` no longer packs the jar for it.
 */
private fun collectProductDescriptor(
  index: DevDistBazelIndex,
  project: JpsProject,
  properties: ProductProperties,
  platformPrefix: String,
  outputProvider: ModuleOutputProvider,
  contentModuleFilter: ContentModuleFilter,
  generatedClosureOf: (
    mainModule: String,
    rootLoadPath: String,
    sourceRelativePath: String,
    xml: String,
    isContentModuleIncluded: (DeclaredContentModule) -> Boolean,
  ) -> PluginDescriptorClosure,
  /** The case-safe name of the product, which names the two targets and the source. */
  name: String,
  /** The command that regenerates the files of the half, see [DevDistHalf.generatorCommand]. The header names it. */
  generatorCommand: String,
): ProductDescriptorPlan {
  val spec = requireNotNull(properties.getProductContentDescriptor()) { "Split dev distribution '$platformPrefix' declares no Product DSL content" }
  val mainModule = properties.applicationInfoModule
  val module = outputProvider.findRequiredModule(mainModule)
  val sourceFile = requireNotNull(
    outputProvider.findFileInModuleSources(module, PLUGIN_XML_RELATIVE_PATH)
    ?: outputProvider.findFileInModuleSources(module, "META-INF/${properties.platformPrefix}Plugin.xml")
  ) { "Cannot find the product plugin descriptor of '$platformPrefix' in '$mainModule'" }
  val descriptorPath = "META-INF/${sourceFile.fileName}"
  val sourceRelativePath = "$PRODUCT_DESCRIPTOR_PACKAGE/$name.xml"
  val content = renderEmbeddedProductContent(spec, outputProvider)
  val closure = generatedClosureOf(mainModule, descriptorPath, sourceRelativePath, content) { contentModule ->
    !contentModule.isOptional || contentModuleFilter.isOptionalModuleIncluded(contentModule.name.substringBeforeLast('/'), null)
  }
  check(closure.unmodelledContentIncludes.isEmpty()) {
    "The product descriptor of '$platformPrefix' has an unsupported content include: ${closure.unmodelledContentIncludes}"
  }
  val labels = closureDescriptorLabels(closure = closure, index = index, owner = "the product descriptor of '$platformPrefix'")
  val communityHomeDir = index.communityRoot
  val scrambled = closure.declaredContentModules.filter { contentModule ->
    isProductContentModuleScrambled(
      moduleName = contentModule.name,
      isEmbedded = contentModule.loading == "embedded",
      productProperties = properties,
      outputProvider = outputProvider,
      communityHomeDir = communityHomeDir,
    )
  }
  val header = buildString {
    append("<!-- DO NOT EDIT: This file is auto-generated from Kotlin code by collectProductDescriptor -->\n")
    append("<!-- To regenerate, run '").append(generatorCommand).append("' -->\n")
    append("<!-- Source: ${productContentSource(properties)}, with the module sets and the deprecated includes inlined -->\n")
    append("<!-- Product: $platformPrefix. ").append(PRODUCT_DESCRIPTOR_HEADER_END)
  }
  return ProductDescriptorPlan(
    name = name,
    mainModule = mainModule,
    source = ":$name.xml",
    sourceRelativePath = sourceRelativePath,
    content = header + content,
    descriptorPath = descriptorPath,
    descriptors = labels.descriptors,
    libraryDescriptors = labels.libraryDescriptors,
    refusedContentModules = closure.refusedContentModules,
    scrambledContentModules = scrambled.map { it.name },
    applicationInfo = applicationInfoLabel(index = index, project = project, properties = properties, platformPrefix = platformPrefix),
    applicationInfoPath = "idea/${properties.platformPrefix ?: ""}ApplicationInfo.xml",
    replacements = properties.appInfoXmlReplacements.orEmpty().map { (key, value) -> "$key=$value" },
  )
}

/** The labels of the descriptors a generated closure reads: the files, and the library containers with their load paths. */
private class ClosureDescriptorLabels(
  @JvmField val descriptors: Map<String, String>,
  @JvmField val libraryDescriptors: Map<String, String>,
)

/** [ClosureDescriptorLabels] of [closure]. [owner] names the reader in a failure. */
private fun closureDescriptorLabels(
  closure: PluginDescriptorClosure,
  index: DevDistBazelIndex,
  owner: String,
): ClosureDescriptorLabels {
  val descriptors = TreeMap<String, String>()
  for (descriptor in closure.reached) {
    val label = requireNotNull(descriptorLabel(descriptor = descriptor, index = index)) {
      "No label names '${descriptor.relativePath}' of '${descriptor.moduleName}', which $owner reads"
    }
    check(descriptors.put(label, descriptor.loadPath) == null) {
      "$owner reads two load paths from '$label'"
    }
  }

  val libraryLoadPaths = TreeMap<String, MutableList<String>>()
  for (descriptor in closure.libraryJarDescriptors.values) {
    val label = requireNotNull(libraryContainerLabel(descriptor = descriptor, targets = index.targets)) {
      "No container label names library '${descriptor.libraryName}' in '${descriptor.moduleName}', which answers '${descriptor.loadPath}'"
    }
    check(' ' !in descriptor.loadPath) {
      "The load path '${descriptor.loadPath}' of library '${descriptor.libraryName}' holds a space"
    }
    libraryLoadPaths.computeIfAbsent(label) { ArrayList() }.add(descriptor.loadPath)
  }
  return ClosureDescriptorLabels(
    descriptors = descriptors,
    libraryDescriptors = libraryLoadPaths.mapValuesTo(LinkedHashMap()) { (_, loadPaths) -> loadPaths.joinToString(" ") },
  )
}

/**
 * The label of the `idea/<prefix>ApplicationInfo.xml` source of [properties] in the recorded form, composed from the
 * package that exports it, see [DevDistBazelIndex.containingPackageLabel].
 */
internal fun applicationInfoLabel(index: DevDistBazelIndex, project: JpsProject, properties: ProductProperties, platformPrefix: String): String {
  val appInfoPath = findApplicationInfoInSources(project, properties)
  val relativePath = index.projectRoot.relativize(appInfoPath).invariantSeparatorsPathString
  return requireNotNull(index.containingPackageLabel(relativePath)) {
    "No Bazel package exports the application info of '$platformPrefix' at '$relativePath'"
  }
}

/** One layout variant's identity: what names its target, and which platforms take it. */
private class LayoutVariant(
  @JvmField val variant: String,
  @JvmField val os: OsFamily?,
  @JvmField val arch: JvmArchitecture?,
) {
  /** The [HOST_PLATFORMS] entries this variant serves, from [variant] alone - see [hostPlatformsOfVariant]. */
  @JvmField val platforms: List<String> = hostPlatformsOfVariant(variant)
}

/** What [layoutVariant] answers about one layout variant. */
private sealed interface VariantResult {
  /** A variant a dev distribution selects on the platforms it states. */
  class Selected(@JvmField val variant: LayoutVariant) : VariantResult

  /** A variant no dev distribution ever selects, so it contributes no plan entry. */
  object NotSelected : VariantResult

  /** A restriction no [HOST_PLATFORMS] subset states. The caller stops generation for this result. */
  object Unstatable : VariantResult
}

/**
 * Which platforms one layout variant serves.
 *
 * `isPluginApplicable` is the authority, and this mirrors it. A marketplace-only variant reaches no distribution, so it
 * is [VariantResult.NotSelected] rather than an error - `intellij.webp` and `intellij.fullLine` each declare one beside
 * their bundled variant. `getPluginLayoutsByJpsModuleNames` has already dropped the cross-platform-only variants. A
 * release-cycle restriction such as `NOT_FOR_RELEASE` is not a platform restriction and does not split a plugin.
 *
 * Four platform shapes exist: no restriction, one operating system, one architecture, and one of each.
 * `isPluginApplicable` asks `satisfiesBundlingRequirements` twice, once with the operating system and once with `null`,
 * which is what makes the architecture-only shape applicable to every operating system of that architecture.
 */
private fun layoutVariant(layout: PluginLayout): VariantResult {
  val restrictions = layout.bundlingRestrictions
  if (restrictions == PluginBundlingRestrictions.MARKETPLACE) {
    return VariantResult.NotSelected
  }
  val everyOs = restrictions.supportedOs == OsFamily.ALL
  val everyArch = restrictions.supportedArch == JvmArchitecture.ALL
  val os = restrictions.supportedOs.singleOrNull()
  val arch = restrictions.supportedArch.singleOrNull()
  return when {
    everyOs && everyArch -> VariantResult.Selected(LayoutVariant(variant = "", os = null, arch = null))
    everyOs && arch != null -> VariantResult.Selected(LayoutVariant(variant = hostPlatformArch(arch), os = null, arch = arch))
    os != null && everyArch -> VariantResult.Selected(LayoutVariant(variant = hostPlatformOs(os), os = os, arch = null))
    os != null && arch != null -> VariantResult.Selected(LayoutVariant(
      variant = "${hostPlatformOs(os)}_${hostPlatformArch(arch)}",
      os = os,
      arch = arch,
    ))
    else -> VariantResult.Unstatable
  }
}


/**
 * One marker-table row, or `null` when the table's two shapes cannot state this replacement.
 *
 * ### The two shapes
 *
 * `os-arch:<osId>:<marketplaceName>` states the `<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->` replacement of one (os, arch)
 * variant. It carries the operating system and the architecture rather than the replacement text, for two reasons.
 * `osArchDescriptorMarker` stays the one owner of the text, so a change to it reaches both producers at once. And the
 * text holds a newline, which the action's multiline parameter file cannot carry on one line.
 *
 * `marker:<literal>:<replacement>` states a plain replacement. The literal is split off at the first `:`, so a literal
 * that holds one is refused here.
 *
 * ### What both producers do with a row, and why the shapes are this narrow
 *
 * `checkedReplace` compiles the literal as a regular expression and reads `$` and `\` in the replacement. A regular
 * expression engine of the descriptor writer is not Java's `Pattern`, so a row that reached either engine would be a row
 * the two producers could read differently. A row therefore has to be inert in both: the literal states no
 * regular-expression metacharacter, and the replacement states no `$` and no `\`. Both producers then replace the first occurrence of a
 * plain string, which is what `checkedReplace` does for such a pair.
 */
private fun renderDescriptorMarker(marker: DescriptorMarker, os: OsFamily?, arch: JvmArchitecture?): String? {
  if (os != null && arch != null) {
    val expected = osArchDescriptorMarker(os = os, arch = arch)
    if (marker.literal == expected.literal && marker.replacement == expected.replacement) {
      return osArchMarkerRow(os = os, arch = arch)
    }
  }
  if (marker.literal.isEmpty() ||
      marker.literal.contains(':') ||
      marker.literal.any { it in REGEX_METACHARACTERS } ||
      marker.replacement.any { it == '$' || it == '\\' } ||
      marker.literal.contains('\n') ||
      marker.replacement.contains('\n')) {
    return null
  }
  return "marker:${marker.literal}:${marker.replacement}"
}

/** What makes a `checkedReplace` literal a pattern rather than a plain string - see [renderDescriptorMarker]. */
private const val REGEX_METACHARACTERS: String = "\\.[]{}()*+?^\$|"

/** The `os-arch` marker row of one (operating system, architecture) pair. The one owner of that spelling. */
private fun osArchMarkerRow(os: OsFamily, arch: JvmArchitecture): String = "os-arch:${os.osId}:${arch.marketplaceName}"

/** One variant's derived marker row and version suffix, together. */
private class OsArchStamps(@JvmField val marker: String, @JvmField val versionSuffix: String)

/**
 * The marker row and the version suffix a variant of one operating system and one architecture takes, or `null` for
 * every other variant.
 *
 * Both are mechanical. The row names the `<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->` replacement, and a plugin of one
 * platform states that platform in its version. So the plan states neither, and this function is what
 * `dev_dist_plugin_descriptor_os_arch_stamps` of `dev_dist_plugin_descriptor.bzl` mirrors.
 *
 * [statedDescriptorFacts] verifies the layout against this answer. A mismatch stops generation. Thus, each entry
 * carries exactly these two values, and the macro derives both.
 */
private fun osArchStamps(os: OsFamily?, arch: JvmArchitecture?): OsArchStamps? {
  if (os == null || arch == null) {
    return null
  }
  return OsArchStamps(
    marker = osArchMarkerRow(os = os, arch = arch),
    versionSuffix = "-${os.osId}-${arch.marketplaceName}",
  )
}

/** The descriptor facts of one layout variant that the plan states as data. */
@ApiStatus.Internal
class StatedDescriptorFacts(
  /** The marker rows, see [renderDescriptorMarker]. */
  @JvmField val markers: List<String>,
  /** What the layout appends to the IDE build version. Empty for a layout that stamps it unchanged. */
  @JvmField val versionSuffix: String,
  /** Range constraint override. */
  @JvmField val compatibleBuildRange: String?,
  /** Whether [markers] and [versionSuffix] follow from the variant, so the plan states neither, see [osArchStamps]. */
  @JvmField val derivesOsArchStamps: Boolean,
)

/**
 * The marker rows and the version suffix of [layout] for the variant [variant] of [os] and [arch]. Both are `null` for
 * a variant that serves every platform.
 *
 * A fact the layout states as code throws [DevDistUnplannableLayoutException]. So does a one-platform variant whose
 * stated facts differ from the facts the variant derives, because the macro derives both.
 */
@ApiStatus.Internal
fun statedDescriptorFacts(
  mainModule: String,
  variant: String,
  layout: PluginLayout,
  os: OsFamily?,
  arch: JvmArchitecture?,
): StatedDescriptorFacts {
  fun unsupported(detail: String): Nothing {
    throw DevDistUnplannableLayoutException(unplannableDescriptorMessage(mainModule, variant, detail))
  }

  // A post-stamp text patch runs over the text the body produced, and no marker table states one: the stamps have
  // already run by then, so a replacement there is not a fact about the source descriptor.
  if (layout.hasPluginXmlPatcher) {
    unsupported("the layout states pluginXmlPatcher, which runs after the stamps")
  }
  val declaredMarkers = layout.descriptorMarkers
  if (declaredMarkers == null) {
    unsupported("the layout states rawPluginXmlPatcher as code, so no marker table states it")
  }
  val markers = declaredMarkers.map { renderDescriptorMarker(marker = it, os = os, arch = arch) }
  val unstatable = declaredMarkers.filterIndexed { index, _ -> markers.get(index) == null }
  if (unstatable.isNotEmpty()) {
    unsupported(
      unstatable.joinToString(prefix = "the marker table cannot state the replacement of ") { "'${it.literal}'" },
    )
  }
  val versionSuffix = layout.versionSuffix
  if (versionSuffix == null) {
    unsupported("the layout states versionEvaluator as code, so no suffix states it")
  }
  val statedMarkers = markers.map { it!! }
  // The marker row and the version suffix of a one-platform variant, verified rather than stated. The macro derives
  // both, so the plan may keep an entry only while the layout agrees with the derivation.
  val stamps = osArchStamps(os = os, arch = arch)
  if (stamps != null) {
    if (statedMarkers != listOf(stamps.marker)) {
      unsupported(
        "the layout of one (os, arch) variant states the marker rows $statedMarkers," +
        " and '${stamps.marker}' is the row the variant gives",
      )
    }
    if (versionSuffix != stamps.versionSuffix) {
      unsupported(
        "the layout of one (os, arch) variant states the version suffix '$versionSuffix'," +
        " and '${stamps.versionSuffix}' is the suffix the variant gives",
      )
    }
  }
  return StatedDescriptorFacts(
    markers = statedMarkers,
    versionSuffix = versionSuffix,
    compatibleBuildRange = layout.compatibleBuildRange?.name,
    derivesOsArchStamps = stamps != null
  )
}

/** The message of a [DevDistUnplannableLayoutException] the descriptor plan throws. The one owner of its shape. */
private fun unplannableDescriptorMessage(mainModule: String, variant: String, detail: String): String {
  return "Cannot plan the descriptor for plugin '$mainModule' variant '$variant': $detail"
}

/**
 * One plugin layout variant as the plan needs it.
 *
 * A layout fact the plan cannot state throws [DevDistUnplannableLayoutException], see [statedDescriptorFacts]. A
 * descriptor fact the plan cannot state, such as a load path with no label, stops the run.
 */
private fun planEntry(
  index: DevDistBazelIndex,
  mainModule: String,
  layout: PluginLayout,
  variant: LayoutVariant,
  outputProvider: ModuleOutputProvider,
  bazelTargets: BazelTargetsInfo.TargetsFile,
  contentModuleFilter: ContentModuleFilter,
  /** The filter of each refusing mode, keyed by the mode id, see [PluginDescriptorEntry.modeRefusedContentModules]. */
  modeContentModuleFilters: Map<String, ContentModuleFilter>,
  frontendModuleFilter: FrontendModuleFilter,
  testPlugin: TestPluginSpec?,
  embeddedProductDescriptor: EmbeddedProductDescriptorPlan?,
  closureOf: (
    mainModule: String,
    embedsContentModules: Boolean,
    testPlugin: TestPluginSpec?,
    rootLoadPath: String,
    isContentModuleIncluded: (DeclaredContentModule) -> Boolean,
  ) -> PluginDescriptorClosure,
): PluginDescriptorEntry {
  fun unstatable(detail: String): Nothing {
    error(unplannableDescriptorMessage(mainModule, variant.variant, detail))
  }

  val stated = statedDescriptorFacts(
    mainModule = mainModule,
    variant = variant.variant,
    layout = layout,
    os = variant.os,
    arch = variant.arch,
  )

  val embedsContentModules = layout.pathsToScramble.isEmpty()
  // The rejects of this predicate, and not its survivors. A survivor is a `<module/>` the descriptor already states,
  // and the action declares that descriptor, so only a refusal is a fact the action cannot read for itself.
  val refusedContentModules = ArrayList<String>()
  val closure = closureOf(mainModule, embedsContentModules, testPlugin, PLUGIN_XML_RELATIVE_PATH) { contentModule ->
    val included = !contentModule.isOptional ||
                   contentModuleFilter.isOptionalModuleIncluded(
                     moduleName = contentModule.name.substringBeforeLast('/'),
                     pluginMainModuleName = mainModule,
                   )
    if (!included) {
      refusedContentModules.add(contentModule.name)
    }
    included
  }
  if (closure.unmodelledContentIncludes.isNotEmpty()) {
    unstatable(
      closure.unmodelledContentIncludes.joinToString(
        prefix = "an xi:include states an xpointer of its own, so the content order is not modelled: ",
      ) { "'$it'" },
    )
  }
  val libraryDescriptors = ArrayList<DeclaredLibraryDescriptor>()
  val libraryDescriptorRows = ArrayList<LibraryDescriptorRow>()
  for (reached in closure.libraryJarDescriptors.values) {
    val containerLabel = libraryContainerLabel(descriptor = reached, targets = bazelTargets)
    if (containerLabel == null) {
      unstatable(
        "no container label names library '${reached.libraryName}'" +
        " in '${reached.moduleName}', which answers '${reached.loadPath}'",
      )
    }
    libraryDescriptors.add(DeclaredLibraryDescriptor(loadPath = reached.loadPath, containerLabel = containerLabel))
    libraryDescriptorRows.add(LibraryDescriptorRow(
      loadPath = reached.loadPath,
      moduleName = reached.moduleName,
      libraryName = reached.libraryName,
    ))
  }
  val moduleTarget = requireNotNull(
    if (testPlugin == null) moduleRuleTarget(module = mainModule, targets = bazelTargets)
    else testModuleRuleTarget(module = mainModule, targets = bazelTargets)
  ) {
    val outputKind = if (testPlugin == null) "production" else "test"
    "Bundled plugin '$mainModule' has no single $outputKind jar target; run ./build/jpsModelToBazel.cmd"
  }
  val packageDirectory = index.packageDirectory(moduleTarget)
  val descriptorPath = closure.descriptor.relativePath
  check(descriptorPath.startsWith("$packageDirectory/")) {
    "'$descriptorPath' of plugin '$mainModule' is outside the Bazel package '$packageDirectory' of $moduleTarget," +
    " so no label names it"
  }
  // `reached` holds every surviving content module's descriptor: `collectPluginClosure` resolves one under the load
  // path `contentModuleNameToDescriptorFileName` derives, which is the key the action's request uses. A non-embedding
  // layout still declares them, because the classpath descriptor action embeds the survivors.
  val reached = closure.reached.sortedBy { it.loadPath }
  val labelless = reached.filter { descriptorLabel(descriptor = it, index = index) == null }
  if (labelless.isNotEmpty()) {
    unstatable(labelless.joinToString(prefix = "no label names the descriptor it reads: ") { "'${it.relativePath}' of '${it.moduleName}'" })
  }
  val derivedName = derivedPluginDirectoryName(mainModule)
  // The modules each mode refuses, in descriptor order. A mode that refuses none states no row.
  val modeRefusedContentModules = TreeMap<String, List<String>>()
  for ((mode, filter) in modeContentModuleFilters) {
    val refused = closure.declaredContentModules.filter { contentModule ->
      contentModule.isOptional && !filter.isOptionalModuleIncluded(moduleName = contentModule.name.substringBeforeLast('/'), pluginMainModuleName = mainModule)
    }
    if (refused.isNotEmpty()) {
      modeRefusedContentModules.put(mode, refused.map { it.name })
    }
  }
  return PluginDescriptorEntry(
    mainModule = mainModule,
    variant = variant.variant,
    moduleTarget = moduleTarget,
    descriptor = if (testPlugin == null) descriptorPath.removePrefix("$packageDirectory/") else PLUGIN_XML_RELATIVE_PATH,
    descriptorInTestOutput = testPlugin != null,
    refusedContentModules = refusedContentModules,
    separateJar = when {
      embedsContentModules -> separateJarContentModules(
        layout = layout,
        closure = closure,
        outputProvider = outputProvider,
        frontendModuleFilter = frontendModuleFilter,
      )
      else -> emptyList()
    },
    descriptors = reached.map {
      DeclaredDescriptor(
        loadPath = it.loadPath,
        label = descriptorLabel(descriptor = it, index = index)!!,
        testOutput = it.testOutput,
        moduleName = it.moduleName,
      )
    },
    includeDescriptors = reached.filter { !it.testOutput && !conventionalDescriptor(descriptor = it, contentModules = closure.contentModules) }
      .map { DescriptorPathRow(loadPath = it.loadPath, relativePath = it.relativePath) },
    libraryDescriptorRows = libraryDescriptorRows,
    libraryDescriptors = libraryDescriptors,
    markers = stated.markers,
    versionSuffix = stated.versionSuffix,
    compatibleBuildRange = stated.compatibleBuildRange,
    derivesOsArchStamps = stated.derivesOsArchStamps,
    embedsContentModules = embedsContentModules,
    exactVersion = layout.pluginCompatibilityExactVersion,
    retainProductDescriptor = layout.retainProductDescriptorForBundledPlugin,
    directoryName = layout.directoryName.takeIf { it != derivedName },
    layout = layout,
    contentModules = closure.declaredContentModules,
    embeddedProductDescriptor = embeddedProductDescriptor,
    modeRefusedContentModules = modeRefusedContentModules,
  )
}

/**
 * The filter a plugin's `<content>` takes in the plan of [properties]: the product's [contentModuleFilter] for a monolith,
 * and include-all for every other mode. The run time refuses a plugin module by mode, the build does not.
 */
internal fun pluginContentModuleFilter(properties: ProductProperties, contentModuleFilter: ContentModuleFilter): ContentModuleFilter {
  return if (properties.productMode == ProductMode.MONOLITH) contentModuleFilter else includeAllContentModules
}

private val includeAllContentModules: ContentModuleFilter = object : ContentModuleFilter {
  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean = true

  override fun toString(): String = "includeAllContentModules"
}

/**
 * The product application-info label of [properties], composed from the package that exports the file.
 *
 * The embedded frontend takes the product names, version and release date from this file. Every product that embeds the
 * frontend states its own.
 */
private fun embeddedFrontendApplicationInfo(
  support: DevDistEmbeddedFrontendSupport,
  index: DevDistBazelIndex,
  project: JpsProject,
  properties: ProductProperties,
  platformPrefix: String,
): FrontendApplicationInfoPlan {
  return FrontendApplicationInfoPlan(
    clientApplicationInfo = support.clientApplicationInfo,
    productApplicationInfo = applicationInfoLabel(index = index, project = project, properties = properties, platformPrefix = platformPrefix),
  )
}

private class GeneratedEmbeddedProductDescriptor(
  @JvmField val plan: EmbeddedProductDescriptorPlan,
  @JvmField val relativePath: String,
  /** The text of the file at [relativePath], or `null` when another product of the class writes it. */
  @JvmField val content: String?,
)

/**
 * Generates and plans the embedded frontend descriptor that the layout of [DevDistEmbeddedFrontendSupport.pluginMainModule]
 * packs.
 *
 * The XML is written into the Bazel package of that plugin under [DevDistEmbeddedFrontendSupport.descriptorFileName], and
 * the plan names it from the same package. So the `dev` section of the plugin reads a file beside itself, and no other
 * package exports it. Only the home of the class of [platformPrefix] writes the file, see [DevDistEmbeddedFrontendClasses].
 * A frontend product of the class packs the resolved descriptor too.
 */
private fun collectEmbeddedProductDescriptor(
  support: DevDistEmbeddedFrontendSupport,
  index: DevDistBazelIndex,
  properties: ProductProperties,
  platformPrefix: String,
  outputProvider: ModuleOutputProvider,
  bazelTargets: BazelTargetsInfo.TargetsFile,
  frontendModuleFilter: FrontendModuleFilter,
  embeddedClasses: DevDistEmbeddedFrontendClasses,
  generatedClosureOf: (
    mainModule: String,
    rootLoadPath: String,
    sourceRelativePath: String,
    xml: String,
    isContentModuleIncluded: (DeclaredContentModule) -> Boolean,
  ) -> PluginDescriptorClosure,
): GeneratedEmbeddedProductDescriptor {
  val embeddedProperties = requireNotNull(properties.embeddedFrontendProperties?.invoke()) {
    "Product '$platformPrefix' packs the embedded frontend but declares no embedded frontend properties"
  }
  val project = outputProvider.findRequiredModule(properties.applicationInfoModule).project
  val frontendApplicationInfo = embeddedFrontendApplicationInfo(
    support = support,
    index = index,
    project = project,
    properties = properties,
    platformPrefix = platformPrefix,
  )
  val content = renderEmbeddedProductContent(embeddedProductSpec(embeddedProperties, platformPrefix), outputProvider)
  val pluginTarget = requireNotNull(moduleRuleTarget(module = support.pluginMainModule, targets = bazelTargets)) {
    "No production target names '${support.pluginMainModule}', so the embedded frontend descriptor has no package"
  }
  val home = embeddedClasses.home(platformPrefix)
  val fileName = support.descriptorFileName(home)
  val relativePath = "${index.packageDirectory(pluginTarget)}/$fileName"
  val closure = generatedClosureOf(
    support.descriptorModule,
    support.descriptorLoadPath,
    relativePath,
    content,
  ) { true }
  check(closure.unmodelledContentIncludes.isEmpty()) {
    "The generated embedded frontend descriptor has an unsupported content include: ${closure.unmodelledContentIncludes}"
  }

  val labels = closureDescriptorLabels(closure = closure, index = index, owner = "the embedded frontend descriptor")

  val modules = sortedSetOf(support.descriptorModule)
  closure.contentModules.mapTo(modules) { it.substringBeforeLast('/') }
  val header = support.descriptorHeader(
    source = productContentSource(embeddedProperties),
    embeddingProducts = embeddedClasses.embeddingMembers(home).ifEmpty { listOf(platformPrefix) },
    frontendProducts = embeddedClasses.frontendMembers(home),
  )
  return GeneratedEmbeddedProductDescriptor(
    relativePath = relativePath,
    content = if (home == platformPrefix) header + content else null,
    plan = EmbeddedProductDescriptorPlan(
      home = home,
      source = pluginTarget.substringBeforeLast(':') + ":" + fileName,
      descriptors = labels.descriptors,
      libraryDescriptors = labels.libraryDescriptors,
      modules = modules.toList(),
      separateJar = separateJarContentModules(
        layout = PluginLayout.pluginAuto(listOf(support.descriptorModule)),
        closure = closure,
        outputProvider = outputProvider,
        frontendModuleFilter = frontendModuleFilter,
      ).sorted(),
      frontendApplicationInfo = frontendApplicationInfo,
    ),
  )
}

/** The Product DSL content of [properties]. [product] names the product in the failure. */
@ApiStatus.Internal
fun embeddedProductSpec(properties: ProductProperties, product: String): ProductModulesContentSpec {
  return requireNotNull(properties.getProductContentDescriptor()) {
    "The embedded frontend of '$product' declares no Product DSL content"
  }
}

/**
 * The embedded descriptor text of [spec]: every module set and every XML include inlined, as
 * `deprecatedResolveDescriptorForEmbeddedProduct` renders it in production.
 */
@ApiStatus.Internal
fun renderEmbeddedProductContent(spec: ProductModulesContentSpec, outputProvider: ModuleOutputProvider): String {
  return buildProductContentXml(
    spec = spec,
    outputProvider = outputProvider,
    inlineXmlIncludes = true,
    inlineModuleSets = true,
    metadataBuilder = { it.append("  <id>com.intellij</id>\n") },
  ).xml
}

/**
 * The `getProductContentDescriptor()` call that renders the content of [properties], for the header. It names the
 * declaring class, and the class of [properties] when that class only sets what the declaring class reads.
 */
internal fun productContentSource(properties: ProductProperties): String {
  val declaringClass = properties.javaClass.getMethod("getProductContentDescriptor").declaringClass
  val call = "${declaringClass.name}.getProductContentDescriptor()"
  return if (declaringClass == properties.javaClass) call else "$call of ${properties.javaClass.simpleName}"
}

/** Returns whether this descriptor entry serves the selected dev platform. */
internal fun PluginDescriptorEntry.servesDevPlatform(platform: String): Boolean {
  return variant.isEmpty() || variant == platform || variant == platform.substringBeforeLast('_') || variant == platform.substringAfterLast('_')
}

/**
 * The label of the container the library a descriptor lives in is grouped by, or `null` when no target names it.
 *
 * The container and not one jar of it: a per-jar label carries the artifact version, so a Maven bump rewrote every
 * checked-in file that named the jar. This is `LibraryDescription.target`, which is also the key
 * `BazelModuleOutputProvider.findLibraryRoots` asks the manifest for.
 */
internal fun libraryContainerLabel(descriptor: ReachedLibraryDescriptor, targets: BazelTargetsInfo.TargetsFile): String? {
  val moduleLibrary = targets.modules.get(descriptor.moduleName)?.moduleLibraries?.get(descriptor.libraryName)
  val description = moduleLibrary ?: targets.projectLibraries.get(descriptor.libraryName) ?: return null
  return description.target.takeIf { it.isNotEmpty() }
}

/**
 * Which of the plugin's surviving content modules take `separate-jar="true"`.
 *
 * The three gates `embedContentModule` applies, in its order: the embedded descriptor declares a `package`, the
 * content module name holds no `/`, and `isPluginModulePackedIntoSeparateJar` says yes. The verdict is asked of the
 * shared function, so the assembly and the plan cannot answer differently.
 */
private fun separateJarContentModules(
  layout: PluginLayout,
  closure: PluginDescriptorClosure,
  outputProvider: ModuleOutputProvider,
  frontendModuleFilter: FrontendModuleFilter,
): List<String> {
  return closure.contentModules.filter { contentModule ->
    // A name that holds a `/` names a descriptor of another module, and the assembly refuses the attribute for it.
    if (contentModule.substringBeforeLast('/') != contentModule) {
      return@filter false
    }
    val descriptorFile = closure.contentModuleFiles.get(contentModule) ?: return@filter false
    // Read from the source file rather than from the resolved element: resolving an include splices children and
    // never touches a root attribute, so the two carry the same `package`.
    if (!closure.hasPackageAttribute(descriptorFile)) {
      return@filter false
    }
    val module = outputProvider.findModule(contentModule) ?: return@filter false
    isPluginModulePackedIntoSeparateJar(
      module = module,
      layout = layout,
      frontendModuleFilter = frontendModuleFilter,
      productionLibraryDependencies = getProductionLibraryDependencies(module),
    )
  }
}

/**
 * Whether the convention gives this reached descriptor, so the per-plugin report states nothing about it.
 *
 * The convention is one rule: the file is the descriptor of one `<module/>` of the plugin's own `<content>`, it sits in
 * the module that declares that content module, and its path inside a resource root is the load path
 * `contentModuleNameToDescriptorFileName` derives. The converter walks the plugin's `<content>` and applies the same
 * rule, which is why such a row is checked in nowhere.
 *
 * Everything else is a row an `xi:include` reaches, and the report states it by path.
 */
internal fun conventionalDescriptor(descriptor: ReachedDescriptor, contentModules: List<String>): Boolean {
  val owner = contentModules.firstOrNull { it.replace('/', '.') + ".xml" == descriptor.loadPath }?.substringBeforeLast('/')
  return owner == descriptor.moduleName && descriptor.relativePath.endsWith("/${descriptor.loadPath}")
}

/**
 * The entry of [module] in the descriptor index `MODULE_DESCRIPTORS` of the JPS bridge, in the recorded form, or `null`
 * when the index has no entry for the module.
 *
 * `compute_module_descriptor_target` of `jps_target_derivation.bzl` is the Starlark twin. The rule takes the first
 * production resource root in `.iml` order that the jar takes at its own root and that holds `<module name>.xml`. The
 * file must lie inside the Bazel package of the module, and on the repository half of the module.
 */
internal fun bridgeDescriptorLabel(module: JpsModule, index: DevDistBazelIndex): String? {
  val moduleTarget = moduleRuleTarget(module = module.name, targets = index.targets) ?: return null
  val isCommunity = index.isCommunity(module.name) ?: return null
  val packageDirectory = index.packageDirectory(moduleTarget)
  val projectRoot = index.projectRoot.normalize()
  val communityRoot = index.communityRoot.normalize()
  for (file in descriptorFiles(module = module, loadPath = module.name + ".xml")) {
    val normalized = file.normalize()
    if (normalized.startsWith(communityRoot) != isCommunity || !normalized.startsWith(projectRoot)) {
      continue
    }
    val relativePath = projectRoot.relativize(normalized).invariantSeparatorsPathString
    val insidePackage = when {
      packageDirectory.isEmpty() -> relativePath
      relativePath.startsWith("$packageDirectory/") -> relativePath.removePrefix("$packageDirectory/")
      else -> continue
    }
    return moduleTarget.substringBeforeLast(':') + ":" + insidePackage
  }
  return null
}

/**
 * Whether the descriptor leaf derives the row [label] to [loadPath] itself, so the generator states nothing for it.
 *
 * The leaf derives one row for each of its [contentModules] that the bridge index knows, at the load path
 * `<module name>.xml`. [bridgeLabel] gives the index entry of a module name, see [bridgeDescriptorLabel]. A row of a
 * module outside [contentModules] stays explicit, because the leaf derives no row for it.
 */
internal fun isBridgeDerivedDescriptor(
  label: String,
  loadPath: String,
  contentModules: Collection<String>,
  bridgeLabel: (moduleName: String) -> String?,
): Boolean {
  val moduleName = loadPath.removeSuffix(".xml")
  return moduleName != loadPath && moduleName in contentModules && bridgeLabel(moduleName) == label
}

/**
 * The label that names one reached descriptor in the recorded form, or `null` when no label does.
 *
 * `exportDescriptorFiles` exports every XML under a production resource root from the owning module's own Bazel
 * package, so the label is that package plus the path inside it. That is the composition the macro already applies to
 * the plugin's own descriptor, and it is the reason the plan carries a label rather than a module name: no `.bzl`
 * exposes a module-to-package map over both repository halves, so Starlark cannot compose it.
 *
 * A file the module's own package does not hold falls back to [DevDistBazelIndex.containingPackageLabel], which asks the tree which
 * package holds it. That is the `dotenv-ultimate` and `php.dev` shape, where an ultimate module keeps its resources in
 * the community tree. Generation fails when neither rule answers.
 */
internal fun descriptorLabel(descriptor: ReachedDescriptor, index: DevDistBazelIndex): String? {
  if (descriptor.testOutput) {
    return testModuleJarTarget(module = descriptor.moduleName, targets = index.targets)
  }
  val moduleTarget = moduleRuleTarget(module = descriptor.moduleName, targets = index.targets) ?: return null
  val packageDirectory = index.packageDirectory(moduleTarget)
  if (descriptor.relativePath.startsWith("$packageDirectory/")) {
    return moduleTarget.substringBeforeLast(':') + ":" + descriptor.relativePath.removePrefix("$packageDirectory/")
  }
  return index.containingPackageLabel(descriptor.relativePath)
}

/**
 * Renders `dev_dist_product_info.bzl`: the product mode and the marketplace names of every product, and nothing about a
 * plugin.
 *
 * It states no value of the application info. The descriptor writer reads the EAP flag, the release date and the
 * release version from the sources of `DEV_DIST_APPLICATION_INFOS`, so an edit of an application info changes no
 * generated file.
 *
 * Products with equal values share one private struct, named after the first of them in [plans] order, the way
 * `dev_dist_plan.bzl` shares `_PLAN_<first product>`.
 */
internal fun renderProductInfo(plans: List<PluginDescriptorPlan>, half: DevDistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# The descriptor stamps of every product. `build/dev-dist-descriptors/BUILD.bazel` declares one\n")
  append("# `<product>_product_info` target per key. A consumer sets `@community//build:dev_dist_product_info` to it, and\n")
  append("# every plugin descriptor below that consumer reads its stamps there.\n")
  append("#\n")
  append("# A struct states the product mode and the marketplace names. The target also names the application info sources\n")
  append("# of `DEV_DIST_APPLICATION_INFOS`, and the descriptor writer reads the EAP flag, the release date and the release\n")
  append("# version from them. So an edit of an application info changes no generated file.\n")
  append("#\n")
  append("# Products with equal values share one private struct, named after the first of them.\n")
  append("\n")
  append("# `OsFamily.osId` and `JvmArchitecture.marketplaceName`, keyed by the token `HOST_PLATFORMS` spells. A leaf builds\n")
  append("# the marker row and the version suffix of a one-platform variant from these, and no rule can read an enum.\n")
  append("_MARKETPLACE_NAMES = {\n")
  for ((token, name) in MARKETPLACE_NAMES.toList().sortedBy { it.first }) {
    append("    \"").append(token).append("\": \"").append(name).append("\",\n")
  }
  append("}\n")
  val structNames = LinkedHashMap<ProductInfoValues, String>()
  for (plan in plans) {
    structNames.computeIfAbsent(ProductInfoValues(plan)) { "_STAMPS_${plan.platformPrefix}" }
  }
  for ((stamps, structName) in structNames) {
    append("\n")
    append(structName).append(" = struct(\n")
    append("    marketplace_names = _MARKETPLACE_NAMES,\n")
    append("    # The product mode. A rule places no jar of a module the leaf refuses for this mode.\n")
    append("    mode = \"").append(stamps.mode).append("\",\n")
    append(")\n")
  }
  append("\n")
  append("DEV_DIST_PRODUCT_INFO = {\n")
  for (plan in plans) {
    val structName = structNames.getValue(ProductInfoValues(plan))
    append("    \"").append(plan.platformPrefix).append("\": ").append(structName).append(",\n")
  }
  append("}\n")
}

/** The generated values of one `dev_dist_product_info`, so two products with equal values share one struct. */
private data class ProductInfoValues(val mode: String) {
  constructor(plan: PluginDescriptorPlan) : this(plan.mode)
}

/**
 * The key every deviation table is keyed by: the plugin, and its layout variant where it has one.
 *
 * A deviation is a fact about one (plugin, variant), not about the plugin: two variants of one plugin state different
 * markers, and the OS-specific ones state different versions. `dev_dist_plugin_descriptor.bzl` composes the same key.
 */
internal fun planEntryKey(entry: PluginDescriptorEntry): String = when {
  entry.variant.isEmpty() -> entry.mainModule
  else -> "${entry.mainModule}/${entry.variant}"
}

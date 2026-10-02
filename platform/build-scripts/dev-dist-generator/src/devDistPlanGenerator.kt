// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DestructuringDeclaration", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.buildScripts.pluginModelTool.devDistProductToken
import com.intellij.platform.buildScripts.pluginModelTool.distributionLibraryName
import com.intellij.platform.buildScripts.pluginModelTool.mergedLibraryNames
import com.intellij.platform.distributionContent.DevDistPlatformJars
import com.intellij.platform.pluginSystem.parser.impl.ContentParseResult
import com.intellij.platform.pluginSystem.parser.impl.LoadPathUtil
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import com.intellij.platform.productMode.ProductMode
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.PRESIGNED_NATIVE_LIBS
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.SignNativeFileMode
import org.jetbrains.intellij.build.buildSpan
import org.jetbrains.intellij.build.classPath.contentModuleJarCoreClasspathEntries
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.devDist.isNativeTreeAsset
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.intellij.build.impl.DevPlatformPatchOwner
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.createContentModuleFilter
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.createProductModeContentModuleFilter
import org.jetbrains.intellij.build.impl.getBundledPluginModules
import org.jetbrains.intellij.build.impl.getLibNameBySourceFile
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.impl.nameToJarFileName
import org.jetbrains.intellij.build.impl.nativeBinFiles
import org.jetbrains.intellij.build.impl.productInfo.ProductLaunchModel
import org.jetbrains.intellij.build.impl.productInfo.computeDevProductLaunchModel
import org.jetbrains.intellij.build.impl.productInfo.encodeProductLaunchModel
import org.jetbrains.intellij.build.impl.productInfo.vmOptionsFileName
import org.jetbrains.intellij.build.isTestModule
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.productLayout.JNA_NATIVE_DIR
import org.jetbrains.intellij.build.productLayout.JNA_PLUGIN_MODULE
import org.jetbrains.intellij.build.productLayout.PTY4J_NATIVE_DIR
import org.jetbrains.intellij.build.productLayout.PTY4J_PLUGIN_MODULE
import org.jetbrains.intellij.build.productLayout.SKIKO_NATIVE_DIR
import org.jetbrains.intellij.build.productLayout.COMPOSE_PLUGIN_MODULE
import org.jetbrains.intellij.build.productLayout.ProductContentBuildResult
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildProductContentXml
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.stats.DevDistPlanFileResult
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

/**
 * The file of `DEV_DIST_EXTRA_DESCRIPTOR_FILES`, the descriptors that a module reaches only through an `xi:include`.
 *
 * The shared project model tree carries every content module descriptor and every bundled plugin descriptor. Only
 * the runtime module repository action and the references read the tree. Bazel finds most descriptors by convention,
 * as `<moduleName>.xml` or `META-INF/plugin.xml` at a production resource root. The convention cannot predict the
 * name of an included file, so the generator lists those files here. The generator walks and validates the
 * `xi:include` closure, so an unresolvable include is a generation error.
 *
 * Each generated table has its own file, because the tables change at different rates. A payload-only or a
 * descriptor-only regeneration then leaves the partition file [DEV_DIST_PLAN_RELATIVE_PATH] unchanged. Each file is
 * also its own merge-conflict domain.
 */
private const val DEV_DIST_DESCRIPTORS_RELATIVE_PATH: String = "build/dev_dist_descriptors.bzl"
private const val DEV_DIST_PRODUCT_INFO_RELATIVE_PATH: String = "build/dev_dist_product_info.bzl"
private const val DEV_DIST_PLAN_RELATIVE_PATH: String = "build/dev_dist_plan.bzl"
private const val DEV_DIST_REFERENCE_PLAN_RELATIVE_PATH: String = "build/dev_dist_reference_plan.bzl"

/** The package of the launch models, whose `BUILD.bazel` exports every `<product>.launch.json`. */
private const val DEV_DIST_LAUNCH_DIRECTORY: String = "build/dev-dist-launch"

private const val LAUNCH_MODEL_SUFFIX: String = ".launch.json"

/**
 * Where the launch model of a product lives: the JSON the `platform_resources` component of the product renders.
 * [caseSafeName] is the case-safe name of the product, so two products never share a file on a case-insensitive disk.
 */
private fun launchModelRelativePath(caseSafeName: String): String = "$DEV_DIST_LAUNCH_DIRECTORY/$caseSafeName$LAUNCH_MODEL_SUFFIX"

/**
 * The `lib/` jar order of the platform as a rule with two lists. The core plugin lists the jars of [first] in that
 * order. Then it lists every other jar with a module, sorted by its smallest member module name. Then it lists the jars
 * of [last] in that order. `dev_dist_platform_jar_order.bzl` applies the rule. Bazel knows the member names, but not
 * the two lists.
 *
 * The runtime module repository states the entries of the core plugin in this order. The modular loader builds the main
 * class loader in the same order. Each name is a destination relative to `lib/`.
 */
internal data class PlatformJarOrder(@JvmField val first: List<String>, @JvmField val last: List<String>)

/**
 * The [PlatformJarOrder] of [product]. The true order is the order in which `JarPackager` creates the jars. It holds the
 * module jars in layout order, then the library-only jars in the order of [residualJars]. The key of a module jar is its
 * smallest member module name.
 */
private fun platformJarOrder(product: String, layout: PlatformLayout, residualJars: Map<String, ResidualPlatformJar>): PlatformJarOrder {
  val keys = LinkedHashMap<String, String>()
  for (item in layout.includedModules) {
    keys.merge(item.relativeOutputFile, item.moduleName) { old, new -> minOf(old, new) }
  }
  val libraryOnly = residualJars.filter { it.value.modules.isEmpty() && it.key !in keys }.keys.toList()
  return derivePlatformJarOrder(product = product, keys = keys.entries.map { it.key to it.value }, libraryOnly = libraryOnly)
}

/**
 * The two lists of the [PlatformJarOrder] for the true order [keys] then [libraryOnly]. [keys] are the module jars with
 * their sort keys, in true order. The keys are distinct, because a module packs into one jar.
 *
 * The longest strictly ascending run of keys is the sorted range, and the earliest run wins a tie. [PlatformJarOrder.first]
 * is the jars before the run. [PlatformJarOrder.last] is the jars after the run, then [libraryOnly]. The function fails
 * when the rule does not give the true order, and names [product] and the first jar that differs.
 */
internal fun derivePlatformJarOrder(product: String, keys: List<Pair<String, String>>, libraryOnly: List<String>): PlatformJarOrder {
  var runStart = 0
  var bestStart = 0
  var bestLength = 0
  for (index in keys.indices) {
    if (index > 0 && keys[index - 1].second >= keys[index].second) {
      runStart = index
    }
    val length = index - runStart + 1
    if (length > bestLength) {
      bestStart = runStart
      bestLength = length
    }
  }
  val jars = keys.map { it.first }
  val order = PlatformJarOrder(
    first = jars.subList(0, bestStart).toList(),
    last = jars.subList(bestStart + bestLength, jars.size) + libraryOnly,
  )
  // The production rule is the check: the Bazel side applies this rule, so the generator proves it on the true order.
  val named = HashSet(order.first + order.last)
  val derived = order.first + keys.filter { it.first !in named }.sortedBy { it.second }.map { it.first } + order.last
  val expected = jars + libraryOnly
  val difference = (0 until maxOf(derived.size, expected.size)).firstOrNull { derived.getOrNull(it) != expected.getOrNull(it) }
  if (difference != null) {
    error("$product: the platform jar rule places ${derived.getOrNull(difference)} at position $difference," +
          " but the layout places ${expected.getOrNull(difference)} there")
  }
  return order
}

private fun listLaunchModels(projectRoot: Path): List<String> {
  val directory = projectRoot.resolve(DEV_DIST_LAUNCH_DIRECTORY)
  if (!Files.isDirectory(directory)) {
    return emptyList()
  }
  return Files.newDirectoryStream(directory).use { stream ->
    stream.map { it.fileName.toString() }.filter { it.endsWith(LAUNCH_MODEL_SUFFIX) }.map { "$DEV_DIST_LAUNCH_DIRECTORY/$it" }.sorted()
  }
}
private const val DEV_DIST_FRAGMENT_INPUTS_RELATIVE_PATH: String = "build/dev_dist_fragment_inputs.bzl"
private const val DEV_DIST_MODULE_SETS_RELATIVE_PATH: String = "build/dev_dist_module_sets.bzl"
/**
 * The file of `DEV_DIST_PLUGIN_COMPONENTS`, the component label of every plugin. A component of its own packs each
 * plugin, so the platform payload tables name no plugin jar. The `dev_dist_complex_plugin` call of a complex plugin
 * sits beside the plugin, see [DevDistPluginPlanHome].
 */
private const val DEV_DIST_CONTENT_SETS_RELATIVE_PATH: String = "build/dev-dist-content/dev_dist_content_sets.bzl"
private const val DEV_SERVER_RUN_CONFIGURATIONS_RELATIVE_PATH: String = "build/dev_server_run_configurations.bzl"

/**
 * One descriptor file, as the Bazel side needs it: a project-relative path plus the JPS module whose Bazel
 * package exports it. The label cannot be computed here - that mapping lives in the JPS-to-Bazel converter - so
 * the plan carries the pair and Starlark turns it into a label.
 */
internal data class DescriptorFile(
  @JvmField val relativePath: String,
  @JvmField val moduleName: String,
) : Comparable<DescriptorFile> {
  override fun compareTo(other: DescriptorFile): Int {
    val byPath = relativePath.compareTo(other.relativePath)
    return if (byPath != 0) byPath else moduleName.compareTo(other.moduleName)
  }
}

/**
 * The dev-distribution plan files, and what the run did to each one.
 *
 * [files] always holds every plan file, the unchanged ones included, so the generation summary can state how many
 * files it covers. [diffs] is empty on a committing run, because a write is the requested outcome of that run and not
 * an issue to report.
 */
internal class DevDistPlanResult(
  @JvmField val files: List<DevDistPlanFileResult>,
  @JvmField val diffs: List<FileDiff>,
)

/**
 * The rendered dev-distribution plan, before the run decides to write it.
 *
 * The render reads the product model only, so it runs beside the generation pipeline. The write decision reads the
 * pipeline's errors, so [finish] takes it afterwards.
 */
internal class DevDistPlanCompute(
  private val updater: DeferredFileUpdater,
  private val files: List<DevDistPlanFileResult>,
  private val pluginPlans: DevDistPluginPlanUpdates? = null,
  /** The class of the product properties of every planned split product, keyed by the `dev-build.json` key. Empty for the dev sections. */
  @JvmField val productClasses: Map<String, String> = emptyMap(),
) {
  /**
   * Writes the rendered files when [commitChanges], and otherwise reports them as diffs.
   *
   * A validating run returns the diffs, so the caller can fold them into the generation result and let the
   * `model-generation` validation report staleness like any other generated artifact.
   */
  fun finish(commitChanges: Boolean): DevDistPlanResult {
    if (commitChanges) {
      pluginPlans?.commit()
      updater.commit()
      return DevDistPlanResult(files = files, diffs = emptyList())
    }
    return DevDistPlanResult(files = files, diffs = updater.getDiffs() + pluginPlans?.getDiffs().orEmpty())
  }
}

/**
 * Whether this run has the file the plan is derived from.
 *
 * The plan is derived from `bazel-targets.json`, which `jpsModelToBazel` generates and which is not checked in. A run
 * that never invoked the converter has nothing to derive the plan from. The caller decides what that means, because
 * the answer differs between a validating run and a committing one.
 */
internal fun devDistPlanInputExists(projectRoot: Path): Boolean {
  return Files.exists(ArchivedCompilationContextUtil.getBazelTargetsJsonPath(projectRoot))
}

/**
 * The plan files and the rendered calls of every complex plugin, see [computeDevDistPluginExecutions].
 *
 * [files] holds the plan files and the plan home of every complex plugin. [rendering] holds the calls keyed by main
 * module, and the two maps of `dev_dist_content_sets.bzl`.
 */
internal class DevDistPluginExecutions(
  @JvmField val files: DevDistPluginPlanFiles,
  @JvmField val rendering: DevDistPluginExecutionRendering,
)

/**
 * Collects the plan files of every complex plugin, renders its `dev_dist_complex_plugin` calls, and binds both to
 * [sections].
 *
 * The `dev` section of an ultimate complex plugin holds the calls, and the `dev` section of a community complex plugin
 * exports its plan files. So this runs after [DevDistBuildSections.fold] and before [writeDevDistBuildSectionFiles],
 * and [computeDevDistPlan] reads the result to write the cross-half calls and the two maps.
 *
 * [upstreamPackagePlans] are the plans of the community half, which the ultimate half passes. The first collection
 * homes every community plugin in its product package. A plugin whose plan files and calls equal the community ones there reuses the community targets, see
 * [DevDistOwnPackagePlans.acceptsUpstreamPlans]. The run then collects and renders once more with the community home.
 * [communityProducts] are the community products of the half, see [devDistCommunityProducts].
 */
internal fun computeDevDistPluginExecutions(
  sections: DevDistBuildSections,
  upstreamPackagePlans: DevDistOwnPackagePlans? = null,
  communityProducts: Set<String> = emptySet(),
): DevDistPluginExecutions {
  sections.requireDescriptorDeclarationsUnchanged()
  // A simple plugin has no plan file: its own section or its product package declares the packaging.
  val planFileRecords = sections.pluginPlanRecords.filterKeys { sections.simplePackaging(it.plugin) == null }
  val half = sections.half
  // Both collections read one set of texts, so the run encodes and folds every record once.
  val planTexts = buildSpan("plugin executions: encode and fold plan texts") {
    DevDistPluginPlanTexts.prepare(planFileRecords, half.splitProducts)
  }
  fun collect(reusedUpstream: Set<String> = emptySet()): DevDistPluginPlanFiles {
    return collectDevDistPluginPlanFiles(
      projectRoot = sections.index.projectRoot,
      records = planFileRecords,
      index = sections.index,
      half = half,
      productOrder = half.splitProducts,
      ownHome = { plugin, _ -> if (plugin in reusedUpstream) checkNotNull(upstreamPackagePlans).upstreamHome(plugin) else null },
      planTexts = planTexts,
    )
  }
  val firstFiles = buildSpan("plugin executions: collect plan files") { collect() }
  val firstRendering = buildSpan("plugin executions: render calls") { renderGeneratedDevDistPluginExecutions(sections, firstFiles, communityProducts = communityProducts) }
  var files = firstFiles
  var rendering = firstRendering
  if (upstreamPackagePlans != null) {
    val reused = buildSpan("plugin executions: decide upstream reuse") {
      // The plan files keyed by directory, then by file name, in path order.
      val filesByDirectory = HashMap<String, LinkedHashMap<String, String>>()
      for ((path, text) in firstFiles.files) {
        filesByDirectory.computeIfAbsent(path.substringBeforeLast('/', missingDelimiterValue = "")) { LinkedHashMap() }.put(path.substringAfterLast('/'), text)
      }
      firstFiles.homes.keys.filterTo(TreeSet()) { plugin ->
        val home = firstFiles.home(plugin)
        home.callIsCrossHalf && upstreamPackagePlans.acceptsUpstreamPlans(
          plugin,
          LinkedHashMap(filesByDirectory.get(home.directory).orEmpty()),
          firstRendering.calls.get(plugin)?.crossHalfText,
        )
      }
    }
    for (plugin in firstFiles.homes.keys) {
      when {
        plugin in reused -> println("reused the plan files and the calls of $plugin in its community package")
        upstreamPackagePlans.hasHome(plugin) -> println("kept the plan files of $plugin in its product package: the community half states other plan texts or calls")
      }
    }
    if (reused.isNotEmpty()) {
      val reusedFiles = buildSpan("plugin executions: collect reused plan files") { collect(reusedUpstream = reused) }
      files = reusedFiles
      rendering = buildSpan("plugin executions: render reused calls") { renderGeneratedDevDistPluginExecutions(sections, reusedFiles, communityProducts = communityProducts) }
      for (plugin in reused) {
        val calls = rendering.calls.getValue(plugin)
        check(calls.sectionText != null && upstreamPackagePlans.acceptsCalls(plugin, calls)) {
          "The ${sections.half.name} half renders another call of $plugin in its community package than the community half:\n" +
          calls.sectionText + "\nThe community half states:\n" + upstreamPackagePlans.sectionCalls(plugin)
        }
      }
    }
  }
  val boundFiles = files
  val boundRendering = rendering
  buildSpan("plugin executions: bind") { sections.bindPluginExecutions(boundRendering, boundFiles) }
  return DevDistPluginExecutions(files = boundFiles, rendering = boundRendering)
}

/**
 * Renders every dev-distribution file into a deferred writer, and writes nothing.
 *
 * The result holds every plan file, the unchanged ones included, so the generation summary can state how many files it
 * covers. [walk] is the one descriptor walk of the run, which the dev sections read first. [sections] gives the plan
 * every `content_module_jar` label, every plugin content target and every descriptor target, and the plan entries the
 * sections read. So the plan and the dev sections state one label per target and build one entry per plugin.
 * [executions] holds the plan files and the calls of every complex plugin. The calls of a community plugin go into its
 * product package here. [targets] is the JSON the module and library labels still come from.
 *
 * [half] is the half the run writes, and every path is relative to its root, the project root of the index of
 * [sections]. A half writes the reference plan, the platform patches and the embedded descriptor actions only when it has
 * the capability, see [requireHalfCapabilities]. A half writes only into its own packages, see [DevDistHalf.ownsPackage].
 *
 * [communityProducts] are the community products of this half, see [devDistCommunityProducts]. The half collects no
 * fragment plan of them, so it writes no row of them in the plan, the reference files, the fragment inputs, the module
 * sets, the product info and the product descriptor package, and no launch model. Empty for the community half.
 */
internal fun computeDevDistPlan(
  half: DevDistHalf,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  walk: DescriptorWalk,
  sections: DevDistBuildSections,
  executions: DevDistPluginExecutions,
  targets: BazelTargetsInfo.TargetsFile,
  runConfigurationRows: List<DevRunConfigurationRow>,
  communityProducts: Set<String> = emptySet(),
): DevDistPlanCompute {
  sections.requireDescriptorDeclarationsUnchanged()
  val pluginPlans = executions.files
  val pluginExecutions = executions.rendering
  val crossHalfPluginTargets = renderCrossHalfDevPluginTargets(sections)
  val crossHalfPluginCalls = TreeMap<String, String>()
  for (call in pluginExecutions.calls.values) {
    val text = call.crossHalfText ?: continue
    check(crossHalfPluginCalls.put(call.crossHalfPath, text) == null) { "Two complex plugins render into '${call.crossHalfPath}'" }
  }
  val verdicts = sections.verdicts
  val index = sections.index
  val projectRoot = index.projectRoot
  val splitProducts = half.registrySplitProducts(products.map { it.name })
  val runConfigurations = runConfigurationRows
  val collected = buildSpan("dev-distribution plan: collect descriptor files") {
    collectDescriptorFiles(
      half = half,
      index = index,
      outputProvider = outputProvider,
      products = products,
      walk = walk,
      verdicts = verdicts,
      pluginDescriptorPlans = sections.descriptorPlans,
      pluginRequests = sections.pluginRequests,
      platformTable = sections.platformJars,
      targets = targets,
      runtimeModuleRepositoryProducts = devDistRuntimeModuleRepositoryProducts(runConfigurations, splitProducts),
      communityProducts = communityProducts,
    )
  }
  val sortedProducts = collected.products.sortedBy(ProductFragmentPlan::platformPrefix)
  for (product in communityProducts) {
    println("community product $product: the community half plans it, so the ${half.name} half writes no row of it")
  }
  requireHalfCapabilities(
    half = half,
    projectRoot = projectRoot,
    registryProducts = products.map { it.name },
    plannedProducts = sortedProducts.map(ProductFragmentPlan::platformPrefix) +
                      collected.pluginDescriptorPlans.map(PluginDescriptorPlan::platformPrefix),
    embeddingProducts = collected.pluginDescriptorPlans
      .filter { plan -> plan.plugins.any { it.embeddedProductDescriptor != null } }
      .map(PluginDescriptorPlan::platformPrefix),
    hasPlatformPatches = collected.platformPatches?.isEmpty == false,
    runtimeModuleRepositoryProducts = sortedProducts
      .filter(ProductFragmentPlan::runtimeModuleRepository)
      .map(ProductFragmentPlan::platformPrefix),
  )
  // The generation pipeline's own writer. It reports a write the same way the rest of the pipeline does.
  val updater = DeferredFileUpdater(projectRoot)
  // The descriptor leaf of every plugin whose own package cannot declare one, in the same table as the central files,
  // so the `model-generation` validation diff-gates a stale package exactly as it gates a stale `.bzl`.
  val crossHalfDescriptorPackages = sections.crossHalfDescriptorPackages
  val productDescriptorFiles = renderProductDescriptorPackage(sortedProducts.mapNotNull(ProductFragmentPlan::productDescriptor).distinct(), index, half)
  val relocatedContentModuleJarPackage = renderRelocatedContentModuleJarPackage(sections.relocatedContentModuleJarCalls, half)
  val relocatedContentModuleJarPackagePath = "$DEV_DIST_CONTENT_MODULE_JARS_PACKAGE/BUILD.bazel"
  // The file name of a launch model is the case-safe name of the key, which both halves state alike, see
  // [checkSplitDistributionsExtend].
  val productClasses = devDistProductClasses(products)
  val plannedProductClasses = sortedProducts.associateTo(TreeMap()) { it.platformPrefix to productClasses.get(it.platformPrefix).orEmpty() }
  val fileContents = buildSpan("dev-distribution plan: render files") { renderSpan ->
    val contents = buildList {
      add(DEV_DIST_DESCRIPTORS_RELATIVE_PATH to renderDescriptors(collected.files, half))
      add(DEV_DIST_PRODUCT_INFO_RELATIVE_PATH to renderProductInfo(collected.pluginDescriptorPlans.filter { it.platformPrefix !in communityProducts }, half))
      add(DEV_DIST_PLAN_RELATIVE_PATH to renderPartition(sortedProducts, half, communityProducts))
      if (DevDistCapability.REFERENCE_PLAN in half.capabilities) {
        add(DEV_DIST_REFERENCE_PLAN_RELATIVE_PATH to renderReferencePlan(sortedProducts, half))
        val referenceInputs = buildSpan("dev-distribution plan: resolve reference inputs") {
          resolveDevDistReferenceInputs(
            products = sortedProducts.map(::referenceProduct),
            moduleSets = collected.moduleSets.associateBy(ModuleSetData::name),
            model = referenceInputModel(targets, outputProvider),
          )
        }
        add(DEV_DIST_REFERENCE_INPUTS_RELATIVE_PATH to renderDevDistReferenceInputs(referenceInputs, half.generatedByHeader))
      }
      add(DEV_DIST_FRAGMENT_INPUTS_RELATIVE_PATH to renderFragmentInputs(sortedProducts, half))
      add(DEV_DIST_MODULE_SETS_RELATIVE_PATH to renderModuleSets(collected.moduleSets, half))
      add(DEV_DIST_CONTENT_SETS_RELATIVE_PATH to renderContentSets(pluginExecutions, half))
      add(DEV_SERVER_RUN_CONFIGURATIONS_RELATIVE_PATH to renderDevServerRunConfigurations(runConfigurations, splitProducts, half.macrosBzl, half.refusedRowProperties, half.generatedByHeader))
      addAll(crossHalfDescriptorPackages.files(crossHalfPluginTargets, crossHalfPluginCalls).toList())
      productDescriptorFiles.entries.mapTo(this) { it.key to it.value }
      collected.platformPatches?.renderPackage()?.entries?.mapTo(this) { it.key to it.value }
      relocatedContentModuleJarPackage?.let { add(relocatedContentModuleJarPackagePath to it) }
      for (product in sortedProducts) {
        add(product.launchModelRelativePath to encodeProductLaunchModel(product.launchModel))
      }
    }
    renderSpan.setAttribute("files", contents.size.toLong())
    contents
  }
  // A half writes only into its own packages, see `DevDistHalf.ownsPackage`.
  val files = buildSpan("dev-distribution plan: compare files") {
    fileContents.map { (relativePath, newContent) ->
      half.requireWritable(relativePath)
      DevDistPlanFileResult(
        relativePath = relativePath,
        status = updater.updateIfChanged(path = projectRoot.resolve(relativePath), newContent = newContent),
      )
    }
  }
  pluginPlans.updates.results.forEach { half.requireWritable(it.relativePath) }
  buildSpan("dev-distribution plan: sweep stale files") {
    // The run deletes every descriptor package on disk that it does not write, see `CrossHalfDescriptorPackages.stale`.
    val stalePackages = crossHalfDescriptorPackages.stale(projectRoot, crossHalfPluginTargets, crossHalfPluginCalls)
    for (relativePath in stalePackages) {
      updater.delete(projectRoot.resolve(relativePath))
    }
    // A run without a relocated call leaves no package of relocated calls behind. Only the ultimate half writes one.
    if (relocatedContentModuleJarPackage == null && !half.writesCommunityPackages && Files.exists(projectRoot.resolve(relocatedContentModuleJarPackagePath))) {
      updater.delete(projectRoot.resolve(relocatedContentModuleJarPackagePath))
    }
    // A product that leaves the split path, or that the community half now plans, leaves its launch model behind.
    val launchModelPaths = sortedProducts.mapTo(HashSet()) { it.launchModelRelativePath }
    for (relativePath in listLaunchModels(projectRoot)) {
      if (relativePath !in launchModelPaths) {
        updater.delete(projectRoot.resolve(relativePath))
      }
    }
    // The descriptor actions compose the content from the module-set table, so an embedded descriptor file is stale.
    half.embeddedFrontend?.let { embeddedFrontend ->
      for (relativePath in embeddedFrontend.staleDescriptors(projectRoot)) {
        updater.delete(projectRoot.resolve(relativePath))
      }
    }
    // The product descriptor actions compose the content from the module-set table, so a product content file is stale.
    for (relativePath in staleProductDescriptorSources(projectRoot)) {
      updater.delete(projectRoot.resolve(relativePath))
    }
  }
  return DevDistPlanCompute(
    updater = updater,
    files = files + pluginPlans.updates.results,
    pluginPlans = pluginPlans.updates,
    productClasses = plannedProductClasses,
  )
}

/**
 * Fails when a half would plan what it cannot: a product outside its registry, or an embedded descriptor, a platform
 * patch or a runtime module repository without the capability, see [DevDistHalf.capabilities].
 * [registryProducts] are the keys of `build/dev-build.json` under [projectRoot], the root of [half], so no generated file of
 * the community half names a product of another registry.
 */
internal fun requireHalfCapabilities(
  half: DevDistHalf,
  projectRoot: Path,
  registryProducts: Collection<String>,
  plannedProducts: Collection<String>,
  /** The products that plan an embedded descriptor. */
  embeddingProducts: Collection<String>,
  hasPlatformPatches: Boolean,
  runtimeModuleRepositoryProducts: Collection<String>,
) {
  val registry = registryProducts.toHashSet()
  val foreign = plannedProducts.filterNot { it in registry }.distinct().sorted()
  check(foreign.isEmpty()) {
    "The ${half.name} half renders products outside ${projectRoot.resolve(PRODUCT_REGISTRY_RELATIVE_PATH)}: $foreign"
  }
  val capabilities = half.capabilities
  check(DevDistCapability.EMBEDDED_FRONTENDS in capabilities || embeddingProducts.isEmpty()) {
    "The ${half.name} half cannot plan an embedded descriptor: ${embeddingProducts.sorted()}"
  }
  check(DevDistCapability.PLATFORM_PATCHES in capabilities || !hasPlatformPatches) { "The ${half.name} half cannot write a platform patch" }
  check(DevDistCapability.RUNTIME_MODULE_REPOSITORY in capabilities || runtimeModuleRepositoryProducts.isEmpty()) {
    "The ${half.name} half cannot write a runtime module repository, and these products ask for one: " +
    runtimeModuleRepositoryProducts.sorted()
  }
}

/** The product registry of a repository half, relative to the root of the half. */
private const val PRODUCT_REGISTRY_RELATIVE_PATH: String = "build/dev-build.json"

/**
 * The rendered `dev_plugin` target of every simple plugin a generated package packs, keyed by the package path.
 *
 * A cross-half simple plugin gets one in its plugin package, over the baseline leaf. A product that reads another leaf
 * or packs the plugin differently gets one in the product package [DevDistBuildSections.devPluginPackageProduct] names,
 * over the leaf of its class and with its packaging, whether or not the plugin is cross-half. Products that share that
 * package pack the plugin alike, so the first one renders the target for all. The descriptor label is the one the
 * product's neutral plan reads, so the packaging and the plan name one descriptor.
 */
private fun renderCrossHalfDevPluginTargets(sections: DevDistBuildSections): Map<String, String> {
  val plans = sections.descriptorPlans.associateBy(PluginDescriptorPlan::platformPrefix)
  val result = TreeMap<String, String>()
  for (entry in sections.pluginPlanEntries) {
    val packaging = sections.simplePackaging(entry.mainModule, entry.product) ?: continue
    val path = when {
      sections.readsProductPackage(entry.mainModule, entry.product) -> {
        crossHalfPackagePath(entry.mainModule, product = sections.devPluginPackageProduct(entry.mainModule, entry.product))
      }
      packaging.crossHalf -> crossHalfPackagePath(entry.mainModule, product = null)
      else -> continue
    }
    if (result.containsKey(path)) continue
    val descriptorPlan = plans.getValue(entry.product)
    val descriptorEntry = descriptorPlan.plugins.single { it.mainModule == entry.mainModule && it.variant.isEmpty() }
    val descriptorLabel = sections.descriptorDeclaration(descriptorPlan, descriptorEntry).label
    result.put(path, renderCrossHalfDevPluginTarget(packaging, descriptorLabel, sections.index, sections::contentModuleJarLabel))
  }
  return result
}

/**
 * Checks the presigned native trees of the plugin plans in [entries].
 *
 * A native tree sits in the `lib/` directory of the plugin that owns its natives jar. The build scripts refused two
 * owners of one tree when the tree was at the distribution root. A tree below the plugin directory loses that check, so
 * this function refuses a tree that two plugins of one product pack. It also requires that the launcher path of the JNA,
 * pty4j and Skiko trees is the path the plan gives: `plugins/<plugin directory>/<tree destination>`.
 */
internal fun checkPluginNativeTrees(entries: Collection<DevDistPluginPlanEntry>) {
  val launcherDirs = mapOf(
    JNA_PLUGIN_MODULE to JNA_NATIVE_DIR,
    PTY4J_PLUGIN_MODULE to PTY4J_NATIVE_DIR,
    COMPOSE_PLUGIN_MODULE to SKIKO_NATIVE_DIR,
  )
  val owners = HashMap<Pair<String, String>, MutableSet<String>>()
  for (entry in entries) {
    for ((variant, record) in entry.records) {
      val layout = entry.layout(variant)
      val trees = record.plan.projection.assets.filter(::isNativeTreeAsset)
      for (tree in trees) {
        owners.computeIfAbsent(entry.product to tree.destination) { LinkedHashSet() }.add(entry.mainModule)
      }
      val launcherDir = launcherDirs.get(entry.mainModule) ?: continue
      val planDirs = trees.map { "plugins/${layout.directoryName}/${it.destination}" }
      check(planDirs == listOf(launcherDir)) {
        "${entry.product}: the launcher reads the native tree of '${entry.mainModule}' at '$launcherDir', " +
        "but the plan of variant '$variant' places the native trees at $planDirs"
      }
    }
  }
  val shared = owners.filterValues { it.size > 1 }
  check(shared.isEmpty()) {
    "Two or more plugins of one product pack the same presigned native tree: " +
    shared.entries.joinToString { (key, plugins) -> "${key.first}/${key.second} in $plugins" }
  }
}

/** The fragment owning `lib/`, minus the jars the per-module packer produces. */
private const val PLATFORM_LIB_FRAGMENT = "platform_lib"

/**
 * The component that places `modules/module-descriptors.{dat,jar}`, which the `dev_dist_runtime_module_repository`
 * action writes. The plan emits it for a product with a run configuration that asks for the runtime module repository,
 * and only such a row composes it.
 */
private const val PLATFORM_RUNTIME_MODULE_REPOSITORY_FRAGMENT = "platform_runtime_module_repository"

/** The exact module and library payload one fragment may resolve, expressed as JPS names rather than Bazel labels. */
private data class FragmentPayload(
  @JvmField val name: String,
  @JvmField val modules: List<String>,
  @JvmField val projectLibraries: List<String>,
  /** Module sets whose transitive membership belongs to this payload, as top-level references. */
  @JvmField val moduleSets: List<String> = emptyList(),
  /**
   * The modules whose runtime classpath a platform patch loads. The Bazel side declares the outputs and the libraries
   * of their dependency closure unconditionally: the classpath resolves a module's raw jar even when the payload
   * hands the module's packed jar over to another producer.
   */
  @JvmField val runtimeClasspathModules: List<String> = emptyList(),
  /**
   * The `content_module_jar` target of each payload module that owns a `lib/` jar - labels, not names, and only for the
   * `lib/`-owning payload.
   *
   * The one thing about this payload that Bazel cannot ask the graph. Packing is a target of its own now, and a
   * repository rule can neither see a provider nor test whether a target exists, so the payload's own modules cannot be
   * asked which of them pack - the question `dev_dist_platform_payload` used to answer with an aspect over an output
   * group. The generator is the only side that knows, and `bazel-targets.json` already records the label per module.
   *
   * This is not the name table that [ADR 0001][] deleted. That table had 2 524 module names in a separate file.
   * Each `.iml` edit re-keyed that table. Each label here selects an existing packing target for that payload.
   *
   * [collectFragmentPlan] fills the whole handover set. [collectDescriptorFiles] then keeps only the labels no
   * referenced module set carries, see [sharePackedLabels]: a set member's label lives in [ModuleSetData.packed], and
   * the binder unions the two halves back at load time with `dev_dist_packed_labels`.
   */
  @JvmField val packedContentModuleJars: List<String> = emptyList(),
  @JvmField val residualJars: Map<String, ResidualPlatformJar> = emptyMap(),
  /**
   * The label of the `plugins/plugin-classpath.txt` prefix that the product descriptor action writes, see
   * [ProductDescriptorPlan]. Only the payload that owns `lib/` states it.
   */
  @JvmField val pluginClasspathPrefix: String? = null,
  /**
   * The `content_module_jar` labels of the packed jars that the module system loads, sorted. Only the `lib/`-owning
   * payload states them.
   *
   * `dev_dist_platform_payload` puts every other packed jar that is a direct child of `lib/` on the core classpath.
   * [collectFragmentPlan] fills the whole list. [collectDescriptorFiles] then keeps only the labels that no referenced
   * module set carries, see [shareModuleSystemLoadedLabels].
   */
  @JvmField val moduleSystemLoaded: List<String> = emptyList(),
)

/**
 * One `lib/` jar the `platform_lib` fragment hands to a `dev_dist_platform_jar` target: the member outputs and the
 * merged libraries as labels. A jar with a module member holds no native file. A library-only jar has no [modules]
 * and keeps the native files of its libraries, as `JarPackager` does.
 *
 * The application-info module jar also states [patches]: the product descriptor and the stamped application info,
 * keyed by label and valued by the entry path. They replace entries of the output of [patchedModule].
 */
@ApiStatus.Internal
class ResidualPlatformJar(
  @JvmField val modules: List<String>,
  @JvmField val libraries: List<String>,
  @JvmField val patches: Map<String, String> = emptyMap(),
  @JvmField val patchedModule: String? = null,
)

/**
 * One module set as the Bazel side needs it: the modules it declares itself, the sets it nests, and the packing label
 * of each member that owns a `content_module_jar` target.
 *
 * A product references a handful of top-level sets; naming the sets instead of flattening their transitive
 * membership into every product's payload is what keeps this data shared. The membership of a set is the same
 * whichever product references it - a set is a DSL object, not a per-product view of one - so this table is
 * repo-global and the payloads carry only what no set covers.
 *
 * [packed] is keyed by member name. The label is a fact about the module, not about the product that reaches it, so
 * every product that references the set hands it over, less the members its mode refuses, see [sharePackedLabels].
 *
 * [moduleSystemLoaded] names the [packed] members whose jar the module system loads in every product that reaches the
 * set, sorted. A product can override the loading of a member, so the list comes from the product verdicts, see
 * [moduleSetModuleSystemLoaded].
 *
 * [modeRefused] maps a product mode id to the sorted [packed] members that the mode refuses. The layout of a product of
 * that mode places no jar of them. The refusal is a fact about the module dependencies, so the table states it once.
 */
internal data class ModuleSetData(
  @JvmField val name: String,
  /** The own members, in DSL order. */
  @JvmField val modules: List<String>,
  /** The nested sets, in DSL order. */
  @JvmField val nested: List<String>,
  @JvmField val packed: Map<String, String> = emptyMap(),
  @JvmField val moduleSystemLoaded: List<String> = emptyList(),
  @JvmField val modeRefused: Map<String, List<String>> = emptyMap(),
  /** The loading rule of each own member with a non-default rule, as the descriptor states it, in member order. */
  @JvmField val loading: Map<String, String> = emptyMap(),
  /** The `required-if-available` module of each own member that states one, in member order. */
  @JvmField val requiredIfAvailable: Map<String, String> = emptyMap(),
  /** The plugin alias of the set, or `null` when it declares none. */
  @JvmField val alias: String? = null,
)

private data class ProductFragmentPlan(
  @JvmField val platformPrefix: String,
  /** The id of the product mode. The payload hands over no module set member that this mode refuses. */
  @JvmField val productMode: String,
  @JvmField val buildModules: List<String>,
  /**
   * Whether the product has the runtime module repository component: a row asks for it, or the product loads the
   * modular loader. Only a row with the runtime module repository composes it, unless [modularLoader] is set.
   */
  @JvmField val runtimeModuleRepository: Boolean,
  /**
   * The `build/dev-build.json` key of the split product whose platform the embedded frontend is, or `null` when the
   * product embeds none or has no [runtimeModuleRepository] component. The runtime module repository component lays
   * the frontend's platform and its own bundled plugins out too, so the Bazel side takes that product's `platform_lib`
   * declaration and the components of its frontend-only plugins beside the product's own.
   */
  @JvmField val embeddedFrontend: String?,
  /**
   * Whether the product starts through the modular loader (`ProductProperties.rootModuleForModularLoader`). Such a
   * product reads `modules/module-descriptors.jar` at every start, so every row composes the [runtimeModuleRepository] component.
   */
  @JvmField val modularLoader: Boolean,
  @JvmField val payloads: List<FragmentPayload>,
  @JvmField val platformAssets: PlatformAssets,
  /** What the `platform_resources` component renders. */
  @JvmField val launchModel: ProductLaunchModel,
  /** Where [launchModel] lives, under the case-safe name of the product. */
  @JvmField val launchModelRelativePath: String,
  /** The label of the base `idea.properties` of [launchModel], see [DevDistHalf.baseIdeaProperties]. */
  @JvmField val ideaProperties: String,
  /** The application info sources that the `platform_resources` component reads beside [launchModel]. */
  @JvmField val applicationInfoSources: ApplicationInfoSources,
  /** The `lib/` jar order of the platform. `null` for a product without the [runtimeModuleRepository] component. */
  @JvmField val platformJarOrder: PlatformJarOrder? = null,
  /** The application-info module, which holds the descriptor of the core plugin of the runtime module repository. */
  @JvmField val applicationInfoModule: String = "",
  /**
   * The actions that write the product descriptor and the stamped application info, or `null` when a platform patch
   * keeps the application-info module jar with the `platform_lib` fragment.
   */
  @JvmField val productDescriptor: ProductDescriptorPlan? = null,
)

/**
 * The application info sources of a product, as labels. The product files action derives the names, the version, the
 * suffix, the icon, the vendor and the release date from them.
 */
private data class ApplicationInfoSources(
  /** The label of `idea/<prefix>ApplicationInfo.xml` of the product. */
  @JvmField val source: String,
  /** The label of the application info of the host product of a frontend, or `null` when the product has no host. */
  @JvmField val host: String?,
  /** `ProductProperties.appInfoXmlReplacements` as `<key>=<value>`, in their order. */
  @JvmField val replacements: List<String>,
)

/** One file of an unpacked dev-launch archive that the `platform_assets` component places at [path]. */
internal data class PlatformAssetFile(
  @JvmField val source: String,
  @JvmField val path: String,
  @JvmField val executable: Boolean,
)

/**
 * The platform's declared dist files of one product, as the `platform_assets` component collects them.
 * [files] is keyed by host platform. [archives] names the archives the declarations read, so a fragment does not
 * download what the component already unpacks.
 */
internal data class PlatformAssets(
  @JvmField val files: Map<String, List<PlatformAssetFile>>,
  @JvmField val archives: List<String>,
)

private class CollectedPlan(
  @JvmField val files: List<DescriptorFile>,
  @JvmField val pluginDescriptorPlans: List<PluginDescriptorPlan>,
  @JvmField val products: List<ProductFragmentPlan>,
  @JvmField val moduleSets: List<ModuleSetData>,
  /** The platform patch targets of the run, or `null` for a half without platform patches. */
  @JvmField val platformPatches: DevDistPlatformPatchTargets?,
)

/**
 * The descriptor plan of every split product over [walk], sorted by platform prefix.
 *
 * After the flat walk, because the per-plugin partition needs the flat walk's resolutions, so that both walks credit
 * one module with a load path. The dev sections read these entries first, and the plan reads the same entries after
 * them. [index] gives the module and library labels and the root of the half. A plugin the plan cannot state stops the run.
 *
 * Each entry states the refusals of every mode of [DEV_DIST_STATED_PRODUCT_MODES], and not only of the modes of the
 * split products of [half]. So both halves render one leaf of a community plugin.
 */
internal fun collectPluginDescriptorPlans(
  walk: DescriptorWalk,
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  requestLayoutsByProduct: Map<String, List<PluginLayout>>,
  testPluginsByProduct: Map<String, Map<String, TestPluginSpec>> = emptyMap(),
  half: DevDistHalf,
  embeddedClasses: DevDistEmbeddedFrontendClasses,
): List<PluginDescriptorPlan> {
  val splitModes = products
    .filter { it.name in half.splitDistributions }
    .mapNotNull { product -> (product.properties as? ProductProperties)?.productMode?.let { product.name to it } }
  val modesById = (splitModes.map { it.second } + DEV_DIST_STATED_PRODUCT_MODES).associateBy { it.id }
  val refusingModes = devDistRefusingModeIds(splitModes.map { (product, mode) -> product to mode.id }).map(modesById::getValue)
  // The products plan beside each other. The collector memos take concurrent callers, and the closure walks read the
  // finished flat walk.
  val plans = products.mapConcurrent { product ->
    // A product with no split dev distribution has no plan.
    if (product.name !in half.splitDistributions) return@mapConcurrent null
    val properties = product.properties as? ProductProperties ?: return@mapConcurrent null
    collectPluginDescriptorPlan(
      index = index,
      outputProvider = outputProvider,
      properties = properties,
      platformPrefix = product.name,
      bazelTargets = index.targets,
      layouts = requireNotNull(requestLayoutsByProduct.get(product.name)) {
        "Split product '${product.name}' has no dev-plugin request layouts"
      },
      testPluginsByMainModule = testPluginsByProduct.get(product.name).orEmpty(),
      closureOf = { mainModule, embedsContentModules, testPlugin, rootLoadPath, isContentModuleIncluded ->
        walk.collector.collectPluginClosure(
          mainModule = mainModule,
          embedsContentModules = embedsContentModules,
          testPlugin = testPlugin,
          rootLoadPath = rootLoadPath,
          isContentModuleIncluded = isContentModuleIncluded,
        )
      },
      generatedClosureOf = { mainModule, rootLoadPath, sourceRelativePath, xml, isContentModuleIncluded ->
        walk.collector.collectGeneratedPluginClosure(
          mainModule = mainModule,
          rootLoadPath = rootLoadPath,
          sourceRelativePath = sourceRelativePath,
          xml = xml,
          isContentModuleIncluded = isContentModuleIncluded,
        )
      },
      half = half,
      embeddedClasses = embeddedClasses,
      refusingModes = refusingModes,
    )
  }.filterNotNull()
  checkEmbeddedDescriptorClassesPlanAlike(plans)
  return plans.sortedBy(PluginDescriptorPlan::platformPrefix)
}

/**
 * The modes other than the monolith that a split product of either half uses, sorted by id. Every descriptor entry
 * states the refusals of each of them. A split product of another mode stops the run, so the list grows with the
 * products.
 */
internal val DEV_DIST_STATED_PRODUCT_MODES: List<ProductMode> = listOf(ProductMode.FRONTEND)

/**
 * The ids of the modes whose refusals every descriptor entry states: [DEV_DIST_STATED_PRODUCT_MODES], sorted.
 * [splitModes] are the split products of the run with the ids of their modes.
 *
 * Both halves state the same refusals, so the entries of one plugin are equal across the products and the halves, and
 * one leaf serves them all. A split product of a mode outside that list stops the run.
 */
internal fun devDistRefusingModeIds(splitModes: List<Pair<String, String>>): List<String> {
  val monolith = ProductMode.MONOLITH.id
  val stated = DEV_DIST_STATED_PRODUCT_MODES.map { it.id }
  val unstated = splitModes.filter { (_, mode) -> mode != monolith && mode !in stated }
  check(unstated.isEmpty()) {
    "The split products ${unstated.map { it.first }} use a mode that DEV_DIST_STATED_PRODUCT_MODES does not state: ${unstated.map { it.second }.distinct()}"
  }
  return stated.sorted()
}

/**
 * Fails when a product that embeds the frontend plans another embedded descriptor action than the home of its class. The
 * home declares the one action of the class, so the other products must state the same inputs. The application info
 * stays per product.
 */
private fun checkEmbeddedDescriptorClassesPlanAlike(plans: List<PluginDescriptorPlan>) {
  val embeddedByProduct = plans.associate { plan ->
    plan.platformPrefix to plan.plugins.firstNotNullOfOrNull { it.embeddedProductDescriptor }?.copy(frontendApplicationInfo = null)
  }
  for ((product, embedded) in embeddedByProduct) {
    if (embedded == null || embedded.home == product) {
      continue
    }
    val home = embeddedByProduct.get(embedded.home)
    check(home == embedded) {
      "The embedded descriptor of '$product' is not the one of its home '${embedded.home}': $embedded != $home." +
      " A product of the class reads the action of its home, so both must plan the same inputs"
    }
  }
}

/**
 * The flat walk over every product: [collector] after the walk, and the content of each product it read, by product index.
 *
 * One walk serves the dev sections and the plan. The run walks once, in [walkDescriptors], and gives the result to both.
 */
internal class DescriptorWalk(
  @JvmField val collector: DescriptorCollector,
  @JvmField val contentByProduct: List<ProductContentBuildResult?>,
)

/**
 * The flat walk: every content module and root descriptor of every product, and every bundled plugin's `plugin.xml`.
 * [generatedModuleSetDescriptors] are the module-set descriptor directories of the half, see
 * [DevDistHalf.generatedModuleSetDescriptors].
 */
internal fun walkDescriptors(
  projectRoot: Path,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  generatedModuleSetDescriptors: Map<String, String>,
): DescriptorWalk {
  val collector = DescriptorCollector(projectRoot = projectRoot, outputProvider = outputProvider)
  for ((relativeRoot, moduleName) in generatedModuleSetDescriptors) {
    collector.collectGeneratedModuleSetDescriptors(relativeRoot = relativeRoot, moduleName = moduleName)
  }

  // First the scope, then the walk. An `xi:include` in a product's root descriptor can name a file another module owns
  // (for example `/META-INF/DesignerCorePlugin.xml` from `intellij.uiDesigner`), and the layout
  // resolves it by searching the platform's whole module list - see `XIncludeElementResolverImpl.resolveElement`. So
  // the collector needs that list before it starts following includes.
  // The products that drop each unresolved content module, for the census below.
  val droppedModules = TreeMap<String, MutableList<String>>()
  val builtContent = buildSpan("walk descriptors: product content") {
    products.mapConcurrent { product ->
      val spec = product.spec ?: return@mapConcurrent null
      val content = buildProductContentXml(
        spec = spec,
        outputProvider = null,
        inlineXmlIncludes = false,
        inlineModuleSets = true,
        metadataBuilder = {},
      )
      content to unresolvedContentModules(product = product, content = content, outputProvider = outputProvider)
    }
  }
  val contentByProduct = products.mapIndexed { index, product ->
    val (content, dropped) = builtContent.get(index) ?: return@mapIndexed null
    for (module in dropped) {
      droppedModules.computeIfAbsent(module) { ArrayList() }.add(product.name)
    }
    if (dropped.isEmpty()) content else content.withoutModules(dropped)
  }
  // The bundled plugins of every product with content, and the extra plugins of its embedded frontend.
  val pluginModulesByProduct = buildSpan("walk descriptors: bundled plugins") {
    products.withIndex().toList().mapConcurrent { (index, product) ->
      if (contentByProduct.get(index) == null) return@mapConcurrent null
      val properties = product.properties as? ProductProperties ?: return@mapConcurrent null
      val bundledPluginModules = getBundledPluginModules(properties, outputProvider)
      val frontendProperties = properties.embeddedFrontendProperties?.invoke()
      val frontendOnlyPluginModules = if (frontendProperties == null) {
        emptyList()
      }
      else {
        val bundled = bundledPluginModules.toHashSet()
        getBundledPluginModules(frontendProperties, outputProvider).filterNot { it in bundled }
      }
      bundledPluginModules to frontendOnlyPluginModules
    }
  }
  for ((module, droppingProducts) in droppedModules) {
    println(
      "dropped $module from ${droppingProducts.joinToString()}: the project model cannot resolve it," +
      " and the product skips unresolved content modules"
    )
  }
  for ((index, product) in products.withIndex()) {
    val content = contentByProduct[index] ?: continue
    content.contentBlocks.flatMap { it.modules }.map { it.moduleId.name }.forEach(collector::addToSearchScope)
    product.spec?.deprecatedXmlIncludes?.forEach { collector.addToSearchScope(it.contentModuleName.value) }
    pluginModulesByProduct.get(index)?.let { (bundledPluginModules, frontendOnlyPluginModules) ->
      bundledPluginModules.forEach(collector::addToSearchScope)
      frontendOnlyPluginModules.forEach(collector::addToSearchScope)
    }
  }

  // The walk below reads the same files in the same order, and the prefetch only fills the memos of the collector.
  buildSpan("walk descriptors: prefetch") {
    val roots = ArrayList<DescriptorCollector.PrefetchRoot>()
    for ((index, product) in products.withIndex()) {
      val content = contentByProduct.get(index) ?: continue
      for (contentModule in content.contentBlocks.flatMap { it.modules }.map { it.moduleId.name }) {
        roots.add(DescriptorCollector.PrefetchRoot.contentModule(contentModule))
      }
      product.spec?.deprecatedXmlIncludes?.forEach { include ->
        roots.add(DescriptorCollector.PrefetchRoot(moduleName = include.contentModuleName.value, relativePath = include.resourcePath, isInclude = true))
      }
      val (bundledPluginModules, frontendOnlyPluginModules) = pluginModulesByProduct.get(index) ?: continue
      for (mainModule in bundledPluginModules + frontendOnlyPluginModules) {
        roots.add(DescriptorCollector.PrefetchRoot(moduleName = mainModule, relativePath = PLUGIN_XML_RELATIVE_PATH, isInclude = false))
      }
    }
    collector.prefetch(roots)
  }

  buildSpan("walk descriptors: collect") { collectFlatWalk(collector, products, contentByProduct, pluginModulesByProduct) }
  return DescriptorWalk(collector = collector, contentByProduct = contentByProduct)
}

/** The sequential flat walk of [walkDescriptors]. [pluginModulesByProduct] holds the bundled and the frontend-only plugins by product index. */
private fun collectFlatWalk(
  collector: DescriptorCollector,
  products: List<DiscoveredProduct>,
  contentByProduct: List<ProductContentBuildResult?>,
  pluginModulesByProduct: List<Pair<List<String>, List<String>>?>,
) {
  for ((index, product) in products.withIndex()) {
    val content = contentByProduct[index] ?: continue
    for (contentModule in content.contentBlocks.flatMap { it.modules }.map { it.moduleId.name }) {
      collector.collectContentModule(contentModule)
    }

    // The product's root descriptor and everything it pulls in. Not a content module and not a `plugin.xml`, so
    // nothing else reaches it, yet the layout reads it and its whole include closure on every fragment.
    product.spec?.deprecatedXmlIncludes?.forEach { include ->
      collector.collectInclude(declaringModuleName = include.contentModuleName.value, relativePath = include.resourcePath)
    }

    // The embedded frontend context reads its `product-modules.xml` and the included ones from sources when a fragment
    // writes `product-info.json` (`generateEmbeddedFrontendLaunchData` -> `getBundledPluginModules` -> `loadRawProductModules`).
    val properties = product.properties as? ProductProperties ?: continue
    properties.embeddedFrontendRootModule?.let(collector::collectProductModules)
    properties.rootModuleForModularLoader?.let(collector::collectProductModules)

    // The `use-idea-classloader` scan reads every bundled plugin's descriptor, whether or not the product packs
    // anything of that plugin in this fragment.
    val (bundledPluginModules, frontendOnlyPluginModules) = checkNotNull(pluginModulesByProduct.get(index))
    for (mainModule in bundledPluginModules) {
      collector.collect(moduleName = mainModule, relativePath = PLUGIN_XML_RELATIVE_PATH)
    }
    for (mainModule in frontendOnlyPluginModules) {
      collector.collect(moduleName = mainModule, relativePath = PLUGIN_XML_RELATIVE_PATH)
    }
  }
}

/**
 * The content modules of [content] that the model of [outputProvider] cannot resolve, when the layout of [product] sets
 * `skipUnresolvedContentModules`, sorted. Otherwise an empty set.
 *
 * The production content filter drops such a module too. The community model lacks the ultimate members of some module
 * sets, and IDEA Community sets the flag for them. The walk drops these modules, so every later reader of the walk reads
 * the filtered content. The product descriptor plan refuses them through the same production filter.
 */
private fun unresolvedContentModules(
  product: DiscoveredProduct,
  content: ProductContentBuildResult,
  outputProvider: ModuleOutputProvider,
): Set<String> {
  val properties = product.properties as? ProductProperties ?: return emptySet()
  if (!properties.productLayout.skipUnresolvedContentModules) {
    return emptySet()
  }
  val result = TreeSet<String>()
  for (block in content.contentBlocks) {
    for (module in block.modules) {
      if (outputProvider.findModule(module.moduleId.name.substringBeforeLast('/')) == null) {
        result.add(module.moduleId.name)
      }
    }
  }
  return result
}

/** This content without the content modules [modules] and without their module-set chains. */
private fun ProductContentBuildResult.withoutModules(modules: Set<String>): ProductContentBuildResult {
  return copy(
    contentBlocks = contentBlocks.map { block -> block.copy(modules = block.modules.filterNot { it.moduleId.name in modules }) },
    moduleToSetChainMapping = moduleToSetChainMapping.filterKeys { it.value !in modules },
  )
}

/**
 * Every part of the plan: the files of the flat [walk], the fragment plans of the split products and the descriptor
 * plans. [pluginDescriptorPlans] are the entries the dev sections read, passed through unchanged. [pluginRequests]
 * are the plugins that get a component in this run, so a fragment plan can check that every bundled plugin has one.
 * [runtimeModuleRepositoryProducts] names the products whose plan gets the runtime module repository component, see
 * [devDistRuntimeModuleRepositoryProducts]. [communityProducts] are the community products of [half], see
 * [devDistCommunityProducts]. The community half plans them, so this half collects no fragment plan, no product
 * descriptor and no module-set row of them.
 */
private fun collectDescriptorFiles(
  half: DevDistHalf,
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  walk: DescriptorWalk,
  verdicts: DevDistToolVerdicts,
  pluginDescriptorPlans: List<PluginDescriptorPlan>,
  pluginRequests: List<DevDistPluginRequest>,
  platformTable: DevDistPlatformJars,
  targets: BazelTargetsInfo.TargetsFile,
  runtimeModuleRepositoryProducts: Set<String>,
  communityProducts: Set<String>,
): CollectedPlan {
  val contentByProduct = walk.contentByProduct
  // In the product order, so the first product of a group of equal plans names the shared action, as an embedded
  // descriptor class does.
  val plannedDescriptors = pluginDescriptorPlans.mapNotNull { plan -> plan.productDescriptor?.let { plan.platformPrefix to it } }.toMap()
  val productDescriptors = shareEqualProductDescriptors(
    half.splitProducts.filterNot { it in communityProducts }.mapNotNull { product -> plannedDescriptors.get(product)?.let { product to it } }.toMap()
  )

  // The split product an embedded frontend is, by the class of its `ProductProperties`. A frontend debug wrapper
  // shares the class of its base and names a `rootModule`, so the base is the one key of the class without one.
  val splitProductsByClass = products
    .filter { it.name in half.splitDistributions && it.config.rootModule == null }
    .groupBy { it.config.className }
  val embeddedFrontendOf: (DiscoveredProduct, ProductProperties) -> String = { product, frontendProperties ->
    val candidates = splitProductsByClass.get(frontendProperties.javaClass.name).orEmpty()
    candidates.singleOrNull()?.name ?: error(
      "The embedded frontend of '${product.name}' is '${frontendProperties.javaClass.name}', which is not one split product" +
      " without a rootModule: ${candidates.map { it.name }}. Split it, or give the base key no rootModule."
    )
  }

  // The filter of each stated mode, keyed by mode id. The layout of a product of that mode applies the same filter. The
  // module set table and the payload of each product ask these filters, so both state one refusal.
  val modeFilters = devDistModeFilters(half = half, products = products, outputProvider = outputProvider)
  // The sets that the product descriptors and the embedded descriptors of the split products reach, from the DSL. The
  // payloads walk them, and the product descriptor rules compose the content from them.
  val moduleSets = TreeMap<String, ModuleSetData>()
  for (plan in pluginDescriptorPlans) {
    if (plan.platformPrefix in communityProducts) continue
    mergeModuleSetRows(table = moduleSets, rows = plan.moduleSets, owner = "Product '${plan.platformPrefix}'")
  }
  val platformPatches = half.platformPatches?.newTargets()
  val fragmentPlans = products.withIndex().mapNotNull { (productIndex, product) ->
    if (product.name in communityProducts) return@mapNotNull null
    val content = contentByProduct[productIndex] ?: return@mapNotNull null
    val moduleToSetChain = content.moduleToSetChainMapping.mapKeys { it.key.value }
    collectFragmentPlan(
      half = half,
      index = index,
      outputProvider = outputProvider,
      product = product,
      moduleToSetChain = moduleToSetChain,
      // A module the spec names itself, and not one a module set brought in - that one belongs to the set. The
      // platform jar table reports the same list, but a packaging test writes it, so reading it from there would hold
      // a spec change out of the plan until that test had run.
      directContentModules = content.contentBlocks.asSequence()
        .flatMap { it.modules }
        .map { it.moduleId.name }
        .filterNot { it in moduleToSetChain }
        .toList(),
      moduleSets = moduleSets,
      platformPatches = platformPatches,
      bazelTargets = targets,
      descriptorCollector = walk.collector,
      verdicts = verdicts,
      pluginRequests = pluginRequests,
      platformTable = platformTable,
      runtimeModuleRepository = product.name in runtimeModuleRepositoryProducts,
      embeddedFrontendOf = { frontendProperties -> embeddedFrontendOf(product, frontendProperties) },
      productDescriptor = productDescriptors.get(product.name),
      modeFilters = modeFilters,
    )
  }
  // The label of a set member is written once here, and a product body keeps only the labels no set carries, see
  // `sharePackedLabels`.
  val packedTable = moduleSets.values.map { set ->
    val packed = set.modules.mapNotNull { module -> verdicts.contentModuleJarLabels.get(module)?.let { module to it.label } }.toMap(TreeMap())
    set.copy(
      packed = packed,
      modeRefused = modeFilters.entries
        .mapNotNull { (mode, filter) ->
          val refused = packed.keys.filterNot { filter.isOptionalModuleIncluded(moduleName = it, pluginMainModuleName = null) }
          if (refused.isEmpty()) null else mode to refused
        }
        .toMap(TreeMap()),
    )
  }
  // The module system loading of a member is a product verdict, so the set list waits for every product too.
  val moduleSetTable = moduleSetModuleSystemLoaded(table = packedTable, payloads = fragmentPlans.map { plan ->
    val payload = plan.payloads.single { it.name == PLATFORM_LIB_FRAGMENT }
    ProductModuleSystemLoading(
      product = plan.platformPrefix,
      productMode = plan.productMode,
      moduleSets = payload.moduleSets,
      moduleSystemLoaded = payload.moduleSystemLoaded,
    )
  })
  val moduleSetsByName = moduleSetTable.associateBy(ModuleSetData::name)
  return CollectedPlan(
    files = walk.collector.result.toList(),
    pluginDescriptorPlans = pluginDescriptorPlans,
    platformPatches = platformPatches,
    products = fragmentPlans.map { plan ->
      plan.copy(payloads = plan.payloads.map { payload ->
        if (payload.name != PLATFORM_LIB_FRAGMENT) {
          payload
        }
        else {
          payload.copy(
            packedContentModuleJars = sharePackedLabels(
              product = plan.platformPrefix,
              productMode = plan.productMode,
              handedOver = payload.packedContentModuleJars,
              moduleSets = payload.moduleSets,
              table = moduleSetsByName,
            ),
            moduleSystemLoaded = shareModuleSystemLoadedLabels(
              product = plan.platformPrefix,
              productMode = plan.productMode,
              moduleSystemLoaded = payload.moduleSystemLoaded,
              moduleSets = payload.moduleSets,
              table = moduleSetsByName,
            ),
          )
        }
      })
    },
    moduleSets = moduleSetTable,
  )
}

/** The module sets that [moduleSets] reference, directly or through [ModuleSetData.nested], in walk order. */
private fun walkModuleSets(moduleSets: Collection<String>, table: Map<String, ModuleSetData>): List<ModuleSetData> {
  val result = ArrayList<ModuleSetData>()
  val pending = ArrayDeque(moduleSets)
  val visited = HashSet<String>()
  while (pending.isNotEmpty()) {
    val setName = pending.removeFirst()
    if (!visited.add(setName)) {
      continue
    }
    val moduleSet = table.get(setName) ?: continue
    result.add(moduleSet)
    pending.addAll(moduleSet.nested)
  }
  return result
}

/**
 * The content module filter of each mode of [DEV_DIST_STATED_PRODUCT_MODES], keyed by mode id. The layout of a product
 * of that mode applies the same filter. Empty when [half] has no split product.
 */
private fun devDistModeFilters(
  half: DevDistHalf,
  products: List<DiscoveredProduct>,
  outputProvider: ModuleOutputProvider,
): Map<String, ContentModuleFilter> {
  val applicationInfoModule = products.firstNotNullOfOrNull { product ->
    (product.properties as? ProductProperties)?.takeIf { product.name in half.splitDistributions }?.applicationInfoModule
  } ?: return emptyMap()
  val project = outputProvider.findRequiredModule(applicationInfoModule).project
  return DEV_DIST_STATED_PRODUCT_MODES.associateTo(TreeMap()) { mode -> mode.id to createProductModeContentModuleFilter(project, mode) }
}

/**
 * The top-level module sets of one `platform_lib` payload, the id of its product mode, and the labels of its packed
 * jars that the module system loads.
 */
internal class ProductModuleSystemLoading(
  @JvmField val product: String,
  @JvmField val productMode: String,
  @JvmField val moduleSets: List<String>,
  @JvmField val moduleSystemLoaded: Collection<String>,
)

/**
 * [table] with [ModuleSetData.moduleSystemLoaded] filled from the verdicts of [payloads].
 *
 * A packed member goes into the list of a set when its label is in [ProductModuleSystemLoading.moduleSystemLoaded] of
 * every payload that reaches the set and whose mode does not refuse the member. A member with a mixed verdict gets no
 * set entry, and the product payloads keep its label. A member that every reaching payload refuses gets no set entry.
 * A set that no payload reaches gets an empty list.
 */
internal fun moduleSetModuleSystemLoaded(table: List<ModuleSetData>, payloads: List<ProductModuleSystemLoading>): List<ModuleSetData> {
  val byName = table.associateBy(ModuleSetData::name)
  val reachingPayloads = HashMap<String, MutableList<ProductModuleSystemLoading>>()
  val loadedByPayload = payloads.associateWith { it.moduleSystemLoaded.toHashSet() }
  for (payload in payloads) {
    for (moduleSet in walkModuleSets(payload.moduleSets, byName)) {
      reachingPayloads.computeIfAbsent(moduleSet.name) { ArrayList() }.add(payload)
    }
  }
  return table.map { moduleSet ->
    val reaching = reachingPayloads.get(moduleSet.name).orEmpty()
    if (reaching.isEmpty()) {
      moduleSet
    }
    else {
      moduleSet.copy(moduleSystemLoaded = moduleSet.packed.filter { (module, label) ->
        val loading = reaching.filterNot { module in moduleSet.modeRefused.get(it.productMode).orEmpty() }
        loading.isNotEmpty() && loading.all { label in loadedByPayload.getValue(it) }
      }.keys.sorted())
    }
  }
}

/**
 * The labels of [moduleSystemLoaded] that no module set of one `platform_lib` payload carries, sorted.
 *
 * The Bazel side rebuilds [moduleSystemLoaded] as the union of the result and the labels of the walked sets, see
 * `dev_dist_packed_labels`. Both sides skip a set member that [productMode] refuses. The union is exact only when every
 * other set label is in [moduleSystemLoaded], so this function fails for a set member that the module system of
 * [product] does not load.
 */
internal fun shareModuleSystemLoadedLabels(
  product: String,
  productMode: String,
  moduleSystemLoaded: Collection<String>,
  moduleSets: Collection<String>,
  table: Map<String, ModuleSetData>,
): List<String> {
  val full = moduleSystemLoaded.toHashSet()
  val setLabels = TreeMap<String, String>()
  for (moduleSet in walkModuleSets(moduleSets, table)) {
    val refused = moduleSet.modeRefused.get(productMode).orEmpty()
    for (module in moduleSet.moduleSystemLoaded) {
      if (module in refused) {
        continue
      }
      setLabels.put(module, requireNotNull(moduleSet.packed.get(module)) {
        "Module set '${moduleSet.name}' names '$module' as loaded by the module system, but has no packing label for it"
      })
    }
  }
  val notLoaded = setLabels.filterValues { it !in full }
  check(notLoaded.isEmpty()) {
    "The module system of '$product' does not load these module set members. A module set names a member as loaded " +
    "only when every product that references the set loads it:\n" +
    notLoaded.entries.joinToString(separator = "\n") { (module, label) -> "  $module ($label)" }
  }
  val carried = setLabels.values.toHashSet()
  return full.filterTo(TreeSet()) { it !in carried }.toList()
}

/**
 * The labels of the handover set of one `platform_lib` payload that no module set of the payload carries, sorted.
 *
 * [handedOver] is the whole handover set: the label of every payload module that packs a `lib/` jar. [moduleSets] are
 * the top-level sets the payload references, and [table] holds every set with its [ModuleSetData.packed] labels. The
 * Bazel side rebuilds [handedOver] as the union of the result and the labels of the walked sets. Both sides skip a set
 * member that [productMode] refuses, see [ModuleSetData.modeRefused]. The union is exact only when every other set
 * label is in [handedOver] and no refused label is. So this function fails for a set member the payload does not hand
 * over, and for a refused member it hands over.
 */
internal fun sharePackedLabels(
  product: String,
  productMode: String,
  handedOver: Collection<String>,
  moduleSets: Collection<String>,
  table: Map<String, ModuleSetData>,
): List<String> {
  val full = handedOver.toHashSet()
  val setLabels = TreeMap<String, String>()
  val refusedLabels = TreeMap<String, String>()
  for (moduleSet in walkModuleSets(moduleSets, table)) {
    val refused = moduleSet.modeRefused.get(productMode).orEmpty()
    for ((module, label) in moduleSet.packed) {
      (if (module in refused) refusedLabels else setLabels).put(module, label)
    }
  }
  val notHandedOver = setLabels.filterValues { it !in full }
  check(notHandedOver.isEmpty()) {
    "'$product' does not hand over the packing label of these module set members. A module set carries the label " +
    "of a member for every product that references the set, less the members the product mode refuses:\n" +
    notHandedOver.entries.joinToString(separator = "\n") { (module, label) -> "  $module ($label)" }
  }
  val refusedHandedOver = refusedLabels.filterValues { it in full }
  check(refusedHandedOver.isEmpty()) {
    "'$product' hands over these module set members, which its mode '$productMode' refuses:\n" +
    refusedHandedOver.entries.joinToString(separator = "\n") { (module, label) -> "  $module ($label)" }
  }
  val carried = setLabels.values.toHashSet()
  return full.filterTo(TreeSet()) { it !in carried }.toList()
}

/**
 * The modules whose raw output no packing target may take, sorted: a plugin layout excludes paths from them.
 */
internal fun collectContentVetoModules(products: List<DiscoveredProduct>): List<String> {
  return products.asSequence()
    .mapNotNull { it.properties as? ProductProperties }
    .flatMap { properties ->
      properties.productLayout.pluginLayouts.value.asSequence().flatMap { it.getModuleExcludesModuleNames() }
    }
    .distinct()
    .sorted()
    .toList()
}

/**
 * The `content_module_jar` labels of the handed-over jars that the module system loads, sorted.
 *
 * `dev_dist_platform_payload` puts a packed jar on the core classpath when it is a direct child of `lib/` and the
 * result does not name it. [coreClassPath] is what `contentModuleJarCoreClasspathEntries` returns over [handedOver].
 * This function fails when the two answers can differ:
 *
 * - an entry of [coreClassPath] names a subdirectory of `lib/`;
 * - a direct child of `lib/` is off the core classpath, but no `content_module_jar` packs it, so no label names it.
 *
 * A library-only jar is not in [handedOver]. Both answers put it on the core classpath when it is a direct child of `lib/`.
 * [contentModuleJarDestinations] maps a handed-over destination to the `content_module_jar` label that packs it.
 */
private fun moduleSystemLoadedLabels(
  product: String,
  coreClassPath: Set<String>,
  handedOver: Set<String>,
  contentModuleJarDestinations: Map<String, String>,
): List<String> {
  val nested = coreClassPath.filter { '/' in it }.sorted()
  check(nested.isEmpty()) {
    "$product: these core classpath jars are in a subdirectory of lib/, but the payload puts only a direct child of lib/ on it: " +
    nested.joinToString()
  }
  val off = handedOver.filter { '/' !in it && it !in coreClassPath }.sorted()
  val unlabeled = off.filter { it !in contentModuleJarDestinations }
  check(unlabeled.isEmpty()) {
    "$product: these lib/ jars are off the core classpath, but no content_module_jar packs them, so no label can name them: " +
    unlabeled.joinToString()
  }
  return off.mapTo(TreeSet()) { contentModuleJarDestinations.getValue(it) }.toList()
}

/**
 * The flat core comes from the product's rows of the platform jar table rather than from `createPlatformLayout`:
 * computing the layout needs a `BuildContext` per product, and the table is the same answer. `deriveDevDistPlatformJars`
 * derives the rows from the source layout of every discovered product in the same run, so a product without a
 * packaging test has rows too. `ultimateGenerator` reads the same table for validation.
 *
 * What the table answers is `lib/` alone: its jars, and the modules and libraries in them. The product's own content
 * modules come from the spec, see [directContentModules]. The table states those too, and taking them from there cost
 * a round trip: a packaging test writes the table, so a spec change reached the plan only on the generator run after
 * that test had run.
 */
private fun collectFragmentPlan(
  half: DevDistHalf,
  /** The index of the run, which spells a label for a plan package of [half]. Its project root is the root of [half]. */
  index: DevDistBazelIndex,
  outputProvider: ModuleOutputProvider,
  product: DiscoveredProduct,
  moduleToSetChain: Map<String, List<String>>,
  /** The content modules the product spec names itself, as opposed to the ones a module set contains. */
  directContentModules: List<String>,
  /** The module-set table of the half, keyed by set name. The payload walks the sets it references. */
  moduleSets: Map<String, ModuleSetData>,
  /** The `dev_dist_platform_patch` targets of every product, or `null` for a half without platform patches. */
  platformPatches: DevDistPlatformPatchTargets?,
  bazelTargets: BazelTargetsInfo.TargetsFile,
  descriptorCollector: DescriptorCollector,
  verdicts: DevDistToolVerdicts,
  pluginRequests: List<DevDistPluginRequest>,
  platformTable: DevDistPlatformJars,
  /** Whether a run configuration of the product asks for the runtime module repository. */
  runtimeModuleRepository: Boolean,
  /** The split product key of the embedded frontend, see [ProductFragmentPlan.embeddedFrontend]. Fails on a miss. */
  embeddedFrontendOf: (ProductProperties) -> String,
  /** The actions that write the two generated entries of the application-info module jar, or `null` when none do. */
  productDescriptor: ProductDescriptorPlan?,
  /** The content module filter of each stated mode, keyed by mode id. The monolith has none. */
  modeFilters: Map<String, ContentModuleFilter>,
): ProductFragmentPlan? {
  val config = half.splitDistributions.get(product.name) ?: return null
  val properties = product.properties as? ProductProperties
                   ?: error("Split dev distribution '${product.name}' has no ProductProperties")
  val productToken = devDistProductToken(projectHome = index.projectRoot, productProperties = properties)

  // One fragment owns `lib/`, minus the jars another producer packs - which it is told by name, see the `except`
  // selector. Splitting it buys nothing: a fragment's action key covers the shared project model tree, so an `.iml`
  // edit re-keys every fragment however the jars are grouped, and the jars worth isolating from a *source* edit are
  // exactly the ones that left for their own packing actions.
  val platformPayload = MutablePayload()

  // The table states every jar under `lib/` with all of its members. The product content modules among them come from
  // `directContentModules` and the module sets, so the payload takes the flat core: the members outside
  // `[platform_content_modules]`, and a jar with no such member is not the payload's.
  val productContentModules = platformTable.platformContentModules.filterTo(HashSet()) { it.product == productToken }.mapTo(HashSet()) { it.module }
  var platformJarRows = 0
  for (row in platformTable.platformJars) {
    if (row.product != productToken) continue
    platformJarRows++
    val members = row.members.filter { it !in productContentModules }
    if (members.isNotEmpty()) {
      platformPayload.addJar(relativeOutputFile = row.relativeOutputFile, members = members)
    }
  }
  check(platformJarRows > 0) {
    "The source layout of split dev distribution '${product.name}' has no platform jars for '$productToken'"
  }
  // A `[platform_libraries]` row names a project library or a module library, and only the project model tells which.
  // The module that owns a module library declares it, so the payload takes the project libraries alone.
  val jpsProject = outputProvider.findRequiredModule(properties.applicationInfoModule).project
  for (row in platformTable.platformLibraries) {
    if (row.product == productToken && jpsProject.libraryCollection.findLibrary(row.library) != null) {
      platformPayload.projectLibraries.add(row.library)
    }
  }
  for (contentModule in directContentModules) {
    // The name is `moduleName/descriptorName` when the descriptor is not named after the module; the payload declares
    // Bazel outputs, which are per module.
    platformPayload.modules.add(contentModule.substringBeforeLast('/'))
  }

  // The set membership itself goes to `dev_dist_module_sets.bzl` and the payload keeps the references, so a set
  // shared by two split products is written once instead of flattened into both. The table states the DSL sets, and
  // the chain a module was reached by names the top-level set that the payload references first.
  for ((contentModule, chain) in moduleToSetChain) {
    check(chain.isNotEmpty()) { "Content module '$contentModule' of '${product.name}' has an empty module-set chain" }
    platformPayload.moduleSets.add(chain.first())
  }
  // The application-info module is not a content module, and the handover below checks that. So no content module jar
  // packs its jar. A residual jar packs it with two patches, the product descriptor and the stamped application info,
  // see `ProductDescriptorPlan`. A platform patch of the module keeps the jar with the fragment, which writes both itself.
  platformPayload.modules.add(properties.applicationInfoModule)
  for (moduleName in config.runtimeClasspathModules) {
    checkNotNull(outputProvider.findModule(moduleName)) {
      "Split dev distribution '${product.name}' loads the runtime classpath of module '$moduleName', which the project does not have"
    }
    platformPayload.runtimeClasspathModules.add(moduleName)
  }
  val isFrontend = properties.productMode == com.intellij.platform.productMode.ProductMode.FRONTEND
  // A product uses its product descriptor action when the action patches the application-info module jar, or, for a
  // frontend, for the plugin-classpath prefix alone.
  fun usesProductDescriptor(residualJars: Collection<ResidualPlatformJar>): Boolean {
    return isFrontend || residualJars.any { it.patchedModule == properties.applicationInfoModule }
  }
  val layout = createPlatformLayout(properties, outputProvider)
  // A module library merges into the jar of its module, as the content module jar packs it. No producer of the split
  // distribution packs one that the layout places elsewhere, see ADR 0023.
  layout.getIncludedModuleLibraries().firstOrNull()?.let {
    error("${product.name}: the platform layout places the module library '${it.libraryName}' of '${it.moduleName}' outside its module jar")
  }
  // The entries a layout patcher of the product writes, keyed by the patched module, in the order the patchers write
  // them. See `DevPlatformPatchOwner`.
  val entryPatches = LinkedHashMap<String, LinkedHashMap<String, String>>()
  for (entry in layout.patchers.filterIsInstance<DevPlatformPatchOwner>().flatMap { it.devPlatformEntryPatches }) {
    val patchTargets = checkNotNull(platformPatches) {
      "${product.name}: the ${half.name} half has no platform patches, so it cannot plan the patch of '${entry.path}' of '${entry.moduleName}'"
    }
    val label = patchTargets.label(product = product.name, entry = entry) { module ->
      requireNotNull(index.dependencyLabel(module, dependentIsCommunity = index.planPackageIsCommunity)) {
        "Patched module '$module' has no Bazel target"
      }
    }
    check(entryPatches.getOrPut(entry.moduleName) { LinkedHashMap() }.put(label, entry.path) == null) {
      "${product.name}: two platform patches write '${entry.path}' of '${entry.moduleName}'"
    }
  }
  // The patches of the platform jars, keyed by the patched module: the generated entries of the application-info module
  // jar, the entries of the layout patchers, and the frontend icons of the base IDE. No content module jar packs a
  // patched module. The application info comes first and the product descriptor last, as `JarPackager` writes them.
  val modulePatches = buildMap {
    // A frontend packs its application-info module in the frontend root descriptor jar, see the handover below.
    if (!isFrontend) {
      productDescriptor?.let { descriptor ->
        put(properties.applicationInfoModule, buildMap {
          put(descriptor.applicationInfoPatchLabel(index), descriptor.applicationInfoPath)
          entryPatches.remove(properties.applicationInfoModule)?.let(::putAll)
          put(descriptor.descriptorLabel, descriptor.descriptorPath)
        })
      }
    }
    putAll(entryPatches)
    if (isFrontend) {
      val embeddedFrontend = checkNotNull(half.embeddedFrontend) {
        "${product.name}: the ${half.name} half has no embedded frontend, so it cannot plan a frontend product"
      }
      put(embeddedFrontend.iconsModule, frontendIconPatches(support = embeddedFrontend, index = index, product = product.name, properties = properties))
    }
  }

  // A module has no `content_module_jar` label for two reasons, and only one of them is a defect. The module can be
  // absent from `bazel-targets.json`, which means that file is older than the project model. Or the tool gives it no
  // call, which it does deliberately. This list reports the first reason only. Its scope is the content modules the
  // payload reached. The payload also holds the application-info module and the module excludes, which are not content
  // modules and correctly have no such label.
  val staleTargetNames = ArrayList<String>()
  // The module set members that the product mode refuses. The payload hands over none of them.
  val refusedSetMembers = HashSet<String>()
  val platformLibPayload = platformPayload.let { payload ->
    // The `lib/`-owning payload hands jars over to another producer, and `dev_dist_platform_payload` needs the handover
    // set. See `FragmentPayload.packedContentModuleJars`.
    val packed = run {
      // Every module the payload holds at *analysis* time, not just the ones it names here. `_expand_module_sets` in
      // `jps_dynamic_deps_ultimate.bzl` folds a referenced set's membership into `modules` on the Bazel side, and a
      // module that reaches the payload only through a set packs its `lib/` jar exactly like one named directly - so
      // walking the sets here is what keeps the handover set complete. Skipping the walk produced 18 labels where the
      // payload hands over 416 jars, and the fragment would have packed the other 398 itself while their packing
      // targets went unbuilt.
      val reached = LinkedHashSet<String>(payload.modules)
      // The content modules the payload reached, which is the scope of the `staleTargetNames` check. A module packed
      // under another jar name is skipped for the same reason the filter below skips it. The seed holds the content
      // modules the spec names itself, and the walk below adds the ones a module set carries.
      val contentModules = directContentModules.mapTo(HashSet<String>()) { it.substringBeforeLast('/') }
      // A set member that the product mode refuses is not handed over, as `ModuleSetData.modeRefused` states. The
      // filter applies to set members only. The layout places a refused module the spec names itself, so the checks
      // below report it.
      val modeFilter = modeFilters.get(properties.productMode.id)
      val pending = ArrayDeque(payload.moduleSets)
      val visited = HashSet<String>()
      while (pending.isNotEmpty()) {
        val setName = pending.removeFirst()
        if (!visited.add(setName)) {
          continue
        }
        val moduleSet = moduleSets.get(setName) ?: continue
        for (module in moduleSet.modules) {
          if (modeFilter == null || modeFilter.isOptionalModuleIncluded(moduleName = module, pluginMainModuleName = null)) {
            reached.add(module)
          }
          else {
            refusedSetMembers.add(module)
          }
        }
        contentModules.addAll(moduleSet.modules)
        pending.addAll(moduleSet.nested)
      }
      // A `content_module_jar` has neither the stamped application info nor the product descriptor. A frontend is the
      // exception: its root descriptor jar packs the application-info module, see the handover below.
      check(
        properties.productMode == com.intellij.platform.productMode.ProductMode.FRONTEND ||
        properties.applicationInfoModule !in contentModules
      ) {
        "Split dev distribution '${product.name}' declares its application-info module '${properties.applicationInfoModule}'" +
        " as a content module. The jar of that module carries the product descriptor, so a content module jar cannot pack it." +
        " Move the content module descriptor to a module of its own."
      }
      for (contentModule in contentModules) {
        if (contentModule !in payload.modulesPackedUnderAnotherName && !bazelTargets.modules.containsKey(contentModule)) {
          staleTargetNames.add(contentModule)
        }
      }
      // A module the table packs under another jar name is skipped, however it earned its packing target: that target
      // is some plugin's `lib/<module>.jar`, not this payload's. See [MutablePayload.modulesPackedUnderAnotherName].
      // A module with patches is skipped too, because a packing target reads the raw output and its residual jar
      // applies the patches.
      reached.asSequence()
        .filterNot { it in payload.modulesPackedUnderAnotherName || it in modulePatches }
        .mapNotNull { module ->
          if (properties.productMode == com.intellij.platform.productMode.ProductMode.FRONTEND && module == properties.applicationInfoModule) {
            // The jar that packs the embedded descriptor of the product that embeds this frontend, as the root descriptor.
            requireNotNull(verdicts.frontendRootDescriptorJars.get(product.name)) {
              "Frontend product '${product.name}' has no frontend root descriptor jar"
            }
          }
          else {
            verdicts.contentModuleJarLabels.get(module)?.label
          }
        }
        .distinct()
        .sorted()
        .toList()
    }
    val frozen = payload.freeze(name = PLATFORM_LIB_FRAGMENT, packedContentModuleJars = packed)
    run {
      // A `content_module_jar` is packed once for every product, with its natives mode from the shared map.
      check(properties.presignedNativeLibs == PRESIGNED_NATIVE_LIBS) {
        "${product.name}: the dev distribution packs natives by PRESIGNED_NATIVE_LIBS, " +
        "but the product maps ${properties.presignedNativeLibs}"
      }
      val residual = LinkedHashMap<String, ResidualPlatformJar>()
      // The destinations another producer than the fragment packs, for the core classpath below.
      val handedOver = HashSet<String>()
      // The `content_module_jar` label of each handed-over destination that such a target packs.
      val contentModuleJarDestinations = HashMap<String, String>()
      // The labels of `packed` that the layout places, for the core classpath check below.
      val placedLabels = HashSet<String>()
      for ((destination, items) in layout.includedModules.groupBy { it.relativeOutputFile }) {
        val memberNames = items.map { it.moduleName }
        // The layout and `ModuleSetData.modeRefused` ask the same filter. The layout asks it for an optional module
        // only, so a refused member that the layout still places fails here.
        val placedRefused = memberNames.filter { it in refusedSetMembers }
        check(placedRefused.isEmpty()) {
          "${product.name}: the layout places '$destination', but the product mode refuses its module set members $placedRefused"
        }
        val contentModuleJarLabel = verdicts.contentModuleJarLabels.get(destination.removeSuffix(".jar"))?.label
        if (contentModuleJarLabel != null && contentModuleJarLabel in packed) {
          handedOver.add(destination)
          contentModuleJarDestinations.put(destination, contentModuleJarLabel)
          placedLabels.add(contentModuleJarLabel)
          continue
        }
        // The frontend root descriptor jar packs the application-info module of a frontend, see the handover above.
        if (properties.productMode == com.intellij.platform.productMode.ProductMode.FRONTEND && properties.applicationInfoModule in memberNames) {
          handedOver.add(destination)
          verdicts.frontendRootDescriptorJars.get(product.name)?.let(placedLabels::add)
          continue
        }
        // Any other jar the packer cannot pack fails the generator, because a holdout brings `platform_lib` back.
        fun cannotPack(reason: String): Nothing = error("${product.name}: the packer cannot pack the platform jar '$destination': $reason")
        when {
          // A nested destination is fine. `dev_dist_platform_jar` states its own `lib/`-relative path, and the
          // collector places the jar there, so `ext/platform-main.jar` packs like any other residual jar.
          !destination.endsWith(".jar") -> cannotPack("not a jar")
          items.any { it.moduleName in layout.getModuleExcludesModuleNames() } -> cannotPack("a member has a module exclude")
          memberNames.any { index.location(it) == null } -> cannotPack("a member has no Bazel target")
          memberNames.any { outputProvider.findRequiredModule(it).isTestModule() } -> cannotPack("a member is test output")
          memberNames.any { member -> layout.includedModules.count { it.moduleName == member } != 1 } -> cannotPack("a member belongs to several jars")
        }
        // The application-info module carries the generated product descriptor. Two actions write it and the stamped
        // application info, see `ProductDescriptorPlan`.
        if (properties.applicationInfoModule in memberNames && properties.applicationInfoModule !in modulePatches) {
          cannotPack("the application-info module jar has no product descriptor plan")
        }
        val patchedMembers = memberNames.filter { it in modulePatches }
        if (patchedMembers.size > 1) {
          cannotPack("two members have patches: $patchedMembers")
        }
        val patchedModule = patchedMembers.singleOrNull()
        val librariesByMember = items.associate { item ->
          item.moduleName to mergedLibraryNames(item, outputProvider.findRequiredModule(item.moduleName), layout).toSet()
        }
        val libraryNames = librariesByMember.values.flatten().toSet()
        // A platform jar never merges a presigned library. The library packs as a `content_module_jar` in natives mode,
        // so the packer refuses a residual jar that merges one.
        val nativeLibs = mergedPresignedNativeLibs(
          packedModuleNames = memberNames,
          findModule = outputProvider::findRequiredModule,
          isMerged = { name, _, _, module -> name in librariesByMember.getValue(module.name) },
          presignedNativeLibs = properties.presignedNativeLibs,
        )
        if (nativeLibs.isNotEmpty()) {
          cannotPack("a member merges a presigned native library")
        }
        val libraries = mergedLibraryTargetLabels(
          packedModuleNames = memberNames,
          recordedNames = libraryNames,
          findModule = outputProvider::findModule,
          isMerged = { name, _, _, module -> name in librariesByMember.getValue(module.name) },
          labelOf = { library, owner -> index.libraryLabel(library, owner, dependentIsCommunity = index.planPackageIsCommunity) },
          refuse = ::cannotPack,
        ) ?: cannotPack("the module libraries cannot be declared")
        val layoutLibraries = layoutPlacedLibraryLabels(destination = destination, layout = layout, index = index, refuse = ::cannotPack)
        residual.put(destination, ResidualPlatformJar(
          modules = memberNames.map { requireNotNull(index.dependencyLabel(it, dependentIsCommunity = index.planPackageIsCommunity)) },
          // The rule merges the libraries after the module outputs in this order, which is the order `JarPackager`
          // writes the same jar: the members' merged libraries first, the layout-placed ones after them.
          libraries = (libraries + layoutLibraries).distinct(),
          patches = patchedModule?.let(modulePatches::getValue) ?: emptyMap(),
          patchedModule = patchedModule,
        ))
        handedOver.add(destination)
      }
      val libraryJars = collectLibraryOnlyJars(
        productName = product.name,
        layout = layout,
        moduleDestinations = layout.includedModules.mapTo(HashSet()) { it.relativeOutputFile },
        index = index,
      )
      residual.putAll(libraryJars)
      // Every module jar goes to a producer: a content module jar, a residual jar or the frontend root descriptor jar.
      check(layout.includedModules.all { it.relativeOutputFile in handedOver }) {
        "${product.name}: a platform jar has no producer: " +
        layout.includedModules.map { it.relativeOutputFile }.distinct().filter { it !in handedOver }.joinToString()
      }
      // The rule of `generateClassPathByLayoutReport` over the jars the fragment does not pack. A split fragment
      // starts with `isBootClassPathCorrect = false`, so `nio-fs.jar` stays on the core classpath. The payload rule
      // derives the core classpath again, and `moduleSystemLoadedLabels` proves that both answers are equal.
      val libDir = Path.of("lib")
      val coreClassPath = contentModuleJarCoreClasspathEntries(
        libDir = libDir,
        includedModules = layout.includedModules,
        externallyPackedJars = handedOver,
        skipNioFs = false,
      ).mapTo(HashSet()) { libDir.relativize(it).invariantSeparatorsPathString }
      // The payload packs every handed-over jar, so a jar that the layout does not place is a defect of the handover.
      val notPlaced = packed.filterNot { it in placedLabels }
      check(notPlaced.isEmpty()) {
        "${product.name}: the layout places no jar of these handed-over labels:\n" + notPlaced.joinToString(separator = "\n") { "  $it" }
      }
      val moduleSystemLoaded = moduleSystemLoadedLabels(
        product = product.name,
        coreClassPath = coreClassPath,
        handedOver = handedOver,
        contentModuleJarDestinations = contentModuleJarDestinations,
      )
      // A project library the layout places in a residual jar with a module member is inside that jar and nowhere
      // else, so the fragment that no longer packs the jar has no use for the raw library either. The libraries of a
      // library-only jar stay in the payload: the runtime module repository fragment reads the platform declaration
      // and resolves every library of the platform layout.
      val liftedLibraries = layout.getIncludedProjectLibraries()
        .filter { it.outPath in residual.keys && it.outPath !in libraryJars.keys }
        .mapTo(HashSet()) { it.libraryName }
      frozen.copy(
        projectLibraries = frozen.projectLibraries.filterNot { it in liftedLibraries },
        residualJars = residual,
        pluginClasspathPrefix = requireNotNull(productDescriptor?.pluginClasspathPrefixLabel.takeIf { usesProductDescriptor(residual.values) }) {
          "${product.name}: no product descriptor action writes the plugin-classpath prefix"
        },
        moduleSystemLoaded = moduleSystemLoaded,
      )
    }
  }
  check(staleTargetNames.isEmpty()) {
    "Content modules of '${product.name}' are absent from bazel-targets.json, so that file is older than the project" +
    " model; run ./build/jpsModelToBazel.cmd:\n" +
    staleTargetNames.sorted().joinToString(separator = "\n") { "  $it" }
  }
  // A modular-loader product reads the repository at every start, so its plan carries the component whatever its rows ask.
  val modularLoader = properties.rootModuleForModularLoader != null
  val hasRuntimeModuleRepository = runtimeModuleRepository || modularLoader
  val frontendProperties = if (!hasRuntimeModuleRepository) null else properties.embeddedFrontendProperties?.invoke()
  val embeddedFrontend = frontendProperties?.let(embeddedFrontendOf)
  val runtimeModuleRepositoryPayload = if (!hasRuntimeModuleRepository) {
    emptyList()
  }
  else {
    listOf(collectRuntimeModuleRepositoryPayload(
      product = product.name,
      properties = properties,
      embeddedFrontend = embeddedFrontend,
      frontendProperties = frontendProperties,
      outputProvider = outputProvider,
      pluginRequests = pluginRequests,
    ))
  }
  if (hasRuntimeModuleRepository) {
    checkRuntimeModuleRepositoryDescriptors(
      outputProvider = outputProvider,
      properties = properties,
      index = index,
      descriptorCollector = descriptorCollector,
    )
  }
  val launchModel = computeDevProductLaunchModel(properties, outputProvider, PINNED_BUILD_DATE_IN_SECONDS)
  return ProductFragmentPlan(
    platformPrefix = product.name,
    productMode = properties.productMode.id,
    buildModules = (sequenceOf("intellij.idea.community.build") + product.config.modules).distinct().sorted().toList(),
    runtimeModuleRepository = hasRuntimeModuleRepository,
    embeddedFrontend = embeddedFrontend,
    modularLoader = modularLoader,
    payloads = listOf(platformLibPayload) + runtimeModuleRepositoryPayload,
    platformAssets = collectPlatformAssets(layout = layout, properties = properties, index = index),
    launchModel = launchModel,
    launchModelRelativePath = launchModelRelativePath(half.caseSafeProductName(product.name)),
    ideaProperties = index.planLabel(half.baseIdeaProperties(product.name, languageServerBase = launchModel.ideaProperties.languageServerBase)),
    applicationInfoSources = applicationInfoSources(
      index = index,
      project = jpsProject,
      properties = properties,
      product = product.name,
      hostProperties = half.embeddedFrontend?.hostProperties(properties),
    ),
    platformJarOrder = if (hasRuntimeModuleRepository) platformJarOrder(product.name, layout, platformLibPayload.residualJars) else null,
    applicationInfoModule = properties.applicationInfoModule,
    productDescriptor = productDescriptor.takeIf { usesProductDescriptor(platformLibPayload.residualJars.values) },
  )
}

/**
 * [ApplicationInfoSources] of [product], spelled for a plan package of [index]. A frontend takes the application info of
 * its host product [hostProperties] as the host, the same file that `applicationInfoOverride` reads.
 */
private fun applicationInfoSources(
  index: DevDistBazelIndex,
  project: JpsProject,
  properties: ProductProperties,
  product: String,
  hostProperties: ProductProperties?,
): ApplicationInfoSources {
  val host = hostProperties?.let { index.planLabel(applicationInfoLabel(index = index, project = project, properties = it, platformPrefix = product)) }
  @Suppress("DEPRECATION")
  check(host != null || properties.applicationInfoOverride(project) == null) {
    "$product overrides its application info, and the plan knows no source for it"
  }
  return ApplicationInfoSources(
    source = index.planLabel(applicationInfoLabel(index = index, project = project, properties = properties, platformPrefix = product)),
    host = host,
    replacements = properties.appInfoXmlReplacements.orEmpty().map { (key, value) -> "$key=$value" },
  )
}

/**
 * [plans] with one plan for every group of equal plans: a later product reads the plan of the first product of its group.
 *
 * Two plans are equal when they differ only in the name and in the header of the source. The frontends of one class
 * render the same content, so they share one action instead of one copy of the source each.
 */
internal fun shareEqualProductDescriptors(plans: Map<String, ProductDescriptorPlan>): Map<String, ProductDescriptorPlan> {
  val firstByKey = HashMap<ProductDescriptorPlan, ProductDescriptorPlan>()
  return plans.mapValues { (_, plan) -> firstByKey.getOrPut(plan.withoutIdentity()) { plan } }
}

/**
 * The files that the icon patcher of the frontend [product] writes over the output of
 * [DevDistEmbeddedFrontendSupport.iconsModule], keyed by label and valued by the entry path.
 *
 * A frontend states the images directory of its base IDE. The patcher reads the icons there, and
 * [DevDistEmbeddedFrontendSupport.iconPatches] names their entries in the order the patcher writes them. The patcher
 * lets an EAP icon fall back to the release icon. One label cannot state two entries, so the generator requires every
 * file.
 */
private fun frontendIconPatches(
  support: DevDistEmbeddedFrontendSupport,
  index: DevDistBazelIndex,
  product: String,
  properties: ProductProperties,
): Map<String, String> {
  val imagesDirectory = requireNotNull(properties.imagesDirectoryPath) {
    "Frontend '$product' states no imagesDirectoryPath, so the generator cannot state its icon patches"
  }.normalize()
  val result = LinkedHashMap<String, String>()
  for ((fileName, entry) in support.iconPatches) {
    val file = imagesDirectory.resolve(fileName)
    check(Files.isRegularFile(file)) { "Frontend '$product' has no icon '$file'" }
    val relativePath = index.projectRoot.relativize(file).invariantSeparatorsPathString
    val label = requireNotNull(index.containingPackageLabel(relativePath)) {
      "No Bazel package holds the icon '$relativePath' of frontend '$product'"
    }
    result.put(label, entry)
  }
  return result
}

/**
 * The payload of the runtime module repository fragment: the few names no other declaration carries.
 *
 * The generator lays the platform and every bundled plugin out without files, and the dry layout resolves the output
 * and the libraries of each module it packs. The `platform_lib` payload of the product already names the platform,
 * and the `platform_lib` payload of [embeddedFrontend] names the frontend's platform. The reference macro takes
 * both declarations whole, before the packed jars leave them. The bundled plugins arrive as `DevDistContentInfo` from
 * their components, which `dev_dist_plugin_content` unions per product. So this payload names only the embedded
 * frontend root modules and the product's own modular-loader root, which no platform payload and no plugin carries.
 * A debug wrapper's `rootModule` (`build/dev-build.json`) is that root, and the layout packs it into a residual jar
 * only, whose raw output the `platform_lib` payload does not declare.
 *
 * Every bundled plugin of the product and every frontend-only plugin of the frontend must have a component, or the
 * fragment would lack that plugin's modules and the reference assembler would fail on an undeclared input.
 * [pluginRequests] names the plugins that get one in this run, and this fails on a bundled plugin without a request.
 */
private fun collectRuntimeModuleRepositoryPayload(
  product: String,
  properties: ProductProperties,
  embeddedFrontend: String?,
  frontendProperties: ProductProperties?,
  outputProvider: ModuleOutputProvider,
  pluginRequests: List<DevDistPluginRequest>,
): FragmentPayload {
  val payload = MutablePayload()
  properties.embeddedFrontendRootModule?.let(payload.modules::add)
  properties.rootModuleForModularLoader?.let(payload.modules::add)

  val bundledPluginModules = getBundledPluginModules(properties, outputProvider)
  checkBundledPluginsHaveComponents(product = product, properties = properties, mainModules = bundledPluginModules, pluginRequests = pluginRequests)

  if (frontendProperties != null) {
    frontendProperties.embeddedFrontendRootModule?.let(payload.modules::add)
    val frontendOnlyPlugins = getBundledPluginModules(frontendProperties, outputProvider).filterNot { it in bundledPluginModules }
    checkBundledPluginsHaveComponents(
      product = requireNotNull(embeddedFrontend) { "'$product' embeds a frontend without a split product key" },
      properties = frontendProperties,
      mainModules = frontendOnlyPlugins,
      pluginRequests = pluginRequests,
    )
  }

  // A name the project does not have declares nothing. The generator resolves the reference inputs from these names
  // and fails on a name that the targets JSON lacks, so the payload keeps only the names of the project.
  payload.modules.retainAll { outputProvider.findModule(it) != null }
  return payload.freeze(name = PLATFORM_RUNTIME_MODULE_REPOSITORY_FRAGMENT)
}

/**
 * Fails when a bundled plugin of [product] among [mainModules] has no bundled component request in [pluginRequests].
 *
 * The layouts, not the raw names: the old payload walked `getPluginLayoutsByJpsModuleNames` too, so a name without a
 * layout declared nothing then and needs no component now.
 */
private fun checkBundledPluginsHaveComponents(
  product: String,
  properties: ProductProperties,
  mainModules: Collection<String>,
  pluginRequests: List<DevDistPluginRequest>,
) {
  val withComponent = pluginRequests.asSequence()
    .filter { it.product == product && it.tier == DevDistPluginTier.BUNDLED }
    .mapTo(HashSet()) { it.layout.mainModule }
  val missing = getPluginLayoutsByJpsModuleNames(mainModules, properties.productLayout)
    .map { it.mainModule }
    .filter { it !in withComponent }
    .sorted()
  check(missing.isEmpty()) {
    "Bundled plugins of '$product' have no component, so the runtime module repository fragment would lack their modules:\n" +
    missing.joinToString(separator = "\n") { "  $it" }
  }
}

/**
 * Fails when the project model tree lacks a descriptor that the runtime module repository fragment reads.
 *
 * The fragment dry-builds every bundled plugin descriptor the runtime repository generator reads, including the extra
 * frontend-only plugins of the embedded frontend. The tree takes the conventional descriptors, and the others from the
 * `DEV_DIST_EXTRA_DESCRIPTOR_FILES` of the flat walk. The converter drops a listed file that the package of its module
 * does not hold, so such a file is missing from the tree as well.
 */
private fun checkRuntimeModuleRepositoryDescriptors(
  outputProvider: ModuleOutputProvider,
  properties: ProductProperties,
  index: DevDistBazelIndex,
  descriptorCollector: DescriptorCollector,
) {
  val treeFiles = descriptorCollector.result.mapTo(HashSet()) { it.relativePath }
  val missing = sortedSetOf<String>()

  fun checkPluginDescriptors(mainModules: Collection<String>, productProperties: ProductProperties) {
    // The reference keeps every plugin module of a frontend and embeds the bodies of the refused ones, so it reads them.
    val contentModuleFilter = pluginContentModuleFilter(productProperties, createContentModuleFilter(
      project = outputProvider.findRequiredModule(productProperties.applicationInfoModule).project,
      productProperties = productProperties,
      outputProvider = outputProvider,
      bundledPluginModules = { getBundledPluginModules(productProperties, outputProvider) },
    ))
    for (layout in getPluginLayoutsByJpsModuleNames(mainModules, productProperties.productLayout)) {
      val closure = descriptorCollector.collectPluginClosure(
        mainModule = layout.mainModule,
        embedsContentModules = layout.pathsToScramble.isEmpty(),
        isContentModuleIncluded = { contentModule ->
          !contentModule.isOptional || contentModuleFilter.isOptionalModuleIncluded(contentModule.name.substringBeforeLast('/'), layout.mainModule)
        },
      )
      for (descriptor in closure.reached) {
        if (conventionalDescriptor(descriptor = descriptor, contentModules = closure.contentModules)) {
          continue
        }
        val moduleTarget = moduleRuleTarget(module = descriptor.moduleName, targets = index.targets)
        if (descriptor.relativePath !in treeFiles ||
            moduleTarget == null ||
            !descriptor.relativePath.startsWith("${index.packageDirectory(moduleTarget)}/")) {
          missing.add("${descriptor.relativePath} (module ${descriptor.moduleName})")
        }
      }
    }
  }

  val bundledPluginModules = getBundledPluginModules(properties, outputProvider)
  checkPluginDescriptors(bundledPluginModules, properties)

  val frontendProperties = properties.embeddedFrontendProperties?.invoke()
  if (frontendProperties != null) {
    val frontendBundledPluginModules = getBundledPluginModules(frontendProperties, outputProvider)
    checkPluginDescriptors(frontendBundledPluginModules.filterNot { it in bundledPluginModules }, frontendProperties)
  }
  check(missing.isEmpty()) {
    "The runtime module repository fragment of '${properties.platformPrefix ?: properties.applicationInfoModule}' reads" +
    " descriptors the project model tree does not hold:\n" + missing.joinToString(separator = "\n") { "  $it" }
  }
}

/**
 * The Maven names of the presigned native libraries the members [packedModuleNames] merge, sorted.
 *
 * `JarPackager` decides per library file, not per JPS library: the name [getLibNameBySourceFile] reads from a compiled
 * root, when [presignedNativeLibs] has it. The traversal and [isMerged] are the ones of [mergedLibraryTargetLabels].
 */
@ApiStatus.Internal
fun mergedPresignedNativeLibs(
  packedModuleNames: List<String>,
  findModule: (String) -> JpsModule,
  isMerged: (reportName: String?, jpsLibrary: JpsLibrary, owner: String?, packedModule: JpsModule) -> Boolean,
  presignedNativeLibs: Map<String, String>,
): Set<String> {
  val result = TreeSet<String>()
  for (packedModuleName in packedModuleNames) {
    val packedModule = findModule(packedModuleName)
    for (element in packedModule.dependenciesList.dependencies) {
      if (element !is JpsLibraryDependency || !isProductionRuntimeScope(element)) {
        continue
      }
      val parentReference = element.libraryReference.parentReference
      if (parentReference.resolve() is JpsGlobal) {
        continue
      }
      val jpsLibrary = element.library ?: continue
      val owner = (parentReference as? JpsModuleReference)?.moduleName
      if (!isMerged(distributionLibraryName(jpsLibrary), jpsLibrary, owner, packedModule)) {
        continue
      }
      for (file in jpsLibrary.getPaths(JpsOrderRootType.COMPILED)) {
        val libName = getLibNameBySourceFile(file)
        if (presignedNativeLibs.containsKey(libName)) {
          result.add(libName)
        }
      }
    }
  }
  return result
}

/**
 * The jars of [layout] that hold libraries and no module, keyed by destination: a project library that the layout
 * places in a jar of its own, such as `product-backend.jar`.
 *
 * The destination is the one `JarPackager` writes, see `computeProjectLibrariesSources`: the stated jar, or the library
 * name as `nameToJarFileName` spells it. A library whose destination holds a module is not here, because the residual
 * jar of that module merges it, see [layoutPlacedLibraryLabels].
 *
 * `JarPackager` keeps the native files of such a library inside the jar, presigned or not, and so does the rule for a
 * jar with no module. The generator fails on a library that Bazel does not name, and on a library that
 * `STANDALONE_SEPARATE` splits into one jar per file.
 */
private fun collectLibraryOnlyJars(
  productName: String,
  layout: PlatformLayout,
  moduleDestinations: Set<String>,
  index: DevDistBazelIndex,
): Map<String, ResidualPlatformJar> {
  val labelsByDestination = LinkedHashMap<String, MutableList<String>>()
  fun place(destination: String, libraryName: String) {
    val label = index.libraryLabel(jpsName = libraryName, owner = null, dependentIsCommunity = index.planPackageIsCommunity)
                ?: error("$productName: the layout places the library '$libraryName' in '$destination', and it has no Bazel target")
    labelsByDestination.computeIfAbsent(destination) { ArrayList() }.add(label)
  }
  // `JarPackager` packs the project libraries sorted by name.
  for (data in layout.getIncludedProjectLibraries().sortedBy { it.libraryName }) {
    val outPath = data.outPath
    val jarName = nameToJarFileName(data.libraryName)
    val destination = when {
      outPath == null -> jarName
      outPath.endsWith(".jar") -> outPath
      else -> "$outPath/$jarName"
    }
    if (destination in moduleDestinations) {
      continue
    }
    check(data.packMode == LibraryPackMode.STANDALONE_MERGED || outPath != null && outPath.endsWith(".jar")) {
      "$productName: the layout packs the library '${data.libraryName}' as ${data.packMode}, one jar per file"
    }
    place(destination = destination, libraryName = data.libraryName)
  }
  return labelsByDestination.mapValues { (_, labels) -> ResidualPlatformJar(modules = emptyList(), libraries = labels.distinct()) }
}

/**
 * The native signing mode of a dev distribution. `intellij_dev_dist.bzl` assembles unscrambled builds only, and the
 * packer writes a native tree as the unsigned bytes of the library jar. The plugin side states the same mode in
 * its native policy.
 */
@ApiStatus.Internal
val DEV_DIST_SIGN_NATIVE_FILE_MODE: SignNativeFileMode = SignNativeFileMode.DISABLED

/**
 * The labels of the project libraries [layout] places in the jar at [destination] itself, by `withProjectLibraries(names, destination)`.
 *
 * `JarPackager` sorts these libraries by name before it writes them, and the list keeps that order. [refuse] fails on
 * a library with no recorded label.
 */
private fun layoutPlacedLibraryLabels(
  destination: String,
  layout: PlatformLayout,
  index: DevDistBazelIndex,
  refuse: (String) -> Nothing,
): List<String> {
  return layout.getIncludedProjectLibraries().filter { it.outPath == destination }.sortedBy { it.libraryName }.map { library ->
    index.libraryLabel(jpsName = library.libraryName, owner = null, dependentIsCommunity = index.planPackageIsCommunity)
    ?: refuse("the layout-placed project library '${library.libraryName}' has no Bazel target")
  }
}

/** A dev-launch archive label, `@dev_launch_<group>//:files`. Bazel unpacks the same archive as `@dev_launch_<group>_extracted`. */
private val DEV_LAUNCH_ARCHIVE_LABEL = Regex("@(dev_launch_[a-z0-9_]+)//:files")

private const val EXECUTABLE_MODE_BITS = 0b001_001_001

/** The `HOST_PLATFORMS` entry of [os] and [arch]. */
private fun hostPlatformName(os: OsFamily, arch: JvmArchitecture): String = "${if (os == OsFamily.MACOS) "darwin" else os.osId}_${arch.name}"

/**
 * Derives the `platform_assets` component of one product from the platform layout's declared dist files, the `bin`
 * natives of `community/bin` and the product's [ProductProperties.additionalOsSpecificFiles].
 *
 * The component places explicit files, one label each, so a declaration is held to the shape one label can express:
 * one archive-tree asset per archive, whose source is a dev-launch archive, whose mappings name one archive path each
 * with no glob, and whose mode is stated. The file label is the archive path inside the unpacked repository.
 *
 * The natives and the additional files are executable, unless a declaration says otherwise. The Kotlin fragment that
 * placed them before copied them out of the project model tree, a Bazel output, so every copy had the executable bits.
 */
internal fun collectPlatformAssets(layout: PlatformLayout, properties: ProductProperties, index: DevDistBazelIndex): PlatformAssets {
  val files = TreeMap<String, MutableList<PlatformAssetFile>>()
  val archives = sortedSetOf<String>()
  for ((platform, declarations) in layout.distFileDeclarations) {
    val (os, arch) = platform
    val hostPlatform = hostPlatformName(os, arch)
    for (declaration in declarations) {
      val spec = declaration.layoutAssetSpec
      for (asset in spec.assets) {
        val transform = asset.transform
        require(transform != null && transform.kind == "archive-tree" && transform.stripComponents == 0 && asset.sources.size == 1) {
          "A platform dist file on $hostPlatform must be one archive-tree asset with no strip count: ${asset.destination}"
        }
        require(asset.mode != 0) { "A platform dist file on $hostPlatform must state its mode: ${asset.destination}" }
        val source = requireNotNull(spec.sources.get(asset.sources.single()) as? DevPluginLayoutAssetSource.BazelTarget) {
          "A platform dist file on $hostPlatform must read a dev-launch archive as a Bazel target: ${asset.destination}"
        }
        val group = requireNotNull(DEV_LAUNCH_ARCHIVE_LABEL.matchEntire(source.label)?.groupValues?.get(1)) {
          "A platform dist file on $hostPlatform must read a dev-launch archive, @dev_launch_<group>//:files: ${source.label}"
        }
        archives.add(source.label)
        for (mapping in transform.mappings) {
          require(mapping.pattern.none { it in "*?[{" }) {
            "A platform dist file on $hostPlatform must name one archive path, not a glob: ${mapping.pattern}"
          }
          val relative = mapping.pattern.split('/').drop(mapping.stripComponents).joinToString("/")
          require(relative.isNotEmpty()) { "A platform dist file on $hostPlatform strips its whole path: ${mapping.pattern}" }
          files.getOrPut(hostPlatform) { ArrayList() }.add(PlatformAssetFile(
            source = "@${group}_extracted//:${mapping.pattern}",
            path = listOf(asset.destination, mapping.destination, relative).filter { it.isNotEmpty() }.joinToString("/"),
            executable = asset.mode and EXECUTABLE_MODE_BITS != 0,
          ))
        }
      }
    }
  }

  val copyMethod = properties.javaClass.getMethod(
    "copyAdditionalOsSpecificFiles", Path::class.java, OsFamily::class.java, JvmArchitecture::class.java, BuildContext::class.java,
  )
  check(copyMethod.declaringClass == ProductProperties::class.java) {
    "${copyMethod.declaringClass.name} overrides copyAdditionalOsSpecificFiles, which a split dev distribution does not call." +
    " Declare the files with additionalOsSpecificFiles instead."
  }
  val communityRoot = index.communityRoot
  val binDir = communityRoot.resolve("bin")
  for (os in OsFamily.entries) {
    for (arch in JvmArchitecture.entries) {
      val hostPlatform = hostPlatformName(os, arch)
      val platformFiles = files.getOrPut(hostPlatform) { ArrayList() }
      for (native in nativeBinFiles(communityHome = communityRoot, os = os, arch = arch)) {
        platformFiles.add(PlatformAssetFile(
          source = index.planLabel("${COMMUNITY_REPOSITORY_PREFIX}bin:${binDir.relativize(native).invariantSeparatorsPathString}"),
          path = "bin/${native.fileName}",
          executable = true,
        ))
      }
      for (file in properties.additionalOsSpecificFiles(os, arch)) {
        platformFiles.add(PlatformAssetFile(source = index.planLabel(file.label), path = file.relativePath, executable = file.executable))
      }
      val duplicates = platformFiles.groupBy { it.path }.filterValues { it.size > 1 }.keys
      check(duplicates.isEmpty()) { "Two platform dist files on $hostPlatform share a destination: $duplicates" }
    }
  }
  return PlatformAssets(files = files, archives = archives.toList())
}

/** A module's own `jvm_library` target: the jar target minus `.jar`. */
internal fun moduleRuleTarget(module: String, targets: BazelTargetsInfo.TargetsFile): String? {
  return productionTarget(module = module, targets = targets)?.removeSuffix(MODULE_JAR_TARGET_SUFFIX)
}

/** A module's single test jar output label. */
internal fun testModuleJarTarget(module: String, targets: BazelTargetsInfo.TargetsFile): String? {
  return targets.modules.get(module)?.testTargets?.singleOrNull()
}

/** A module's test `jvm_library` target. */
internal fun testModuleRuleTarget(module: String, targets: BazelTargetsInfo.TargetsFile): String? {
  return testModuleJarTarget(module = module, targets = targets)?.removeSuffix(MODULE_JAR_TARGET_SUFFIX)
}

/** How the converter names a module's production jar target - `jvm_library` plus the implicit `.jar` output. */
private const val MODULE_JAR_TARGET_SUFFIX = ".jar"

/** A label in the community repository, as the JPS-to-Bazel converter writes it from the ultimate monorepo. */
internal const val COMMUNITY_REPOSITORY_PREFIX: String = "@community//"

/** The one production target the converter emits for a module, or null when it emits none or several. */
private fun productionTarget(module: String, targets: BazelTargetsInfo.TargetsFile): String? {
  return targets.modules.get(module)?.productionTargets?.singleOrNull()?.takeIf { it.endsWith(MODULE_JAR_TARGET_SUFFIX) }
}

private class MutablePayload {
  @JvmField val modules: MutableSet<String> = sortedSetOf()
  @JvmField val projectLibraries: MutableSet<String> = sortedSetOf()
  @JvmField val moduleSets: MutableSet<String> = sortedSetOf()
  @JvmField val runtimeClasspathModules: MutableSet<String> = sortedSetOf()

  /**
   * The payload modules the platform jar table packs under a jar name that is not their own.
   *
   * The handover set leaves them out. A packing target of such a module writes the `<module>.jar` that a plugin ships,
   * not the jar that the platform ships, so it owns a packing target this payload must not claim.
   */
  @JvmField val modulesPackedUnderAnotherName: MutableSet<String> = sortedSetOf()

  /** Adds one `[platform_jars]` row: the jar at [relativeOutputFile] under `lib/` and the [members] it merges. */
  fun addJar(relativeOutputFile: String, members: List<String>) {
    // One member list decides both what the payload declares and which of its modules the table packs under another
    // name. Reading the two from different lists is what let `intellij.java.rt` through - a member the second list
    // forgets is a member this payload wrongly claims.
    modules.addAll(members)

    // Marking one member too many is safe: the payload then declares a raw jar it may not read, where forgetting one
    // takes a jar out of the declaration that the fragment still packs. So a jar holding more than one member marks
    // every one of them, even the member it is named after: a packed jar carries only what its own recipe merges, and
    // one that stands in for a two-member jar would compose a jar missing the other module.
    val only = members.singleOrNull()
    if (only == null || relativeOutputFile != "$only.jar") {
      modulesPackedUnderAnotherName.addAll(members)
    }
  }

  fun freeze(name: String, packedContentModuleJars: List<String> = emptyList()): FragmentPayload {
    return FragmentPayload(
      name = name,
      modules = modules.toList(),
      projectLibraries = projectLibraries.toList(),
      moduleSets = moduleSets.toList(),
      runtimeClasspathModules = runtimeClasspathModules.toList(),
      packedContentModuleJars = packedContentModuleJars,
    )
  }
}

/**
 * One descriptor a plugin's patch reads: the load path a resolver asks for, the file that answers, and the JPS module
 * whose Bazel package exports it.
 *
 * The load path is the key, because that is what the action's request holds - `--plugin-descriptor=<load path>=<file>`.
 * The module travels beside it only so that the plan can name the Bazel package that exports the file.
 *
 * Every reached descriptor belongs in the plan, the conventional ones included. The convention the flat
 * `dev_dist_descriptors.bzl` relies on is a fact about a Bazel *probe*, which finds `<moduleName>.xml` and
 * `META-INF/plugin.xml` without being told. A per-plugin action declares its own inputs, so it has to name them all.
 */
internal data class ReachedDescriptor(
  @JvmField val loadPath: String,
  @JvmField val relativePath: String,
  @JvmField val moduleName: String,
  @JvmField val testOutput: Boolean = false,
)

/** One `<module/>` of a plugin descriptor's `<content>` block, in descriptor order. */
internal class DeclaredContentModule(
  @JvmField val name: String,
  @JvmField val isOptional: Boolean,
  @JvmField val loading: String? = null,
)

/**
 * The `xpointer` `extractNeededChildrenFor` assumes when an `xi:include` states none.
 *
 * It selects every child of the included root, which is why an include normally splices the whole file. Any other
 * pointer selects a subtree instead, and the content walk of [DescriptorCollector] does not model that.
 */
private const val DEFAULT_XPOINTER = "xpointer(/idea-plugin/*)"

/** What one bundled plugin's descriptor patch reads - see [DescriptorCollector.collectPluginClosure]. */
internal class PluginDescriptorClosure(
  @JvmField val mainModule: String,
  /** The plugin's own `META-INF/plugin.xml`. */
  @JvmField val descriptor: ReachedDescriptor,
  /**
   * Every other non-optional load path the descriptor action declares, in walk order.
   *
   * Includes every surviving content module's descriptor. The primary patch embeds those bodies only when the layout
   * embeds content modules; the classpath descriptor action embeds them either way.
   */
  @JvmField val reached: List<ReachedDescriptor>,
  /** The surviving `<module/>` names, in descriptor order. */
  @JvmField val contentModules: List<String>,
  /** The same surviving modules with their original loading rules. */
  @JvmField val declaredContentModules: List<DeclaredContentModule>,
  /** The file that answers each surviving content module's load path, keyed by content module name. */
  @JvmField val contentModuleFiles: Map<String, Path>,
  /**
   * A load path only a library jar answers, keyed by that load path.
   *
   * `DescriptorSearchPass.MODULE_OUTPUT` resolves it, and there is no source file to declare. The plan declares the
   * jar instead and states the entry to read out of it - see [ReachedLibraryDescriptor].
   */
  @JvmField val libraryJarDescriptors: Map<String, ReachedLibraryDescriptor>,
  /**
   * An `xi:include` of the descriptor whose contribution to the `<content>` order this walk cannot state.
   *
   * One shape reaches it: an include with an `xpointer` of its own. `extractNeededChildrenFor` then splices a subtree
   * instead of the included root's children, and the position a `<content>` block lands at is not the include's. A
   * plugin with one is held out, because the content order decides the patched bytes.
   */
  @JvmField val unmodelledContentIncludes: List<String>,
  /** Whether the root element of a descriptor file declares a `package`. The collector answers it from its parse of the file. */
  @JvmField val hasPackageAttribute: (Path) -> Boolean,
  /** The declared `<module/>` names the content filter refuses, in descriptor order. Only a generated closure states them. */
  @JvmField val refusedContentModules: List<String> = emptyList(),
)

/**
 * A descriptor no production source root holds, which a module library's jar answers instead.
 *
 * `intellij.libraries.kotlinc.analysis.api.k2` is the one case in this product: its own descriptor includes
 * `/META-INF/analysis-api/analysis-api-fir.xml`, which the Kotlin compiler ships inside the library jar. The plan
 * declares that jar and states the entry, and both producers read the entry out of the declared jar. The library and
 * the jar name travel here so the plan can name the jar's Bazel label; the load path is the entry name, because
 * `toLoadPath` already stripped the leading `/`.
 */
internal class ReachedLibraryDescriptor(
  @JvmField val loadPath: String,
  /** The module whose dependency list holds the library. */
  @JvmField val moduleName: String,
  /** The library's JPS name, empty for an unnamed module library. */
  @JvmField val libraryName: String,
  /** The jar's file name, which is how the plan picks the label out of the library's jar targets. */
  @JvmField val jarFileName: String,
  /** The entry's bytes, so the walk can follow this descriptor's own includes without reopening the jar. */
  @JvmField val content: ByteArray,
)

/** One zip entry's bytes, or `null` when the archive has no such entry. */
private fun readZipEntry(jar: Path, entry: String): ByteArray? {
  return FileSystems.newFileSystem(jar).use { zip ->
    val path = zip.getPath(entry)
    if (Files.exists(path)) Files.readAllBytes(path) else null
  }
}

/**
 * The library jar that holds [loadPath], searched from [module] through its production module dependencies.
 *
 * `findFileInModuleLibraryDependencies` asks one module's own libraries, and `findFileInModuleDependenciesRecursive`
 * is what widens that to the module's dependencies in `DescriptorSearchPass.MODULE_OUTPUT`. This is the two together,
 * and it answers the jar as well as the bytes - the plan needs the jar, and the walk needs the bytes.
 *
 * A test dependency is not walked, and neither is a library of one: a dev distribution packs production output.
 */
internal fun findDescriptorInLibraryJars(
  module: JpsModule,
  loadPath: String,
  outputProvider: ModuleOutputProvider,
): ReachedLibraryDescriptor? {
  for ((current, dependency) in descriptorLibraryDependencies(module, outputProvider)) {
    val reference = dependency.libraryReference
    val parent = reference.parentReference
    val roots = outputProvider.findDeclaredLibraryRoots(
      reference.libraryName,
      moduleLibraryModuleName = (parent as? JpsModuleReference)?.moduleName,
    )
    for (jar in roots) {
      val content = readZipEntry(jar = jar, entry = loadPath) ?: continue
      return ReachedLibraryDescriptor(
        loadPath = loadPath,
        moduleName = current.name,
        libraryName = reference.libraryName,
        jarFileName = jar.fileName.toString(),
        content = content,
      )
    }
  }
  return null
}

internal fun descriptorLibraryDependencies(
  module: JpsModule,
  outputProvider: ModuleOutputProvider,
): Sequence<Pair<JpsModule, JpsLibraryDependency>> = sequence {
  val queue = ArrayDeque(listOf(module))
  val visited = HashSet<String>()
  visited.add(module.name)
  while (true) {
    val current = queue.removeFirstOrNull() ?: break
    for (dependency in current.dependenciesList.dependencies) {
      if (dependency is JpsModuleDependency) {
        if (!JpsJavaExtensionService.getInstance().getDependencyExtension(dependency)!!.scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
          continue
        }
        val next = dependency.moduleReference.let { outputProvider.findModule(it.moduleName) } ?: continue
        if (visited.add(next.name)) {
          queue.addLast(next)
        }
        continue
      }
      if (dependency is JpsLibraryDependency) yield(current to dependency)
    }
  }
}

/** [getOrPut] that also keeps a `null` result, so a miss is computed once. Two threads may compute one key, and both get the first result. */
private inline fun <K : Any, V : Any> ConcurrentHashMap<K, Optional<V>>.getOrPutNullable(key: K, compute: () -> V?): V? {
  get(key)?.let { return it.orElse(null) }
  val value = Optional.ofNullable(compute())
  return (putIfAbsent(key, value) ?: value).orElse(null)
}

internal class DescriptorCollector(
  private val projectRoot: Path,
  private val outputProvider: ModuleOutputProvider,
  private val libraryDescriptorResolver: ((JpsModule, String) -> ReachedLibraryDescriptor?)? = null,
) {
  @JvmField val result: MutableSet<DescriptorFile> = sortedSetOf()
  private val visited = HashSet<String>()
  private val found = HashSet<String>()

  /** [ModuleOutputProvider.findFileInModuleSources] per (module, load path). Shared by the flat walk and the per-plugin one. */
  private val fileByKey = ConcurrentHashMap<String, Optional<Path>>()

  /** Test-source counterpart of [fileByKey], used only by Product DSL test plugins. */
  private val testFileByKey = ConcurrentHashMap<String, Optional<Path>>()

  /** [parseContentAndXIncludes] per file. Shared by both walks, which read most files twice. */
  private val parsedByFile = ConcurrentHashMap<Path, ContentParseResult>()

  /** The root element per file, for the `<content>` order walk. A reader of it changes nothing, so the walks of every product share it. */
  private val rootElementByFile = ConcurrentHashMap<Path, Element>()

  /**
   * Which module answered a load path that its declarant does not have, so that both walks credit the same module.
   *
   * The flat walk scans a path across the scope once ([searchedAcrossScope]); this memo is what lets the per-plugin
   * walk repeat the same resolution without repeating the scan.
   */
  private val scopeOwnerByLoadPath = ConcurrentHashMap<String, Optional<String>>()

  /** [findLibraryJarDescriptor] per (module, load path). */
  private val libraryDescriptorByKey = ConcurrentHashMap<String, Optional<ReachedLibraryDescriptor>>()

  /**
   * The modules an include may be resolved against when its own module does not have it, standing in for the
   * platform module list the layout searches. Sorted, so the module credited with a file does not depend on the
   * order products happen to be discovered in.
   */
  private val searchScope = sortedSetOf<String>()
  private val searchedAcrossScope = HashSet<String>()

  /**
   * The last resort, because [searchScope] is only an approximation of the layout's: it is built from what the
   * product model names - content modules, `xi:include` declarants, bundled plugins - and an include may still
   * name a file owned by a module outside that set. The provider answers which modules have the path, so the scan
   * costs no probe per module. Sorted, as [searchScope] is, so the credited module does not depend on the project order.
   */
  private fun allModulesWith(relativePath: String): List<String> {
    return outputProvider.findModulesWithSourceFile(relativePath).map { it.name }.sorted()
  }

  /**
   * Module-set XML is generated into a JPS resource root, but no source descriptor necessarily points to every
   * product-specific file. The runtime layout may still reach one from a generated product descriptor, so carry the
   * complete, small set in the project model tree instead of falling back to the owning module's compiled jar.
   */
  fun collectGeneratedModuleSetDescriptors(relativeRoot: String, moduleName: String) {
    val root = projectRoot.resolve(relativeRoot)
    // The community model has no module of the ultimate module sets, so the community half lists none of their files.
    if (!Files.exists(root) || outputProvider.findModule(moduleName) == null) return

    Files.walk(root).use { files ->
      files
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
        .sorted()
        .forEach { file ->
          result.add(DescriptorFile(relativePath = projectRoot.relativize(file).invariantSeparatorsPathString, moduleName = moduleName))
        }
    }
  }

  fun addToSearchScope(contentModuleName: String) {
    searchScope.add(contentModuleName.substringBeforeLast('/'))
  }

  /** The descriptor of a content module, wherever it was declared: `intellij.foo/bar` is `intellij.foo.bar.xml` in `intellij.foo`. */
  fun collectContentModule(contentModuleName: String) {
    collect(
      moduleName = contentModuleName.substringBeforeLast('/'),
      relativePath = contentModuleName.replace('/', '.') + ".xml",
    )
  }

  /**
   * One `xi:include`, resolved the way the layout resolves it: the declaring module first, then the rest of the
   * search scope. Scanning the scope costs one pass per distinct path that misses everywhere, so an include that no
   * module has - an optional one - is searched once and not again.
   */
  fun collectInclude(declaringModuleName: String, relativePath: String) {
    if (collect(moduleName = declaringModuleName, relativePath = relativePath)) {
      return
    }
    if (!searchedAcrossScope.add(relativePath)) {
      return
    }
    for (candidate in searchScope) {
      if (candidate != declaringModuleName && collect(moduleName = candidate, relativePath = relativePath)) {
        return
      }
    }
    for (candidate in allModulesWith(relativePath)) {
      if (collect(moduleName = candidate, relativePath = relativePath)) {
        return
      }
    }
  }

  /**
   * A start point of [prefetch]. [isInclude] is true for an `xi:include`, which [collectInclude] resolves across the
   * search scope when [moduleName] does not have it.
   */
  class PrefetchRoot(@JvmField val moduleName: String, @JvmField val relativePath: String, @JvmField val isInclude: Boolean) {
    companion object {
      /** The root that [collectContentModule] walks for [contentModuleName]. */
      fun contentModule(contentModuleName: String): PrefetchRoot {
        return PrefetchRoot(
          moduleName = contentModuleName.substringBeforeLast('/'),
          relativePath = contentModuleName.replace('/', '.') + ".xml",
          isInclude = false,
        )
      }
    }
  }

  /**
   * Finds and parses the files that a walk from [roots] reads, on concurrent threads, one level of includes at a time.
   *
   * The prefetch fills only the memos of file lookups and parses. It changes no walk state, so a later walk gives the
   * same result. When the declaring module lacks an include, the prefetch resolves it like [collectInclude].
   * It takes the first search-scope module with the file, then the first of all modules with it.
   * Call it after the search scope is complete.
   */
  fun prefetch(roots: Collection<PrefetchRoot>) {
    val seen = HashSet<String>()
    fun admit(root: PrefetchRoot): Boolean = seen.add("${root.isInclude}:${root.moduleName}/${root.relativePath}")
    var level = roots.filter(::admit)
    while (level.isNotEmpty()) {
      val next = ArrayList<PrefetchRoot>()
      val found: List<Pair<String, ContentParseResult>?> = level.mapConcurrent { root -> prefetchRoot(root) }
      for (answer in found) {
        val (owner, parsed) = answer ?: continue
        for (include in parsed.xIncludePaths) {
          next.add(PrefetchRoot(moduleName = owner, relativePath = include, isInclude = true))
        }
        for (contentModule in parsed.contentModules) {
          next.add(PrefetchRoot.contentModule(contentModule.name))
        }
      }
      level = next.filter(::admit)
    }
  }

  /** The module that answers [root] for the walk and its parsed file, or `null`. */
  private fun prefetchRoot(root: PrefetchRoot): Pair<String, ContentParseResult>? {
    val loadPath = root.relativePath
    fun hasFile(candidate: String): Boolean {
      return candidate != root.moduleName && findProductionSourceFile(moduleName = candidate, loadPath = loadPath) != null
    }
    val owner = when {
      findProductionSourceFile(moduleName = root.moduleName, loadPath = loadPath) != null -> root.moduleName
      root.isInclude -> searchScope.firstOrNull(::hasFile) ?: allModulesWith(loadPath).firstOrNull(::hasFile) ?: return null
      else -> return null
    }
    return owner to parse(checkNotNull(findProductionSourceFile(moduleName = owner, loadPath = loadPath)))
  }

  /** Whether [moduleName] has [relativePath] in its production sources; its includes and content are walked if so. */
  fun collect(moduleName: String, relativePath: String): Boolean {
    val key = "$moduleName/$relativePath"
    if (!visited.add(key)) {
      return found.contains(key)
    }

    // Not in the module's own production sources means a generated resource, or a file another module owns. The
    // unchanged jar read serves those, so leaving one out of the plan costs a pinned jar and nothing else.
    val file = findProductionSourceFile(moduleName = moduleName, loadPath = relativePath) ?: return false
    found.add(key)
    // A recorded miss gives way to the module that has the file, as a `null` value of a plain map gives way to `putIfAbsent`.
    scopeOwnerByLoadPath.compute(relativePath) { _, previous -> if (previous == null || previous.isEmpty) Optional.of(moduleName) else previous }

    // Most descriptors follow the convention Bazel can derive without being told: a module's own
    // `<moduleName>.xml`, and `META-INF/plugin.xml`, both at a production resource root. Listing those here would
    // make this file 3332 lines of churn for a large team to conflict over; the Bazel side probes for them
    // instead, and the plan carries only what that probe cannot find.
    if (relativePath != "$moduleName.xml" && relativePath != PLUGIN_XML_RELATIVE_PATH) {
      result.add(DescriptorFile(relativePath = projectRoot.relativize(file).invariantSeparatorsPathString, moduleName = moduleName))
    }
    val parsed = parse(file)
    for (include in parsed.xIncludePaths) {
      collectInclude(declaringModuleName = moduleName, relativePath = include)
    }
    // A descriptor's own `<content>` block: a bundled plugin's content modules are declared in its plugin.xml and
    // nowhere in the product spec, and the layout embeds their descriptors the same way it embeds the product's.
    for (contentModule in parsed.contentModules) {
      collectContentModule(contentModule.name)
    }
    return true
  }

  /**
   * The `product-modules.xml` of a module-based loader root and every file it includes.
   *
   * `BuildContext.getBundledPluginModules()` of an embedded frontend context reads them from sources
   * (`loadRawProductModules`), and a fragment that writes `product-info.json` calls it. No convention names them,
   * so the plan carries the whole closure. Loud when the root has no file: the full build fails on that too.
   */
  fun collectProductModules(rootModuleName: String) {
    val relativePath = "META-INF/$rootModuleName/product-modules.xml"
    val key = "$rootModuleName/$relativePath"
    if (!visited.add(key)) {
      return
    }
    val file = requireNotNull(findProductionSourceFile(moduleName = rootModuleName, loadPath = relativePath)) {
      "Module '$rootModuleName' has no $relativePath in its production sources"
    }
    found.add(key)
    result.add(DescriptorFile(relativePath = projectRoot.relativize(file).invariantSeparatorsPathString, moduleName = rootModuleName))
    for (included in productModulesIncludes(file)) {
      collectProductModules(included)
    }
  }

  /** The `<include>/<from-module>` names of a `product-modules.xml`, the way `ProductModulesXmlSerializer` reads them. */
  private fun productModulesIncludes(file: Path): List<String> {
    return rootElement(file).getChildren("include").mapNotNull { include ->
      include.getChildTextTrim("from-module")?.takeIf { it.isNotEmpty() }
    }
  }

  private fun findProductionSourceFile(moduleName: String, loadPath: String): Path? {
    return fileByKey.getOrPutNullable("$moduleName/$loadPath") {
      val module = outputProvider.findModule(moduleName) ?: return@getOrPutNullable null
      outputProvider.findFileInModuleSources(module = module, relativePath = loadPath, onlyProductionSources = true)
    }
  }

  private fun findTestSourceFile(moduleName: String, loadPath: String): Path? {
    return testFileByKey.getOrPutNullable("$moduleName/$loadPath") {
      val module = outputProvider.findModule(moduleName) ?: return@getOrPutNullable null
      outputProvider.findFileInModuleSources(module = module, relativePath = loadPath, onlyProductionSources = false)
    }
  }

  /** Whether the root element of the descriptor [file] declares a `package`. The walks parsed the file already. */
  fun hasPackageAttribute(file: Path): Boolean = parse(file).hasPackage

  /** [findDescriptorInLibraryJars] per (module, load path). Each call opens the library jars, and every product asks again. */
  private fun findLibraryJarDescriptor(module: JpsModule, loadPath: String): ReachedLibraryDescriptor? {
    return libraryDescriptorByKey.getOrPutNullable("${module.name}/$loadPath") {
      findDescriptorInLibraryJars(module = module, loadPath = loadPath, outputProvider = outputProvider)
    }
  }

  private fun parse(file: Path): ContentParseResult = parsedByFile.getOrPut(file) {
    parseContentAndXIncludes(input = Files.readAllBytes(file), locationSource = file.toString())
  }

  private fun rootElement(file: Path): Element = rootElementByFile.getOrPut(file) { JDOMUtil.load(file) }

  private fun reached(moduleName: String, loadPath: String, file: Path, testOutput: Boolean = false): ReachedDescriptor = ReachedDescriptor(
    loadPath = loadPath,
    relativePath = projectRoot.relativize(file).invariantSeparatorsPathString,
    moduleName = moduleName,
    testOutput = testOutput,
  )

  /**
   * The load path resolved the way the layout resolves it, and the module that answered.
   *
   * The declaring module first, then the scope, then every module - the order of
   * `XIncludeElementResolverImpl.resolveElement`, and the same order [collectInclude] walks. The scope answer is
   * memoized, so the scan runs once per path here as it does there.
   */
  private fun resolve(declaringModuleName: String, loadPath: String): Pair<String, Path>? {
    findProductionSourceFile(moduleName = declaringModuleName, loadPath = loadPath)?.let { return declaringModuleName to it }
    val owner = scopeOwnerByLoadPath.getOrPutNullable(loadPath) {
      fun hasFile(candidate: String): Boolean {
        return candidate != declaringModuleName && findProductionSourceFile(moduleName = candidate, loadPath = loadPath) != null
      }
      // The scan of every module runs only when the scope misses.
      searchScope.firstOrNull(::hasFile) ?: allModulesWith(loadPath).firstOrNull(::hasFile)
    } ?: return null
    val file = findProductionSourceFile(moduleName = owner, loadPath = loadPath) ?: return null
    return owner to file
  }

  /**
   * What one bundled plugin's descriptor action declares, as opposed to what the flat walk reaches.
   *
   * A second walk and not a partition of the flat one: the flat walk merges every product's closure and stops at the
   * first visit of a file, so a file another plugin reached first would leave this plugin's list short.
   *
   * Every surviving content module's descriptor joins [PluginDescriptorClosure.reached]. The primary patch embeds the
   * body only when [embedsContentModules] is true. The classpath descriptor action embeds the survivors either way, so
   * a layout that embeds no content module still declares them. [PluginDescriptorClosure.contentModuleFiles] holds
   * the survivor files only when the primary patch embeds, because that map drives `separate-jar` on the embedded body.
   *
   * Loud where the flat walk is quiet. A load path with no production source file leaves the flat plan silently
   * (`collect` returns `false`), and the patch then fails at assembly time. Here it fails with the plugin's name and
   * the path, unless the declaring descriptor states an `xi:fallback` or a condition for it - the two cases
   * `resolveElement` answers with `null`.
   *
   * The `<content>` order is read the way `collectContentModules` reads it - see [PluginClosureWalk.appendContentModules].
   */
  fun collectPluginClosure(
    mainModule: String,
    embedsContentModules: Boolean,
    testPlugin: TestPluginSpec? = null,
    rootLoadPath: String = PLUGIN_XML_RELATIVE_PATH,
    isContentModuleIncluded: (DeclaredContentModule) -> Boolean,
  ): PluginDescriptorClosure {
    val mainFile = when (testPlugin) {
      null -> requireNotNull(findProductionSourceFile(moduleName = mainModule, loadPath = rootLoadPath)) {
        "Module '$mainModule' has no $rootLoadPath in its production sources, so no action can produce its descriptor"
      }
      else -> {
        require(rootLoadPath == PLUGIN_XML_RELATIVE_PATH) {
          "Test plugin '$mainModule' uses the unsupported root descriptor '$rootLoadPath'"
        }
        val declared = projectRoot.resolve(testPlugin.pluginXmlPath).normalize()
        require(declared.startsWith(projectRoot) && Files.isRegularFile(declared)) {
          "Test plugin '$mainModule' declares a missing descriptor '${testPlugin.pluginXmlPath}'"
        }
        val moduleFile = findTestSourceFile(moduleName = mainModule, loadPath = PLUGIN_XML_RELATIVE_PATH)
        require(moduleFile?.normalize() == declared) {
          "Test plugin '$mainModule' does not own the Product DSL descriptor '${testPlugin.pluginXmlPath}'"
        }
        declared
      }
    }
    val walk = PluginClosureWalk(mainModule = mainModule)
    walk.visited.add(rootLoadPath)
    val mainParsed = parse(mainFile)
    walk.followIncludes(declaringModuleName = mainModule, declaringFile = mainFile, parsed = mainParsed)

    val declared = ArrayList<DeclaredContentModule>()
    walk.appendContentModules(declaringModuleName = mainModule, file = mainFile, out = declared)
    val declaredContentModules = declared.filter(isContentModuleIncluded)
    val contentModules = declaredContentModules.map { it.name }
    val contentModuleFiles = LinkedHashMap<String, Path>()
    for (contentModule in contentModules) {
      // `contentModuleNameToDescriptorFileName`: `intellij.foo/bar` is `intellij.foo.bar.xml` in `intellij.foo`.
      val loadPath = contentModule.replace('/', '.') + ".xml"
      val sourceModuleName = contentModule.substringBeforeLast('/')
      val resolved = requireNotNull(
        walk.follow(declaringModuleName = sourceModuleName, loadPath = loadPath)
        ?: testPlugin?.let { walk.followTest(declaringModuleName = sourceModuleName, loadPath = loadPath) }
      ) {
        val sourceKind = if (testPlugin == null) "production" else "production or test"
        "Plugin '$mainModule' declares content module '$contentModule', whose descriptor '$loadPath' has no $sourceKind" +
        " source file in '$sourceModuleName' or in any other module"
      }
      // The primary patch reads the file only when it embeds the body. The classpath action still needs the label.
      if (embedsContentModules) {
        contentModuleFiles.put(contentModule, resolved)
      }
    }
    return PluginDescriptorClosure(
      mainModule = mainModule,
      descriptor = reached(
        moduleName = mainModule,
        loadPath = rootLoadPath,
        file = mainFile,
        testOutput = testPlugin != null,
      ),
      reached = walk.reached.values.toList(),
      contentModules = contentModules,
      declaredContentModules = declaredContentModules,
      contentModuleFiles = contentModuleFiles,
      libraryJarDescriptors = walk.libraryJarDescriptors,
      unmodelledContentIncludes = walk.unmodelledContentIncludes,
      hasPackageAttribute = ::hasPackageAttribute,
    )
  }

  /**
   * Collects an embedded descriptor or a product descriptor from generated XML without reading a checked-in root file.
   *
   * The generated text inlines the module sets and the deprecated includes. An optional or a conditional include stays,
   * and it resolves to nothing at build time. Any other root include is followed, like an include of a checked-in
   * descriptor. [isContentModuleIncluded] is the content filter of the product. A refused module goes to
   * [PluginDescriptorClosure.refusedContentModules], and the walk reads no descriptor of it.
   */
  fun collectGeneratedPluginClosure(
    mainModule: String,
    rootLoadPath: String,
    sourceRelativePath: String,
    xml: String,
    isContentModuleIncluded: (DeclaredContentModule) -> Boolean = { true },
  ): PluginDescriptorClosure {
    val root = JDOMUtil.load(xml)
    val walk = PluginClosureWalk(mainModule = mainModule)
    walk.visited.add(rootLoadPath)
    walk.followGeneratedIncludes(declaringModuleName = mainModule, root = root, location = sourceRelativePath)
    val declared = ArrayList<DeclaredContentModule>()
    walk.appendContentModules(
      declaringModuleName = mainModule,
      root = root,
      out = declared,
    )
    val refused = declared.filterNot(isContentModuleIncluded)
    val kept = declared.filter(isContentModuleIncluded)
    val contentModules = kept.map { it.name }
    val contentModuleFiles = LinkedHashMap<String, Path>()
    for (contentModule in contentModules) {
      val loadPath = contentModule.replace('/', '.') + ".xml"
      val declaredModuleName = contentModule.substringBeforeLast('/')
      val resolved = requireNotNull(walk.follow(declaringModuleName = declaredModuleName, loadPath = loadPath)) {
        "Generated descriptor '$sourceRelativePath' embeds '$contentModule', but '$loadPath' has no production source file"
      }
      contentModuleFiles.put(contentModule, resolved)
    }
    return PluginDescriptorClosure(
      mainModule = mainModule,
      descriptor = ReachedDescriptor(
        loadPath = rootLoadPath,
        relativePath = sourceRelativePath,
        moduleName = mainModule,
      ),
      reached = walk.reached.values.toList(),
      contentModules = contentModules,
      declaredContentModules = kept,
      contentModuleFiles = contentModuleFiles,
      libraryJarDescriptors = walk.libraryJarDescriptors,
      unmodelledContentIncludes = walk.unmodelledContentIncludes,
      hasPackageAttribute = ::hasPackageAttribute,
      refusedContentModules = refused.map { it.name },
    )
  }

  /** One plugin's walk state. Keyed by load path, because that is what the action's request is keyed by. */
  private inner class PluginClosureWalk(private val mainModule: String) {
    @JvmField val visited: MutableSet<String> = HashSet()
    @JvmField val reached: MutableMap<String, ReachedDescriptor> = LinkedHashMap()
    /** A load path that only a library jar answers - see [ReachedLibraryDescriptor]. */
    @JvmField val libraryJarDescriptors: MutableMap<String, ReachedLibraryDescriptor> = LinkedHashMap()

    /** See [PluginDescriptorClosure.unmodelledContentIncludes]. */
    @JvmField val unmodelledContentIncludes: MutableList<String> = ArrayList()

    /** The load paths [appendContentModules] has already descended into, so a cyclic include ends the walk. */
    private val contentWalkVisited = HashSet<String>()

    /** Resolves [loadPath], records it, and walks its own includes. Returns the file, or `null` when nothing has it. */
    fun follow(declaringModuleName: String, loadPath: String): Path? {
      val resolved = resolve(declaringModuleName = declaringModuleName, loadPath = loadPath)
      if (resolved == null) {
        return null
      }
      val (owner, file) = resolved
      if (!visited.add(loadPath)) {
        return file
      }
      reached.put(loadPath, reached(moduleName = owner, loadPath = loadPath, file = file))
      followIncludes(declaringModuleName = owner, declaringFile = file, parsed = parse(file))
      return file
    }

    /** Resolves a test plugin's content descriptor from the test resources of its test-only JPS module. */
    fun followTest(declaringModuleName: String, loadPath: String): Path? {
      val file = findTestSourceFile(moduleName = declaringModuleName, loadPath = loadPath) ?: return null
      if (!visited.add(loadPath)) {
        return file
      }
      reached.put(loadPath, reached(moduleName = declaringModuleName, loadPath = loadPath, file = file, testOutput = true))
      followIncludes(declaringModuleName = declaringModuleName, declaringFile = file, parsed = parse(file))
      return file
    }

    /**
     * The `<module/>` names of the resolved descriptor's `<content>` blocks, in the order the patch leaves them.
     *
     * `collectContentModules` reads `rootElement.getChildren("content")` **after** `resolveIncludes` has run, and
     * `resolveXIncludeElement` replaces an `xi:include` with the included root's children **at the include's own
     * position**. So a `<content>` block an included file states belongs where the include sat, and a walk that reads
     * the declaring file's own `<content>` blocks alone is both short and out of order.
     *
     * JDOM here, and not [parseContentAndXIncludes]: the parser reports the content modules and the include hrefs as
     * two flat lists and states no interleaving. The loading rule is read as the raw `loading` attribute, which is what
     * `isOptionalLoadingRule` reads.
     *
     * Only a root-level include contributes. An include deeper in the tree has its children spliced there, so its
     * `<content>` block is not a child of the root and `collectContentModules` never sees it.
     */
    fun appendContentModules(declaringModuleName: String, file: Path, out: MutableList<DeclaredContentModule>) {
      appendContentModulesOf(root = rootElement(file), declaringModuleName = declaringModuleName, out = out)
    }

    fun appendContentModules(declaringModuleName: String, root: Element, out: MutableList<DeclaredContentModule>) {
      appendContentModulesOf(root = root, declaringModuleName = declaringModuleName, out = out)
    }

    private fun appendContentModulesOf(root: Element, declaringModuleName: String, out: MutableList<DeclaredContentModule>) {
      for (child in root.children) {
        if (child.name == "content") {
          for (moduleElement in child.getChildren("module")) {
            val name = requireNotNull(moduleElement.getAttributeValue("name")) {
              "A <module/> of plugin '$mainModule' states no name"
            }
            val loadingRule = moduleElement.getAttributeValue("loading")
            out.add(DeclaredContentModule(
              name = name,
              isOptional = loadingRule != "required" && loadingRule != "embedded",
              loading = loadingRule,
            ))
          }
          continue
        }
        if (child.name != "include" || child.namespace != JDOMUtil.XINCLUDE_NAMESPACE) {
          continue
        }
        val href = child.getAttributeValue("href") ?: continue
        // `resolveElement` answers `null` for an optional or a dynamic include, so it contributes no child at all.
        if (child.getChild("fallback", child.namespace) != null ||
            child.getAttribute("includeIf") != null ||
            child.getAttribute("includeUnless") != null) {
          continue
        }
        if (child.getAttributeValue("xpointer").let { it != null && it != DEFAULT_XPOINTER }) {
          unmodelledContentIncludes.add(href)
          continue
        }
        val loadPath = LoadPathUtil.toLoadPath(href)
        if (!contentWalkVisited.add(loadPath)) {
          continue
        }
        val (owner, includedFile) = resolve(declaringModuleName = declaringModuleName, loadPath = loadPath) ?: continue
        val included = rootElement(includedFile)
        // `extractNeededChildrenFor` returns nothing when the included root is not the pointer's root tag, so such an
        // include splices no child and contributes no content module.
        if (included.name != "idea-plugin") {
          continue
        }
        appendContentModulesOf(root = included, declaringModuleName = owner, out = out)
      }
    }

    /**
     * [followIncludes] for a generated root, which is no file yet. An include with an `xi:fallback` or a condition
     * contributes nothing, because `resolveElement` answers it with `null`.
     */
    fun followGeneratedIncludes(declaringModuleName: String, root: Element, location: String) {
      for (child in root.children) {
        if (child.name != "include" || child.namespace != JDOMUtil.XINCLUDE_NAMESPACE) {
          continue
        }
        if (child.getChild("fallback", child.namespace) != null ||
            child.getAttribute("includeIf") != null ||
            child.getAttribute("includeUnless") != null) {
          continue
        }
        val href = child.getAttributeValue("href") ?: continue
        val loadPath = LoadPathUtil.toLoadPath(href)
        if (loadPath in visited || follow(declaringModuleName = declaringModuleName, loadPath = loadPath) != null) {
          continue
        }
        val declaringModule = outputProvider.findModule(declaringModuleName)
        if (declaringModule != null && followLibraryJarDescriptor(module = declaringModule, loadPath = loadPath)) {
          continue
        }
        error(
          "Generated descriptor '$location' reads '$loadPath' through an xi:include, and neither a production source" +
          " root nor a module library jar has that file"
        )
      }
    }

    fun followIncludes(declaringModuleName: String, declaringFile: Path, parsed: ContentParseResult) {
      for (include in parsed.xIncludePaths) {
        if (include in visited) {
          continue
        }
        if (follow(declaringModuleName = declaringModuleName, loadPath = include) != null) {
          continue
        }
        if (isOptionalInclude(declaringFile = declaringFile, loadPath = include)) {
          continue
        }
        // The one remaining legitimate route: a descriptor that ships inside a library jar, which belongs to
        // `DescriptorSearchPass.MODULE_OUTPUT` alone and has no source file to declare. The plan cannot name it as a
        // file, so the plugin is held out rather than planned wrong - and the reason names the path.
        val declaringModule = outputProvider.findModule(declaringModuleName)
        if (declaringModule != null && followLibraryJarDescriptor(module = declaringModule, loadPath = include)) {
          continue
        }
        error(
          "Plugin '$mainModule' reads '$include' through an xi:include in" +
          " '${projectRoot.relativize(declaringFile).invariantSeparatorsPathString}', and neither a production source" +
          " root nor a module library jar has that file. Declare an xi:fallback there, or add the file"
        )
      }
    }

    /**
     * Records a descriptor only a library jar answers, and walks its own includes.
     *
     * That walk is the part a first draft leaves out. `analysis-api-fir.xml` includes three more descriptors, each in a
     * library jar of a module the declarant depends on, and those include two more. The action seeds its cache from the
     * declared jars, so it needs the whole chain and not the first link.
     *
     * The search starts at the declaring module and walks its production module dependencies, because that is the
     * bound `findFileInModuleDependenciesRecursive` walks in `DescriptorSearchPass.MODULE_OUTPUT`. `resolveElement`
     * searches the plugin's whole scope instead of one declarant, so a load path two jars answer could be credited to
     * the other one. No path of this product is in two jars.
     */
    fun followLibraryJarDescriptor(module: JpsModule, loadPath: String): Boolean {
      val descriptor = if (libraryDescriptorResolver == null) {
        findLibraryJarDescriptor(module = module, loadPath = loadPath)
      }
      else libraryDescriptorResolver.invoke(module, loadPath)
      if (descriptor == null) return false
      if (!visited.add(loadPath)) {
        return true
      }
      libraryJarDescriptors.put(loadPath, descriptor)
      val parsed = parseContentAndXIncludes(input = descriptor.content, locationSource = "${descriptor.jarFileName}!/$loadPath")
      val owner = outputProvider.findRequiredModule(descriptor.moduleName)
      for (include in parsed.xIncludePaths) {
        if (include in visited) {
          continue
        }
        if (follow(declaringModuleName = descriptor.moduleName, loadPath = include) != null) {
          continue
        }
        if (followLibraryJarDescriptor(module = owner, loadPath = include)) {
          continue
        }
        error(
          "Plugin '$mainModule' reads '$include' through an xi:include in '${descriptor.jarFileName}!/$loadPath'," +
          " and neither a production source root nor a library jar of '${descriptor.moduleName}' and its dependencies" +
          " has that file"
        )
      }
      return true
    }
  }

  /**
   * Whether the `xi:include` of [loadPath] in [declaringFile] states an `xi:fallback` or a condition.
   *
   * Those are the two cases `resolveXIncludeElement` hands to `resolveElement` as optional or dynamic, and
   * `resolveElement` then answers `null` without searching. `parseContentAndXIncludes` reports the href alone, so the
   * file is read again here - only on the failure path, which is rare.
   */
  private fun isOptionalInclude(declaringFile: Path, loadPath: String): Boolean =
    isOptionalIncludeIn(element = JDOMUtil.load(declaringFile), loadPath = loadPath)

  private fun isOptionalIncludeIn(element: Element, loadPath: String): Boolean {
    val isThisInclude = element.name == "include" &&
                        element.namespace == JDOMUtil.XINCLUDE_NAMESPACE &&
                        element.getAttributeValue("href")?.let { LoadPathUtil.toLoadPath(it) } == loadPath
    return isThisInclude &&
           (element.getChild("fallback", element.namespace) != null ||
            element.getAttribute("includeIf") != null ||
            element.getAttribute("includeUnless") != null) ||
           element.children.any { isOptionalIncludeIn(element = it, loadPath = loadPath) }
  }
}

private fun renderDescriptors(files: List<DescriptorFile>, half: DevDistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# The shared project model tree carries every content module descriptor and every bundled plugin descriptor,\n")
  append("# so a reader of the tree reads files instead of opening module jars. Only the runtime module repository action\n")
  append("# and the references read the tree - see `intellij_project_model_tree`.\n")
  append("#\n")
  append("# Bazel finds most of them by convention: `<moduleName>.xml` and `META-INF/plugin.xml` at a production\n")
  append("# resource root. This file is the remainder - descriptors reached only through an `xi:include`, whose name\n")
  append("# the convention cannot predict. Listing the conventional ones here too would make this a 3000-line file\n")
  append("# that every model change rewrites, and a standing merge conflict for everyone.\n")
  append("#\n")
  append("# Each entry is (project-relative path, JPS module name). The module names the Bazel package that\n")
  append("# exports the file, which only the JPS-to-Bazel converter knows.\n")
  append("#\n")
  append("# A stale entry here is dropped rather than an error: this list is probed during module-extension\n")
  append("# evaluation, so failing would make the very tool that regenerates it unbuildable.\n")
  append("DEV_DIST_EXTRA_DESCRIPTOR_FILES = [\n")
  for (file in files) {
    append("    (\"").append(file.relativePath).append("\", \"").append(file.moduleName).append("\"),\n")
  }
  append("]\n")
}

/**
 * [communityProducts] are the community products of [half], see [devDistCommunityProducts]. The half that renders second
 * names them in `DEV_DIST_COMMUNITY_PRODUCTS`, so its binder composes the community rows for a distribution of one.
 */
private fun renderPartition(products: List<ProductFragmentPlan>, half: DevDistHalf, communityProducts: Set<String>): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# The plan of every split product: the facts that its dev distribution reads. Bazel consumes this plan directly\n")
  append("# and fails when the requested product is absent.\n")
  append("#\n")
  if (half.writesCommunityPackages) {
    append("# What the fragments of a product declare as their inputs lives in `dev_dist_fragment_inputs.bzl`, which churns\n")
    append("# with every model change.\n")
  }
  else {
    append("# The facts that only the reference fragments of the gates read live in `dev_dist_reference_plan.bzl`. What those\n")
    append("# fragments declare as their inputs lives in `dev_dist_fragment_inputs.bzl`, which churns with every model change.\n")
  }
  append("#\n")
  append("# There is deliberately no content digest here. An action key already covers everything that decides what the\n")
  append("# action packs, so a plan change rebuilds only the actions it changes. Overlapping ownership cannot survive a\n")
  append("# compose either: the composer fails on every file two components both provide.\n")
  append("#\n")
  append("# A field value that every product states alike is one private constant named after the field, and one that\n")
  append("# several products state alike is named after the field and the first of them. Products with equal plans\n")
  append("# share one private `_PLAN_<first product>` struct. The readers take a product's struct by its key, so a\n")
  append("# shared value reads like an inline one.\n")
  val sharedFields = sharePlanFieldBodies(products)
  for ((field, name) in sharedFields) {
    append(name).append(" = ").append(field.body).append("\n\n")
  }
  // The body of every product at the top-level indent, and the products that render each body.
  val bodyOwners = LinkedHashMap<String, MutableList<ProductFragmentPlan>>()
  for (product in products) {
    bodyOwners.computeIfAbsent(renderPlanFields(product, indent = "    ", sharedFields)) { ArrayList() }.add(product)
  }
  val sharedBodyNames = HashMap<String, String>()
  for ((body, owners) in bodyOwners) {
    if (owners.size < 2) continue
    val name = "_PLAN_" + owners.first().platformPrefix
    for (owner in owners) {
      sharedBodyNames.put(owner.platformPrefix, name)
    }
    append(name).append(" = struct(\n").append(body).append(")\n\n")
  }
  if (!half.writesCommunityPackages) {
    append("# The community products: the keys that both registries state with one product class. The community half plans\n")
    append("# them, so the plan tables of this half have no row of them. The component map states only their additional tier,\n")
    append("# and the rows file keeps their run configurations. A distribution of a community product composes the community\n")
    append("# platform set and the community bundled plugins, see `intellij_dev_dist_declarations`.\n")
    if (communityProducts.isEmpty()) {
      append("DEV_DIST_COMMUNITY_PRODUCTS = []\n")
    }
    else {
      append("DEV_DIST_COMMUNITY_PRODUCTS = [\n")
      for (product in communityProducts) {
        append("    \"").append(product).append("\",\n")
      }
      append("]\n")
    }
    append("\n")
  }
  append("DEV_DIST_PLANS = {\n")
  for (product in products) {
    append("    \"").append(product.platformPrefix).append("\": ")
    val sharedName = sharedBodyNames.get(product.platformPrefix)
    if (sharedName == null) {
      append("struct(\n").append(renderPlanFields(product, indent = "        ", sharedFields)).append("    ),\n")
    }
    else {
      append(sharedName).append(",\n")
    }
  }
  append("}\n")
  append("\n")
  append("# The launch model of every product, which its `platform_resources` component renders. A model file takes the\n")
  append("# case-safe name of its product, so two products never share one file on a case-insensitive disk. The map is\n")
  append("# apart from the plans, so products with equal launch facts still share one plan.\n")
  append("DEV_DIST_LAUNCH_MODELS = {\n")
  for (product in products) {
    val path = product.launchModelRelativePath
    append("    \"").append(product.platformPrefix).append("\": \"//").append(path.substringBeforeLast('/')).append(':').append(path.substringAfterLast('/')).append("\",\n")
  }
  append("}\n")
  append("\n")
  append("# The application info sources of every product. The product files action and the plugin descriptor actions read\n")
  append("# these files. So an edit of a version, a suffix, a release date or the EAP flag changes no generated file.\n")
  append("DEV_DIST_APPLICATION_INFOS = {\n")
  for (product in products) {
    val sources = product.applicationInfoSources
    append("    \"").append(product.platformPrefix).append("\": struct(source = ").append(quoteStarlarkString(sources.source))
    sources.host?.let { append(", host = ").append(quoteStarlarkString(it)) }
    if (sources.replacements.isNotEmpty()) {
      append(", replacements = ").append(sources.replacements.joinToString(", ", "[", "]", transform = ::quoteStarlarkString))
    }
    append("),\n")
  }
  append("}\n")
  append("\n")
  append("# The `lib/` jar order of the platform as a rule with two lists, for every product with the runtime module repository\n")
  append("# fragment. The core plugin lists the jars of `first` in that order. Then it lists every other jar with a module,\n")
  append("# sorted by its smallest member module name. Then it lists the jars of `last` in that order. Bazel knows the member\n")
  append("# names, but not the two lists. Equal lists are one private struct, named as a shared plan field is named.\n")
  appendPlatformJarOrders(products.mapNotNull { product -> product.platformJarOrder?.let { product.platformPrefix to it } })
}

/** A jar list of a [PlatformJarOrder] as [appendNameList] writes it, or `[]` when it is empty. */
private fun StringBuilder.appendJarList(field: String, jars: List<String>, indent: String) {
  if (jars.isEmpty()) {
    append(indent).append(field).append(" = [],\n")
  }
  else {
    appendNameList(field, jars, indent = indent)
  }
}

/**
 * `DEV_DIST_PLATFORM_JAR_ORDERS` of [orders], in the given product order. A struct that two or more products state alike
 * is one private constant. It is `_PLATFORM_JAR_ORDER` when every product states it, and `_PLATFORM_JAR_ORDER_<first
 * product>` otherwise. This is the naming of [sharePlanFieldBodies].
 */
private fun StringBuilder.appendPlatformJarOrders(orders: List<Pair<String, PlatformJarOrder>>) {
  val users = LinkedHashMap<PlatformJarOrder, MutableList<String>>()
  for ((product, order) in orders) {
    users.computeIfAbsent(order) { ArrayList() }.add(product)
  }
  val shared = users.filterValues { it.size > 1 }
  val sharedNames = HashMap<PlatformJarOrder, String>()
  for ((order, owners) in shared) {
    val name = if (users.size == 1) "_PLATFORM_JAR_ORDER" else "_PLATFORM_JAR_ORDER_" + owners.first()
    sharedNames.put(order, name)
    append(name).append(" = struct(\n")
    appendJarList("first", order.first, indent = INDENT)
    appendJarList("last", order.last, indent = INDENT)
    append(")\n\n")
  }
  append("DEV_DIST_PLATFORM_JAR_ORDERS = {\n")
  for ((product, order) in orders) {
    append(INDENT).append("\"").append(product).append("\": ")
    val sharedName = sharedNames.get(order)
    if (sharedName == null) {
      append("struct(\n")
      appendJarList("first", order.first, indent = INDENT + INDENT)
      appendJarList("last", order.last, indent = INDENT + INDENT)
      append(INDENT).append("),\n")
    }
    else {
      append(sharedName).append(",\n")
    }
  }
  append("}\n")
}

private fun renderReferencePlan(products: List<ProductFragmentPlan>, half: DevDistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# The facts of every split product that only the reference fragments of the `jars`, `replay` and `runtime-repo`\n")
  append("# gates read. No distribution reads them. `build_modules` are the modules whose product properties the reference\n")
  append("# assembler evaluates. `platform_asset_archives` are the archives that a reference does not preload, because the\n")
  append("# `platform_assets` component places their files.\n")
  append("#\n")
  append("# Products with equal facts share one private `_REFERENCE_PLAN_<first product>` struct.\n")
  val owners = LinkedHashMap<String, MutableList<String>>()
  for (product in products) {
    owners.computeIfAbsent(renderReferencePlanFields(product, indent = "    ")) { ArrayList() }.add(product.platformPrefix)
  }
  val sharedNames = HashMap<String, String>()
  for ((body, productNames) in owners) {
    if (productNames.size < 2) continue
    val name = "_REFERENCE_PLAN_" + productNames.first()
    for (productName in productNames) {
      sharedNames.put(productName, name)
    }
    append(name).append(" = struct(\n").append(body).append(")\n\n")
  }
  append("DEV_DIST_REFERENCE_PLANS = {\n")
  for (product in products) {
    append("    \"").append(product.platformPrefix).append("\": ")
    val sharedName = sharedNames.get(product.platformPrefix)
    if (sharedName == null) {
      append("struct(\n").append(renderReferencePlanFields(product, indent = "        ")).append("    ),\n")
    }
    else {
      append(sharedName).append(",\n")
    }
  }
  append("}\n")
}

private fun renderReferencePlanFields(product: ProductFragmentPlan, indent: String): String = buildString {
  appendNameList("build_modules", product.buildModules, indent = indent)
  appendNameList("platform_asset_archives", product.platformAssets.archives, indent = indent)
}

/** The names of [product] that the reference inputs resolve, see [resolveDevDistReferenceInputs]. */
private fun referenceProduct(product: ProductFragmentPlan): DevDistReferenceProduct {
  fun payload(name: String): DevDistReferencePayload? {
    val payload = product.payloads.singleOrNull { it.name == name } ?: return null
    return DevDistReferencePayload(
      modules = payload.modules,
      projectLibraries = payload.projectLibraries,
      moduleSets = payload.moduleSets,
      runtimeClasspathModules = payload.runtimeClasspathModules,
    )
  }
  return DevDistReferenceProduct(
    platformPrefix = product.platformPrefix,
    buildModules = product.buildModules,
    embeddedFrontend = product.embeddedFrontend,
    platformLib = payload(PLATFORM_LIB_FRAGMENT),
    runtimeModuleRepository = payload(PLATFORM_RUNTIME_MODULE_REPOSITORY_FRAGMENT),
  )
}

/**
 * The targets JSON and the JPS model as the reference inputs read them. A module dependency outside the test scope is a
 * runtime classpath edge. A library whose parent is the project is a project library reference, whatever its scope.
 */
private fun referenceInputModel(targets: BazelTargetsInfo.TargetsFile, outputProvider: ModuleOutputProvider): DevDistReferenceInputModel {
  val javaExtension = JpsJavaExtensionService.getInstance()
  return DevDistReferenceInputModel(
    targets = targets,
    moduleDependencies = { name ->
      outputProvider.findModule(name)?.dependenciesList?.dependencies.orEmpty()
        .filterIsInstance<JpsModuleDependency>()
        .filter { javaExtension.getDependencyExtension(it)?.scope != JpsJavaDependencyScope.TEST }
        .map { it.moduleReference.moduleName }
    },
    projectLibraryReferences = { name ->
      outputProvider.findModule(name)?.dependenciesList?.dependencies.orEmpty()
        .filterIsInstance<JpsLibraryDependency>()
        .filter { it.libraryReference.parentReference.resolve() is JpsProject }
        .map { it.libraryReference.libraryName }
    },
  )
}


/** One field value of a product plan as [planFieldBodies] renders it at the top level, keyed with its field. */
private data class PlanFieldBody(@JvmField val field: String, @JvmField val body: String)

/**
 * The field values of one product plan that products can share, as top-level bodies with no trailing newline.
 *
 * An empty collection is left out: it states no fact worth a name.
 */
private fun planFieldBodies(product: ProductFragmentPlan): List<PlanFieldBody> {
  val result = ArrayList<PlanFieldBody>()
  if (product.platformAssets.files.isNotEmpty()) {
    result.add(PlanFieldBody("platform_assets", buildString {
      append("{\n")
      for ((platform, files) in product.platformAssets.files) {
        append("    \"").append(platform).append("\": [\n")
        for (file in files) {
          append("        struct(source = \"").append(file.source).append("\", path = \"").append(file.path)
            .append("\", executable = ").append(if (file.executable) "True" else "False").append("),\n")
        }
        append("    ],\n")
      }
      append("}")
    }))
  }
  // The facts of the launch model that Bazel reads at analysis time. `DEV_DIST_LAUNCH_MODELS` names the model itself.
  val model = product.launchModel
  result.add(PlanFieldBody("launch", buildString {
    append("struct(\n")
    append("    idea_properties = \"").append(product.ideaProperties).append("\",\n")
    append("    vmoptions = {\n")
    for ((token, os) in listOf("darwin" to OsFamily.MACOS, "linux" to OsFamily.LINUX, "windows" to OsFamily.WINDOWS)) {
      append("        \"").append(token).append("\": \"").append(model.vmOptionsFileName(os)).append("\",\n")
    }
    append("    },\n")
    append("    main_class = \"").append(model.launch.mainClass).append("\",\n")
    append(")")
  }))
  return result
}

/**
 * The field values two or more products state alike, each with the private name the file defines it under.
 *
 * A value that every product with the field states is `_<FIELD>`. Any other shared value is `_<FIELD>_<product>`, after
 * the first product that states it in file order. The order is the field order, then the order of first use.
 */
private fun sharePlanFieldBodies(products: List<ProductFragmentPlan>): Map<PlanFieldBody, String> {
  val users = LinkedHashMap<PlanFieldBody, MutableList<String>>()
  for (product in products) {
    for (body in planFieldBodies(product)) {
      users.computeIfAbsent(body) { ArrayList() }.add(product.platformPrefix)
    }
  }
  val bodiesByField = users.keys.groupBy { it.field }
  val shared = LinkedHashMap<PlanFieldBody, String>()
  for (field in listOf("platform_assets", "launch")) {
    val bodies = bodiesByField.get(field) ?: continue
    for (body in bodies) {
      val owners = users.getValue(body)
      if (owners.size < 2) continue
      val prefix = "_" + field.uppercase()
      shared.put(body, if (bodies.size == 1) prefix else prefix + "_" + owners.first())
    }
  }
  return shared
}

/** The fields of one product plan with [indent] before every field, a shared value written as its name. */
private fun renderPlanFields(product: ProductFragmentPlan, indent: String, sharedFields: Map<PlanFieldBody, String>): String = buildString {
  val bodies = planFieldBodies(product).associateBy { it.field }
  fun appendField(field: String) {
    val body = bodies.get(field) ?: return
    append(indent).append(field).append(" = ").append(sharedFields.get(body) ?: body.body.replace("\n", "\n$indent")).append(",\n")
  }
  if (product.runtimeModuleRepository) {
    // Only a row that asks for the runtime module repository composes the component. A product without one has no field.
    append(indent).append("runtime_module_repository = True,\n")
    // The split product whose platform the component lays out beside the product's own. The dist macro takes its
    // platform payload and its frontend-only plugin components from here. Only the reference macro reads its
    // `platform_lib` declaration.
    product.embeddedFrontend?.let { append(indent).append("embedded_frontend = \"").append(it).append("\",\n") }
    // A modular-loader product reads the repository at every start, so every row of it composes the component.
    if (product.modularLoader) {
      append(indent).append("modular_loader = True,\n")
    }
    // The module whose descriptor is the core plugin of the repository.
    append(indent).append("application_info_module = \"").append(product.applicationInfoModule).append("\",\n")
  }
  if (bodies.containsKey("platform_assets")) appendField("platform_assets") else append(indent).append("platform_assets = {},\n")
  appendField("launch")
}

private fun renderFragmentInputs(products: List<ProductFragmentPlan>, half: DevDistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# The exact module and library names each fragment declares as its Bazel inputs, so a fragment reads the\n")
  append("# jars its slice of the layout needs instead of the whole production target set.\n")
  append("#\n")
  append("# `modules`, `project_libraries`, `module_sets` and `runtime_classpath_modules` are names. The binder reads this\n")
  append("# file at load time, and no module extension loads it.")
  if (DevDistCapability.REFERENCE_PLAN in half.capabilities) {
    append(" The generator resolves the names to the labels of\n")
    append("# `dev_dist_reference_inputs.bzl`, which only the reference fragments read.")
  }
  append("\n")
  append("# `packed_content_module_jars` is labels: whether a module packs a `lib/` jar is a target of its own, and only the\n")
  append("# generator knows which modules have one.\n")
  append("#\n")
  append("# `module_sets` is a reference, not a name list: the modules a set contains live in `dev_dist_module_sets.bzl`\n")
  append("# and are shared by every product referencing that set, so this file carries only what no set covers.\n")
  append("# The same holds for the packing labels: the label of a set member lives in the `packed` dict of its set, and\n")
  append("# `packed_content_module_jars` names only the labels of the modules no set covers. The binder unions the set\n")
  append("# labels into the payload at load time with `dev_dist_packed_labels`.\n")
  append("#\n")
  append("# `module_system_loaded` names the packed jars that the module system loads, as labels, and only the ones no\n")
  append("# set carries in its own `module_system_loaded`. The payload puts every other packed direct child of `lib/` on\n")
  append("# the core classpath.\n")
  append("#\n")
  append("# The plugins are absent. A plugin's own `dev_plugin` target and the `content_module_jar` targets of its\n")
  append("# members pack its jars and state their inputs as labels. What is left is the platform, whose flat core\n")
  append("# answers to no plugin target.\n")
  append("#\n")
  append("# Products with equal payloads share one private body, named after the first of them in this file. The\n")
  append("# readers take a product's dict by its key, so a shared body reads like an inline one. A residual jar struct\n")
  append("# that two or more products write alike is one private `_RESIDUAL_JAR_<destination>` struct for the same\n")
  append("# reason, and a product names it where it would state the struct.\n")
  val sharedResidualJars = shareResidualJarBodies(products)
  for ((body, name) in sharedResidualJars) {
    append(name).append(" = ").append(body.body).append("\n\n")
  }
  // The body of every product at the top-level indent, and the products that render each body.
  val bodyOwners = LinkedHashMap<String, MutableList<ProductFragmentPlan>>()
  for (product in products) {
    bodyOwners.computeIfAbsent(renderFragmentPayloads(product, indent = "    ", sharedResidualJars)) { ArrayList() }.add(product)
  }
  val sharedBodyNames = HashMap<String, String>()
  for ((body, owners) in bodyOwners) {
    if (owners.size < 2) continue
    val name = "_FRAGMENT_INPUTS_" + owners.first().platformPrefix
    for (owner in owners) {
      sharedBodyNames.put(owner.platformPrefix, name)
    }
    append(name).append(" = {\n").append(body).append("}\n\n")
  }
  append("DEV_DIST_FRAGMENT_INPUTS = {\n")
  for (product in products) {
    append("    \"").append(product.platformPrefix).append("\": ")
    val sharedName = sharedBodyNames.get(product.platformPrefix)
    if (sharedName == null) {
      append("{\n").append(renderFragmentPayloads(product, indent = "        ", sharedResidualJars)).append("    },\n")
    }
    else {
      append(sharedName).append(",\n")
    }
  }
  append("}\n")
}

/** One residual jar struct as [residualJarBody] renders it at the top level, keyed with its destination. */
@ApiStatus.Internal
data class ResidualJarBody(@JvmField val destination: String, @JvmField val body: String)

/**
 * The residual jar structs two or more products write alike, each with the private name the file defines it under.
 *
 * The name is `_RESIDUAL_JAR_` and the destination without its `.jar` suffix, with every character that is not a
 * letter or a digit as `_`. Two different bodies of one destination take a counter from the second on. The order is
 * the order of first use: the products by key, and the destinations of a payload sorted.
 */
private fun shareResidualJarBodies(products: List<ProductFragmentPlan>): Map<ResidualJarBody, String> {
  val uses = LinkedHashMap<ResidualJarBody, Int>()
  for (product in products) {
    for (payload in product.payloads) {
      for ((destination, jar) in payload.residualJars.toSortedMap()) {
        uses.merge(ResidualJarBody(destination = destination, body = residualJarBody(jar)), 1, Int::plus)
      }
    }
  }
  val taken = HashMap<String, Int>()
  val shared = LinkedHashMap<ResidualJarBody, String>()
  for ((key, count) in uses) {
    if (count < 2) {
      continue
    }
    val base = "_RESIDUAL_JAR_" + key.destination.removeSuffix(".jar").replace(NOT_A_STARLARK_NAME_CHARACTER, "_")
    val ordinal = taken.merge(base, 1, Int::plus)!!
    shared.put(key, if (ordinal == 1) base else "${base}_$ordinal")
  }
  return shared
}

private val NOT_A_STARLARK_NAME_CHARACTER = Regex("[^A-Za-z0-9]")

/** The payload entries of one product, each keyed by the fragment name, with [indent] before every key. */
private fun renderFragmentPayloads(
  product: ProductFragmentPlan,
  indent: String,
  sharedResidualJars: Map<ResidualJarBody, String> = emptyMap(),
): String = buildString {
  for (payload in product.payloads.sortedBy(FragmentPayload::name)) {
    appendPayload(key = payload.name, payload = payload, indent = indent, sharedResidualJars = sharedResidualJars)
  }
}

private fun StringBuilder.appendPayload(key: String, payload: FragmentPayload, indent: String, sharedResidualJars: Map<ResidualJarBody, String>) {
  val fieldIndent = "$indent    "
  val fields = buildString {
    appendNonEmptyNameList("modules", payload.modules, indent = fieldIndent)
    appendNonEmptyNameList("project_libraries", payload.projectLibraries, indent = fieldIndent)
    appendNonEmptyNameList("module_sets", payload.moduleSets, indent = fieldIndent)
    appendNonEmptyNameList("runtime_classpath_modules", payload.runtimeClasspathModules, indent = fieldIndent)
    appendNonEmptyNameList("packed_content_module_jars", payload.packedContentModuleJars, indent = fieldIndent)
    appendNonEmptyNameList("module_system_loaded", payload.moduleSystemLoaded, indent = fieldIndent)
    appendResidualJars(payload.residualJars, indent = fieldIndent, shared = sharedResidualJars)
    payload.pluginClasspathPrefix?.let { label ->
      append(fieldIndent).append("plugin_classpath_prefix = \"").append(label).append("\",\n")
    }
  }
  append(indent).append("\"").append(key).append("\": struct(")
  if (fields.isNotEmpty()) {
    append('\n').append(fields).append(indent)
  }
  append("),\n")
}

private fun renderModuleSets(moduleSets: List<ModuleSetData>, half: DevDistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# What each module set a split product references contains: the modules it declares itself, and the sets it\n")
  append("# nests. `dev_dist_fragment_inputs.bzl` names the sets a product's platform payload references and the binder\n")
  append("# walks them from here, so a set two products share is written once instead of flattened into both payloads -\n")
  append("# which is what made that file grow by ~450 names per split product.\n")
  append("#\n")
  append("# The same `moduleSet { }` declarations the generated module-set descriptors come from, so this stays in step\n")
  append("# with what the layout reads at runtime: both are written by this one run and diffed by the same\n")
  append("# model-generation validation. `modules` and `nested` keep the DSL order.\n")
  append("#\n")
  append("# `loading` maps each member with a non-default loading rule to that rule, `required_if_available` maps a member to\n")
  append("# the module it states, and `alias` is the plugin alias of the set. `dev_dist_product_descriptor` and\n")
  append("# `dev_dist_embedded_product_descriptor` walk the sets in DSL order with these facts and compose the product content.\n")
  append("#\n")
  append("# `packed` maps each member that owns a `content_module_jar` target to that label, written once per set. A\n")
  append("# product's `platform_lib` payload in `dev_dist_fragment_inputs.bzl` names only the labels no set carries.\n")
  append("# Every product that references a set hands over the labels of its members, less the ones its mode refuses.\n")
  append("#\n")
  append("# `module_system_loaded` names the `packed` members whose jar the module system loads in every product that\n")
  append("# references the set. The payload puts every other packed direct child of `lib/` on the core classpath.\n")
  append("#\n")
  append("# `mode_refused` names, per product mode, the `packed` members that the mode refuses. The layout of a product of\n")
  append("# that mode places no jar of them. Such a product hands over no such label, and the binder skips it.\n")
  append("#\n")
  append("# The binder fails on a set name that a payload references and this table does not have. One generator run writes\n")
  append("# both files, and no module extension loads this one.\n")
  append("DEV_DIST_MODULE_SETS = {\n")
  for (moduleSet in moduleSets) {
    append("    \"").append(moduleSet.name).append("\": struct(\n")
    appendNameList("modules", moduleSet.modules, indent = "        ")
    appendNameList("nested", moduleSet.nested, indent = "        ")
    appendStringDict("loading", moduleSet.loading)
    appendStringDict("required_if_available", moduleSet.requiredIfAvailable)
    moduleSet.alias?.let { alias ->
      append("        alias = \"").append(alias).append("\",\n")
    }
    if (moduleSet.packed.isNotEmpty()) {
      append("        packed = {\n")
      for ((module, label) in moduleSet.packed) {
        append("            \"").append(module).append("\": \"").append(label).append("\",\n")
      }
      append("        },\n")
    }
    appendNonEmptyNameList("module_system_loaded", moduleSet.moduleSystemLoaded)
    if (moduleSet.modeRefused.isNotEmpty()) {
      append("        mode_refused = {\n")
      for ((mode, modules) in moduleSet.modeRefused) {
        append("            \"").append(mode).append("\": [\n")
        for (module in modules) {
          append("                \"").append(module).append("\",\n")
        }
        append("            ],\n")
      }
      append("        },\n")
    }
    append("    ),\n")
  }
  append("}\n")
}

/** A `name = {...}` field of a set struct, one entry per line and sorted by key, as buildifier keeps a dict. An empty dict is left out. */
private fun StringBuilder.appendStringDict(name: String, values: Map<String, String>) {
  if (values.isEmpty()) {
    return
  }
  append("        ").append(name).append(" = {\n")
  for ((key, value) in values.toSortedMap()) {
    append("            \"").append(key).append("\": \"").append(value).append("\",\n")
  }
  append("        },\n")
}

private fun renderContentSets(pluginExecutions: DevDistPluginExecutionRendering, half: DevDistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# `DEV_DIST_PLUGIN_COMPONENTS`, derived from the Product DSL, the layouts and the plugin descriptors: the\n")
  append("# component of every plugin by product key, tier and main module. The tier is `bundled` or `additional`. The\n")
  append("# value is one label, or a dict from host platform to label for a platform-specific plugin. The bundled tier is\n")
  append("# in composition order: the generator's frozen list first. A simple plugin's component is its `dev_plugin`\n")
  if (half.writesCommunityPackages) {
    append("# target. A complex plugin's component is the component of its `dev_dist_complex_plugin` call. The call sits in\n")
    append("# the `dev` section of the plugin when its own package holds the same plan files and calls. Otherwise it sits in\n")
    append("# `build/dev-dist-descriptors/<main module>/BUILD.bazel`.\n")
  }
  else {
    append("# target. A complex plugin's component is the component of its `dev_dist_complex_plugin` call, which sits beside\n")
    append("# the plugin: in the `dev` section of an ultimate plugin, or in `build/dev-dist-descriptors/<main module>/BUILD.bazel`\n")
    append("# for a community plugin.\n")
  }
  append("#\n")
  append("# Labels, not names: a target Bazel cannot resolve is an analysis error, not a fragment that quietly packs\n")
  val macrosBzlPath = half.macrosBzl.removePrefix("//").replace(':', '/')
  append("# less. `").append(macrosBzlPath).append("` loads this file, not a module extension, so nothing here is\n")
  append("# fail-open and the file does not have to keep its own generator buildable.\n")
  append("\n")
  append(pluginExecutions.components)
}

/**
 * The `residual_jars` field of one payload, sorted by destination, or nothing for a payload without one.
 *
 * A jar whose struct [shared] names is written as that name. Any other jar is written as its struct, see
 * [residualJarBody], at the field's indent.
 */
@ApiStatus.Internal
fun StringBuilder.appendResidualJars(
  residualJars: Map<String, ResidualPlatformJar>,
  indent: String,
  shared: Map<ResidualJarBody, String> = emptyMap(),
) {
  if (residualJars.isEmpty()) {
    return
  }
  append(indent).append("residual_jars = {\n")
  for ((destination, jar) in residualJars.toSortedMap()) {
    val body = residualJarBody(jar)
    append(indent).append("    \"").append(destination).append("\": ")
    val sharedName = shared.get(ResidualJarBody(destination = destination, body = body))
    if (sharedName != null) {
      append(sharedName)
    }
    else {
      append(body.lines().joinToString(separator = "\n$indent    "))
    }
    append(",\n")
  }
  append(indent).append("},\n")
}

/** One residual jar as a `struct(...)` at the top level, with no trailing newline. */
@ApiStatus.Internal
fun residualJarBody(jar: ResidualPlatformJar): String = buildString {
  append("struct(\n")
  appendNonEmptyNameList("modules", jar.modules, indent = "    ")
  appendNonEmptyNameList("libraries", jar.libraries, indent = "    ")
  if (jar.patches.isNotEmpty()) {
    append("    patches = {\n")
    for ((label, path) in jar.patches) {
      append("        \"").append(label).append("\": \"").append(path).append("\",\n")
    }
    append("    },\n")
    append("    patched_module = \"").append(requireNotNull(jar.patchedModule)).append("\",\n")
  }
  append(")")
}

/** Omits an empty list when the reader already supplies that default. */
private fun StringBuilder.appendNonEmptyNameList(field: String, names: List<String>, indent: String = "        ") {
  if (names.isNotEmpty()) {
    appendNameList(field, names, indent = indent)
  }
}

internal fun StringBuilder.appendNameList(field: String, names: List<String>, indent: String = "        ") {
  append(indent).append(field).append(" = [\n")
  for (name in names) {
    append(indent).append("    \"").append(name).append("\",\n")
  }
  append(indent).append("],\n")
}

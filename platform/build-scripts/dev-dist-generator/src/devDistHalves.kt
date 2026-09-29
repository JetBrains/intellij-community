// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.platform.buildScripts.concurrency.Subtask
import com.intellij.platform.buildScripts.concurrency.TaskScope
import com.intellij.platform.buildScripts.pluginModelTool.ProductDerivation
import com.intellij.platform.buildScripts.pluginModelTool.loadGeneratorJpsProject
import com.intellij.platform.buildScripts.pluginModelTool.productDerivation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.buildSpan
import org.jetbrains.intellij.build.impl.BazelModuleOutputProvider
import org.jetbrains.intellij.build.impl.BazelModuleOutputProviderState
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.stats.DevDistPlanFileResult
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Starts the dev-distribution renders of one generator run beside the product-model pipeline: the plan of the
 * community half, and then the plugin packings, the `dev` sections and the plan of [half].
 *
 * Each half writes the generated files of its own packages, see [DevDistHalf.ownsPackage]. The community half renders
 * first from the community model. [half] renders second, and it reads the community result as the upstream result:
 * it reuses a community target when its own text is equal, and else it writes a product package outside `community/`.
 *
 * The renders read the product model of [productDerivation] and no pipeline output. Only the write decision reads the
 * errors of the pipeline, so [DevDistHalvesRun.finish] takes it afterwards. A run without `bazel-targets.json` or
 * without the community targets JSON forks no section and no plan render. A fork carries the current context, so a
 * span that a render starts nests under the span of the run.
 *
 * [projectRoot] is the monorepo root, and [half] has it as its root. The community half renders against its own root,
 * see [DevDistHalf.root]. [testPlugins] are the Product DSL test plugins that a run-configuration module can be. Both
 * halves read the same specifications, whose paths are relative to the monorepo root.
 */
@ApiStatus.Internal
fun TaskScope.forkDevDistHalves(
  half: DevDistHalf,
  projectRoot: Path,
  outputProvider: ModuleOutputProvider,
  productDerivation: ProductDerivation,
  testPlugins: List<TestPluginSpec>,
  verifyPlanUnits: Boolean,
): DevDistHalvesRun {
  val root = half.root(projectRoot)
  val products = productDerivation.products
  // The derivation carries no Bazel label, so it runs on every checkout. The dev sections and the plan need
  // `bazel-targets.json`. Both read one plugin packing derivation, so they state one packing per plugin. The plan
  // reads the labels the dev sections decide, so it renders after them.
  val derivationTask = fork("derive plugin packings") {
    buildSpan("derive plugin packings") {
      derivePluginPackings(half = half, root = root, outputProvider = outputProvider, derivation = productDerivation)
    }
  }
  var bazelTask: Subtask<DevDistBazelComputes>? = null
  var communityTask: Subtask<DevDistBazelComputes?>? = null
  // The community half renders first, and the half of this run reads its result as the upstream result. A run without
  // the community JSON renders neither half. A committing run then fails in `finish`.
  if (devDistPlanInputExists(projectRoot) && Files.exists(communityTargetsJson(projectRoot))) {
    val upstreamTask = fork("generate community dev-distribution plan") {
      computeCommunityDevDistFiles(projectRoot = projectRoot, verifyPlanUnits = verifyPlanUnits, testPlugins = testPlugins)
    }
    communityTask = upstreamTask
    bazelTask = fork("generate dev-distribution build sections and plan") {
      computeDevDistBazelFiles(
        half = half,
        root = root,
        outputProvider = outputProvider,
        products = products,
        derivation = derivationTask.await(),
        verifyPlanUnits = verifyPlanUnits,
        targets = BazelTargetsInfo.loadBazelTargetsJson(projectRoot),
        testPlugins = testPlugins,
        upstream = requireUpstream(upstreamTask.await(), projectRoot),
      )
    }
  }
  return DevDistHalvesRun(
    half = half,
    projectRoot = projectRoot,
    outputProvider = outputProvider,
    products = products,
    testPlugins = testPlugins,
    verifyPlanUnits = verifyPlanUnits,
    derivationTask = derivationTask,
    bazelTask = bazelTask,
    communityTask = communityTask,
  )
}

/** The message of a run whose community half is missing. */
private fun communityHalfMissing(projectRoot: Path): String {
  return "The community half needs ${projectRoot.relativize(communityTargetsJson(projectRoot))}." +
         " Run ./community/build/jpsModelToBazelCommunityOnly.cmd first."
}

/** [upstream], the result of the community half. The half that reads it fails when it is missing. */
private fun requireUpstream(upstream: DevDistBazelComputes?, projectRoot: Path): DevDistBazelComputes {
  return checkNotNull(upstream) { communityHalfMissing(projectRoot) }
}

/** The dev-distribution renders that [forkDevDistHalves] started. */
@ApiStatus.Internal
class DevDistHalvesRun internal constructor(
  private val half: DevDistHalf,
  /** The monorepo root. */
  private val projectRoot: Path,
  private val outputProvider: ModuleOutputProvider,
  private val products: List<DiscoveredProduct>,
  private val testPlugins: List<TestPluginSpec>,
  private val verifyPlanUnits: Boolean,
  private val derivationTask: Subtask<PluginPackingDerivation>,
  private val bazelTask: Subtask<DevDistBazelComputes>?,
  private val communityTask: Subtask<DevDistBazelComputes?>?,
) {
  /**
   * Writes the dev sections and the plans of both halves when [commitPlan], and otherwise reports them as diffs.
   *
   * The caller sets [commitPlan] only when the run commits and the whole product model is clean. The files reach the
   * result even when they changed nothing, so the `All files unchanged` line counts them too.
   */
  fun finish(commitPlan: Boolean): DevDistHalvesFiles {
    val derivation = derivationTask.await()
    // This run has no `bazel-targets.json` when no task was forked. A validating run reports nothing rather than failing
    // on the missing file: the same validation also runs under Bazel, where the file is a declared input, and that run is
    // what catches a stale plan. A committing run is the converter's own, so there a missing file is a setup error, and
    // the render below fails and says so. That render is sequential, and it costs nothing: it is the run that fails.
    val communityFiles = communityTask?.await()
                         ?: if (commitPlan) computeCommunityDevDistFiles(projectRoot, verifyPlanUnits, testPlugins) else null
    // A committing run needs the community targets JSON, as it needs the monorepo one. Under Bazel the property names it,
    // and a missing file already stopped the run. An IDE run without the workspace file validates no community half.
    check(!commitPlan || communityFiles != null) { communityHalfMissing(projectRoot) }
    val bazelFiles = bazelTask?.await() ?: communityFiles?.takeIf { commitPlan }?.let { upstream ->
      computeDevDistBazelFiles(
        half = half,
        root = half.root(projectRoot),
        outputProvider = outputProvider,
        products = products,
        derivation = derivation,
        verifyPlanUnits = verifyPlanUnits,
        targets = BazelTargetsInfo.loadBazelTargetsJson(projectRoot),
        testPlugins = testPlugins,
        upstream = upstream,
      )
    }
    val results = listOfNotNull(communityFiles, bazelFiles).flatMap { listOf(it.sections.finish(commitChanges = commitPlan), it.plan.finish(commitChanges = commitPlan)) }
    return DevDistHalvesFiles(files = results.flatMap { it.files }, diffs = results.flatMap { it.diffs })
  }
}

/** The dev-distribution files of one run and, for a run that does not write them, their diffs. */
@ApiStatus.Internal
class DevDistHalvesFiles(
  @JvmField val files: List<DevDistPlanFileResult>,
  @JvmField val diffs: List<FileDiff>,
)

/**
 * The two dev-distribution outputs that need `bazel-targets.json`: the `BUILD.bazel` dev sections and the plan.
 *
 * [buildSections] is the computation both outputs read. The half that renders second compares its own sections with the
 * ones of the upstream result. [ownPackagePlans] are the plan files and the calls that the community half writes into
 * the own package of a community plugin, and the half that renders second reuses them.
 */
internal class DevDistBazelComputes(
  @JvmField val sections: DevDistPlanCompute,
  @JvmField val plan: DevDistPlanCompute,
  @JvmField val buildSections: DevDistBuildSections,
  @JvmField val ownPackagePlans: DevDistOwnPackagePlans,
)

/**
 * Walks the descriptors once, renders the dev sections, the plugin executions, and then the plan over the labels the
 * sections decide.
 *
 * The plan reads [DevDistBuildSections.verdicts], so it renders after the sections. The plugin executions render
 * between the sections and their write, because the `dev` section of a complex plugin holds its
 * `dev_dist_complex_plugin` calls or exports its plan files, see [computeDevDistPluginExecutions]. The walk and each
 * render get a span of their own. The JSON is read once, and the index gives it to every render. The descriptor walk
 * runs once, and every render reads it.
 *
 * [root] is the root of [half], and every path of the run is relative to it, see [DevDistHalf.root]. [upstream] is the
 * result of the community half, which the ultimate half reads. It is `null` for the community half, which renders first.
 * [testPlugins] are the Product DSL test plugins that a run-configuration module can be.
 */
internal fun computeDevDistBazelFiles(
  half: DevDistHalf,
  root: Path,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  derivation: PluginPackingDerivation,
  verifyPlanUnits: Boolean,
  targets: BazelTargetsInfo.TargetsFile,
  testPlugins: List<TestPluginSpec>,
  upstream: DevDistBazelComputes? = null,
): DevDistBazelComputes {
  check(upstream == null || !half.writesCommunityPackages) { "The ${half.name} half renders first, so it reads no upstream result" }
  val upstreamSections = upstream?.buildSections
  val index = DevDistBazelIndex(
    targets = targets,
    projectRoot = root,
    communityRoot = half.communityRoot(root),
    planPackageIsCommunity = half.writesCommunityPackages,
  )
  val files = DevDistBuildFiles(index)
  val walk = buildSpan("walk dev-distribution descriptors") {
    walkDescriptors(
      projectRoot = root,
      outputProvider = outputProvider,
      products = products,
      generatedModuleSetDescriptors = half.generatedModuleSetDescriptors,
    )
  }
  fun computeSections(foreignSections: Set<String>): DevDistBuildSections {
    return buildSpan("generate dev-distribution build sections") {
      computeDevDistBuildSections(
        outputProvider = outputProvider,
        products = products,
        walk = walk,
        derivation = derivation,
        index = index,
        files = files,
        half = half,
        testPlugins = testPlugins,
        verifyPlanUnits = verifyPlanUnits,
        foreignSections = foreignSections,
        upstream = upstreamSections,
      )
    }
  }
  val sections = if (upstreamSections == null) computeSections(emptySet()) else computeUpstreamAwareSections(upstreamSections, ::computeSections)
  upstreamSections?.let(sections::requireResourcesDeclaredBy)
  val executions = buildSpan("generate dev-distribution plugin executions") {
    computeDevDistPluginExecutions(
      sections = sections,
      upstreamPackagePlans = upstream?.ownPackagePlans,
    )
  }
  val sectionFiles = buildSpan("write dev-distribution build sections") {
    writeDevDistBuildSectionFiles(projectRoot = root, sections = sections, index = index, files = files, writesPackage = half::ownsPackage)
  }
  val plan = buildSpan("generate dev-distribution plan") {
    computeDevDistPlan(
      half = half,
      outputProvider = outputProvider,
      products = products,
      walk = walk,
      sections = sections,
      executions = executions,
      targets = index.targets,
      runConfigurationRows = derivation.runConfigurations.rows,
    )
  }
  // One key of both registries with one product class states one product, so the two halves render one launch model.
  upstream?.let { other -> checkSharedLaunchModels(half = half, launchModels = plan.launchModels, otherLaunchModels = other.plan.launchModels) }
  return DevDistBazelComputes(
    sections = sectionFiles,
    plan = plan,
    buildSections = sections,
    ownPackagePlans = DevDistOwnPackagePlans.of(executions.files, executions.rendering, half),
  )
}

/**
 * The sections of the ultimate half. The first computation finds
 * the community plugins whose `dev` section the community half states with another text, see [foreignCommunitySections].
 * The second computation moves their leaf and their `dev_plugin` into the product package of the plugin under `build/`.
 * Every other community plugin reuses the targets of its community section. The census prints one line per plugin.
 */
private fun computeUpstreamAwareSections(
  upstreamSections: DevDistBuildSections,
  computeSections: (Set<String>) -> DevDistBuildSections,
): DevDistBuildSections {
  val first = computeSections(emptySet())
  val foreign = foreignCommunitySections(upstream = upstreamSections, ultimate = first)
  for (mainModule in foreign) {
    println("product package of $mainModule: the ultimate half states another leaf or packaging than its community section")
  }
  println("ultimate half: ${foreign.size} product packages of community plugins")
  return if (foreign.isEmpty()) first else computeSections(foreign)
}

/**
 * The launch model of one product of one half: the class of its product properties and the encoded text. Two halves
 * render one text for a key that both registries name with one class.
 */
internal data class DevDistLaunchModel(@JvmField val productClass: String, @JvmField val text: String)

/**
 * Fails when a key of both registries has one product class and two launch models. [launchModels] are the models of
 * [half], and [otherLaunchModels] are the models of the other half. Both are keyed by the `dev-build.json` key. A key
 * with two classes, such as `AndroidStudio`, states two products, so its models may differ. The message names the key
 * and the class.
 */
internal fun checkSharedLaunchModels(
  half: DevDistHalf,
  launchModels: Map<String, DevDistLaunchModel>,
  otherLaunchModels: Map<String, DevDistLaunchModel>,
) {
  for ((product, model) in launchModels) {
    val other = otherLaunchModels.get(product) ?: continue
    if (other.productClass != model.productClass) {
      continue
    }
    check(other.text == model.text) {
      "The two halves render two launch models for '$product' of ${model.productClass}. The ${half.name} half renders:\n" +
      model.text + "\nand the other half renders:\n" + other.text
    }
  }
}

/**
 * The JVM property that names the community targets JSON by its `rlocationpath`. The Bazel targets of the generator set
 * it to the output of `@community//build:community_bazel_targets_json`. An IDE run does not set it.
 */
private const val COMMUNITY_TARGETS_JSON_FILE_PROPERTY: String = "intellij.build.dev.dist.community.targets.json.file"

/**
 * The community targets JSON. When [COMMUNITY_TARGETS_JSON_FILE_PROPERTY] is set, the file is the Bazel output it names,
 * and a missing file stops the run. Otherwise, the file is the `bazel-targets.json` of the community converter under the
 * monorepo root [projectRoot]. That file is not checked in.
 */
@ApiStatus.Internal
fun communityTargetsJson(projectRoot: Path): Path {
  val configured = System.getProperty(COMMUNITY_TARGETS_JSON_FILE_PROPERTY)
    ?: return CommunityDevDistHalf.root(projectRoot).resolve("build/bazel-targets.json")
  val path = Path.of(configured)
  val file = if (path.isAbsolute) path else BazelRunfiles.resolveRunfilePath(configured)
  check(Files.exists(file)) {
    "The community targets JSON $file does not exist. The property $COMMUNITY_TARGETS_JSON_FILE_PROPERTY names '$configured'." +
    " Add @community//build:community_bazel_targets_json to the data of the target."
  }
  return file
}

/**
 * The community half: the `dev` sections and the plan of the community products over the community JPS model, written
 * under `community/`.
 *
 * The half renders against its own root: its JPS model, its registry, its run configurations and every path of its
 * outputs are relative to `community/` of the monorepo root [projectRoot], see [DevDistHalf.root].
 *
 * The half reads the community targets JSON, see [communityTargetsJson]. Both targets JSON files spell a community label
 * alike, so under Bazel a module jar resolves through the runfiles of the tool. Out of Bazel, a jar path of the community
 * targets JSON is below the community output directory of the monorepo, and the provider resolves it there. So the
 * provider keeps the monorepo root as its project home. `null` when the property is not set and the workspace file does
 * not exist. The caller decides what that means.
 */
internal fun computeCommunityDevDistFiles(
  projectRoot: Path,
  verifyPlanUnits: Boolean,
  testPlugins: List<TestPluginSpec>,
): DevDistBazelComputes? {
  val half = CommunityDevDistHalf
  val root = half.root(projectRoot)
  val targetsFile = communityTargetsJson(projectRoot)
  if (!Files.exists(targetsFile)) {
    return null
  }
  val targets = buildSpan("load community bazel-targets.json") { communityTargetsSeenFromMonorepo(readBazelTargetsJson(targetsFile)) }
  val project = loadGeneratorJpsProject(root)
  val outputProvider = BazelModuleOutputProvider(
    state = BazelModuleOutputProviderState(
      modules = project.modules,
      projectHome = projectRoot,
      bazelTargetsLoader = { targets },
    ),
    lifetime = null,
    useTestCompilationOutput = true,
  )
  val derivation = productDerivation(root, outputProvider)
  val packings = buildSpan("derive community plugin packings") {
    derivePluginPackings(half = half, root = root, outputProvider = outputProvider, derivation = derivation)
  }
  return computeDevDistBazelFiles(
    half = half,
    root = root,
    outputProvider = outputProvider,
    products = derivation.products,
    derivation = packings,
    verifyPlanUnits = verifyPlanUnits,
    targets = targets,
    testPlugins = testPlugins,
  )
}

private val BAZEL_TARGETS_JSON = Json { ignoreUnknownKeys = true }

private fun readBazelTargetsJson(file: Path): BazelTargetsInfo.TargetsFile {
  return BAZEL_TARGETS_JSON.decodeFromString<BazelTargetsInfo.TargetsFile>(Files.readString(file))
}

/** The output directory of the community converter, relative to the root that the jar paths of its JSON are relative to. */
private const val COMMUNITY_CONVERTER_OUTPUT_PREFIX: String = "out/bazel-out/jvm-fastbuild/bin/"

/**
 * [targets] of the community converter, with every jar path of a module and of a plugin distribution relative to the
 * monorepo root. The community converter writes such a path below the community output directory. In the monorepo that
 * directory is below the `external/community+` output of the main repository. A library jar path is relative to the
 * Bazel output root in both files, so it stays as it is.
 */
internal fun communityTargetsSeenFromMonorepo(targets: BazelTargetsInfo.TargetsFile): BazelTargetsInfo.TargetsFile {
  fun remap(path: String): String {
    if (!path.startsWith(COMMUNITY_CONVERTER_OUTPUT_PREFIX)) {
      return path
    }
    return COMMUNITY_CONVERTER_OUTPUT_PREFIX + "external/community+/" + path.removePrefix(COMMUNITY_CONVERTER_OUTPUT_PREFIX)
  }
  return targets.copy(
    modules = targets.modules.mapValues { (_, module) ->
      module.copy(productionJars = module.productionJars.map(::remap), testJars = module.testJars.map(::remap))
    },
    pluginDistributionTargets = targets.pluginDistributionTargets.mapValues { (_, target) ->
      target.copy(distributionDirectory = remap(target.distributionDirectory))
    },
  )
}

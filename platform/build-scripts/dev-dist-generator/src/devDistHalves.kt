// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.openapi.application.ArchivedCompilationContextUtil
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
import org.jetbrains.jps.model.JpsProject
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
 * errors of the pipeline, so [DevDistHalvesRun.finish] takes it afterwards. A run without `bazel-targets.json` forks no
 * section and no plan render. Both halves read that file, see [computeCommunityDevDistFiles]. A fork carries the current
 * context, so a span that a render starts nests under the span of the run.
 *
 * [projectRoot] is the monorepo root, and [half] has it as its root. The community half renders against its own root,
 * see [DevDistHalf.root]. [testPlugins] are the Product DSL test plugins that a run-configuration module of [half] can
 * be. The community half reads none, see [computeCommunityHalf].
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
  var communityTask: Subtask<DevDistBazelComputes>? = null
  // The community half renders first, and the half of this run reads its result as the upstream result.
  if (devDistPlanInputExists(projectRoot)) {
    val upstreamTask = fork("generate community dev-distribution plan") {
      computeCommunityDevDistFiles(projectRoot = projectRoot, verifyPlanUnits = verifyPlanUnits)
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
        upstream = upstreamTask.await(),
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
  private val communityTask: Subtask<DevDistBazelComputes>?,
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
    // the community render below fails and says so. That render is sequential, and it costs nothing: it is the run that fails.
    val communityFiles = communityTask?.await() ?: if (commitPlan) computeCommunityDevDistFiles(projectRoot, verifyPlanUnits) else null
    val bazelFiles = bazelTask?.await() ?: communityFiles?.let { upstream ->
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
      upstreamLaunchModels = upstream?.plan?.launchModels,
    )
  }
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
 * The keys of both registries with one product class and one launch model, so [half] reuses the model of the other
 * half. [launchModels] are the models of [half], and [otherLaunchModels] are the models of the other half. Both are
 * keyed by the `dev-build.json` key. A key with two classes, such as `AndroidStudio`, states two products, so its models
 * may differ, and neither half reuses. Fails for a key with one class and two texts. The message names the key and the
 * class.
 */
internal fun sharedLaunchModels(
  half: DevDistHalf,
  launchModels: Map<String, DevDistLaunchModel>,
  otherLaunchModels: Map<String, DevDistLaunchModel>,
): Set<String> {
  val shared = LinkedHashSet<String>()
  for ((product, model) in launchModels) {
    val other = otherLaunchModels.get(product) ?: continue
    if (other.productClass != model.productClass) {
      continue
    }
    check(other.text == model.text) {
      "The two halves render two launch models for '$product' of ${model.productClass}. The ${half.name} half renders:\n" +
      model.text + "\nand the other half renders:\n" + other.text
    }
    shared.add(product)
  }
  return shared
}

/**
 * The community half of a monorepo run: the `dev` sections and the plan of the community products over the community
 * JPS model, written under `community/` of the monorepo root [projectRoot].
 *
 * The half reads the community rows of the monorepo `bazel-targets.json`, see [communityTargetsOf]. The jar paths of
 * that file are relative to the monorepo root, so the provider keeps [projectRoot] as its project home. The run fails
 * when the file does not exist, and the message names the converter that writes it.
 */
internal fun computeCommunityDevDistFiles(projectRoot: Path, verifyPlanUnits: Boolean): DevDistBazelComputes {
  check(devDistPlanInputExists(projectRoot)) {
    "The dev-distribution plan needs ${ArchivedCompilationContextUtil.getBazelTargetsJsonPath(projectRoot)}. Run ./build/jpsModelToBazel.cmd first."
  }
  val communityRoot = CommunityDevDistHalf.root(projectRoot)
  val project = loadGeneratorJpsProject(communityRoot)
  val targets = communityTargetsOf(targets = BazelTargetsInfo.loadBazelTargetsJson(projectRoot), project = project)
  return computeCommunityHalf(communityRoot = communityRoot, projectHome = projectRoot, project = project, targets = targets, verifyPlanUnits = verifyPlanUnits)
}

/**
 * The rows of [targets] that the community JPS model [project] names: the module rows and the `imlTargets` of its
 * modules, the plugin distributions of its main modules, and the rows of its project libraries.
 *
 * The monorepo converter writes a row of a community module as the community converter does, with two differences.
 * A jar path is relative to the monorepo, and a generated file holds no jar path. A module library label starts with
 * `@community//`, and the community converter writes `//`. A plan names that label as it is, so the rows get the `//`
 * form, see [communityModuleLibrary]. So the rows render the community half that the community targets JSON renders.
 * The rows keep the order of [targets].
 */
@ApiStatus.Internal
fun communityTargetsOf(targets: BazelTargetsInfo.TargetsFile, project: JpsProject): BazelTargetsInfo.TargetsFile {
  val moduleNames = project.modules.mapTo(HashSet()) { it.name }
  val libraryNames = project.libraryCollection.libraries.mapTo(HashSet()) { it.name }
  return BazelTargetsInfo.TargetsFile(
    modules = targets.modules.filterKeys { it in moduleNames }.mapValues { (_, module) ->
      if (module.moduleLibraries.isEmpty()) module else module.copy(moduleLibraries = module.moduleLibraries.mapValues { communityModuleLibrary(it.value) })
    },
    // The index keys an `imlTargets` entry by the file name of its iml, see `DevDistBazelIndex.bazelPackagePrefix`.
    imlTargets = targets.imlTargets.filter { it.substringAfterLast(':').substringAfterLast('/').removeSuffix(".iml") in moduleNames },
    projectLibraries = targets.projectLibraries.filterKeys { it in libraryNames },
    pluginDistributionTargets = targets.pluginDistributionTargets.filterKeys { it in moduleNames },
  )
}

/**
 * [library] with its target and its jar targets in the form of the community converter: `//pkg` for `@community//pkg`.
 * Its jar paths stay relative to the monorepo.
 */
private fun communityModuleLibrary(library: BazelTargetsInfo.LibraryDescription): BazelTargetsInfo.LibraryDescription {
  fun respell(label: String): String = if (label.startsWith(COMMUNITY_REPOSITORY_PREFIX)) "//" + label.removePrefix(COMMUNITY_REPOSITORY_PREFIX) else label
  return library.copy(target = respell(library.target), jarTargets = library.jarTargets.map(::respell))
}

/**
 * The community half over the community checkout [communityRoot]. The monorepo run and the community binary both call
 * it, so both write the same bytes.
 *
 * The half renders against [communityRoot]: its JPS model, its registry, its run configurations and every path of its
 * outputs are relative to it, see [DevDistHalf.root]. [project] is the JPS model of [communityRoot]. [targets] holds the
 * rows of the community modules, and its jar paths are relative to [projectHome]. A generated file holds no jar path, so
 * [projectHome] changes no byte of the output.
 *
 * The community model states no Product DSL test product, so the half reads no test plugin.
 */
internal fun computeCommunityHalf(
  communityRoot: Path,
  projectHome: Path,
  project: JpsProject,
  targets: BazelTargetsInfo.TargetsFile,
  verifyPlanUnits: Boolean,
): DevDistBazelComputes {
  val half = CommunityDevDistHalf
  val outputProvider = BazelModuleOutputProvider(
    state = BazelModuleOutputProviderState(
      modules = project.modules,
      projectHome = projectHome,
      bazelTargetsLoader = { targets },
    ),
    lifetime = null,
    useTestCompilationOutput = true,
  )
  val derivation = productDerivation(communityRoot, outputProvider)
  val packings = buildSpan("derive community plugin packings") {
    derivePluginPackings(half = half, root = communityRoot, outputProvider = outputProvider, derivation = derivation)
  }
  return computeDevDistBazelFiles(
    half = half,
    root = communityRoot,
    outputProvider = outputProvider,
    products = derivation.products,
    derivation = packings,
    verifyPlanUnits = verifyPlanUnits,
    targets = targets,
    testPlugins = emptyList(),
  )
}

/**
 * Renders the community half over the community checkout [communityRoot], see [computeCommunityHalf], and writes it when
 * [commit]. Otherwise, the result reports each changed file as a diff.
 */
@ApiStatus.Internal
fun renderCommunityHalf(
  communityRoot: Path,
  projectHome: Path,
  project: JpsProject,
  targets: BazelTargetsInfo.TargetsFile,
  commit: Boolean,
  verifyPlanUnits: Boolean,
): DevDistHalvesFiles {
  val half = computeCommunityHalf(communityRoot = communityRoot, projectHome = projectHome, project = project, targets = targets, verifyPlanUnits = verifyPlanUnits)
  val results = listOf(half.sections.finish(commitChanges = commit), half.plan.finish(commitChanges = commit))
  return DevDistHalvesFiles(files = results.flatMap { it.files }, diffs = results.flatMap { it.diffs })
}

private val BAZEL_TARGETS_JSON = Json { ignoreUnknownKeys = true }

/** Parses the targets JSON [file]. A test reads a targets JSON that no property of the run names. */
@ApiStatus.Internal
fun readBazelTargetsJson(file: Path): BazelTargetsInfo.TargetsFile {
  return BAZEL_TARGETS_JSON.decodeFromString<BazelTargetsInfo.TargetsFile>(Files.readString(file))
}

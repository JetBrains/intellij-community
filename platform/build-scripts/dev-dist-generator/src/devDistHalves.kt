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
 * Starts the dev-distribution renders of one generator run beside the product-model pipeline: the plugin packings, the
 * `dev` sections and the plan of [half], and then the plan of the community half.
 *
 * The renders read the product model of [productDerivation] and no pipeline output. Only the write decision reads the
 * errors of the pipeline, so [DevDistHalvesRun.finish] takes it afterwards. A run without `bazel-targets.json` forks
 * no section and no plan render. A fork carries the current context, so a span that a render starts nests under the
 * span of the run.
 *
 * [testPlugins] are the Product DSL test plugins that a run-configuration module can be. Both halves read the same
 * specifications.
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
  val root = DevDistGenerationRoot.of(projectRoot, half)
  val products = productDerivation.products
  // The derivation carries no Bazel label, so it runs on every checkout. The dev sections and the plan need
  // `bazel-targets.json`. Both read one plugin packing derivation, so they state one packing per plugin. The plan
  // reads the labels the dev sections decide, so it renders after them.
  val derivationTask = fork("derive plugin packings") {
    buildSpan("derive plugin packings") {
      derivePluginPackings(root = root, outputProvider = outputProvider, derivation = productDerivation)
    }
  }
  val bazelTask = if (!devDistPlanInputExists(projectRoot)) {
    null
  }
  else {
    fork("generate dev-distribution build sections and plan") {
      computeDevDistBazelFiles(
        root = root,
        outputProvider = outputProvider,
        products = products,
        derivation = derivationTask.await(),
        verifyPlanUnits = verifyPlanUnits,
        targets = BazelTargetsInfo.loadBazelTargetsJson(projectRoot),
        testPlugins = testPlugins,
      )
    }
  }
  // The community pass reads its own model and the sections of the first pass, so it renders beside the pipeline
  // after that pass. A run without the community JSON has no community pass, see `computeCommunityDevDistFiles`.
  val communityTask = if (bazelTask == null) {
    null
  }
  else {
    fork("generate community dev-distribution plan") {
      computeCommunityDevDistFiles(
        projectRoot = projectRoot,
        ultimate = bazelTask.await(),
        verifyPlanUnits = verifyPlanUnits,
        testPlugins = testPlugins,
      )
    }
  }
  return DevDistHalvesRun(
    root = root,
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
  private val root: DevDistGenerationRoot,
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
    val projectRoot = root.projectRoot
    val derivation = derivationTask.await()
    val bazelFiles = when {
      bazelTask != null -> bazelTask.await()
      // This run has no `bazel-targets.json`. A validating run reports nothing rather than failing on the missing file:
      // the same validation also runs under Bazel, where the file is a declared input, and that run is what catches a
      // stale plan. A committing run is the converter's own, so there a missing file is a setup error, and the render
      // below fails and says so. That render is sequential, and it costs nothing: it is the run that fails.
      commitPlan -> computeDevDistBazelFiles(
        root = root,
        outputProvider = outputProvider,
        products = products,
        derivation = derivation,
        verifyPlanUnits = verifyPlanUnits,
        targets = BazelTargetsInfo.loadBazelTargetsJson(projectRoot),
        testPlugins = testPlugins,
      )
      else -> null
    }
    val communityPlanCompute = when {
      communityTask != null -> communityTask.await()
      // Only a committing run renders the first plan above, so this render is sequential for the same reason.
      bazelFiles != null -> computeCommunityDevDistFiles(projectRoot, bazelFiles, verifyPlanUnits, testPlugins)
      else -> null
    }
    // A committing run needs the community targets JSON, as it needs the monorepo one. Under Bazel the property names it,
    // and a missing file already stopped the run. An IDE run without the workspace file validates no community pass.
    check(!commitPlan || communityPlanCompute != null) {
      "The community pass needs ${projectRoot.relativize(communityTargetsJson(projectRoot))}." +
      " Run ./community/build/jpsModelToBazelCommunityOnly.cmd first."
    }
    val sections = bazelFiles?.sections?.finish(commitChanges = commitPlan)
    val plan = bazelFiles?.plan?.finish(commitChanges = commitPlan)
    val communityPlan = communityPlanCompute?.plan?.finish(commitChanges = commitPlan)
    // A run without `bazel-targets.json` reports an empty section.
    return DevDistHalvesFiles(
      files = buildList {
        addAll(sections?.files.orEmpty())
        addAll(plan?.files.orEmpty())
        addAll(communityPlan?.files.orEmpty())
      },
      diffs = buildList {
        addAll(sections?.diffs.orEmpty())
        addAll(plan?.diffs.orEmpty())
        addAll(communityPlan?.diffs.orEmpty())
      },
    )
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
 * [sections] is `null` for the community pass, which writes no `dev` section. [buildSections] is the computation both
 * outputs read. The community pass compares its own sections with the ones of the ultimate pass. [ownPackagePlans] are
 * the plan files and the calls that this pass writes into the own package of a community plugin, and the community pass
 * reuses them.
 */
internal class DevDistBazelComputes(
  @JvmField val sections: DevDistPlanCompute?,
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
 * [ultimate] is the result of the ultimate pass, which the community pass reads. It is `null` for the ultimate pass.
 * [testPlugins] are the Product DSL test plugins that a run-configuration module can be.
 */
internal fun computeDevDistBazelFiles(
  root: DevDistGenerationRoot,
  outputProvider: ModuleOutputProvider,
  products: List<DiscoveredProduct>,
  derivation: PluginPackingDerivation,
  verifyPlanUnits: Boolean,
  targets: BazelTargetsInfo.TargetsFile,
  testPlugins: List<TestPluginSpec>,
  ultimate: DevDistBazelComputes? = null,
): DevDistBazelComputes {
  val ultimateSections = ultimate?.buildSections
  val projectRoot = root.projectRoot
  val index = DevDistBazelIndex(
    targets = targets,
    projectRoot = projectRoot,
    communityRoot = root.communityRoot,
    planPackageIsCommunity = root.dependentIsCommunity,
  )
  val files = DevDistBuildFiles(index)
  val walk = buildSpan("walk dev-distribution descriptors") {
    walkDescriptors(
      projectRoot = projectRoot,
      outputProvider = outputProvider,
      products = products,
      generatedModuleSetDescriptors = root.half.generatedModuleSetDescriptors,
    )
  }
  fun computeSections(foreignSections: Set<String>): DevDistBuildSections {
    return buildSpan("generate dev-distribution build sections") {
      computeDevDistBuildSections(
        projectRoot = projectRoot,
        outputProvider = outputProvider,
        products = products,
        walk = walk,
        derivation = derivation,
        index = index,
        files = files,
        half = root.half,
        testPlugins = testPlugins,
        verifyPlanUnits = verifyPlanUnits,
        foreignSections = foreignSections,
        writtenContentModuleJarModules = ultimateSections?.contentModuleJarCalls?.keys,
      )
    }
  }
  val sections = when (ultimateSections) {
    null -> computeSections(emptySet())
    else -> computeForeignAwareSections(ultimateSections, ::computeSections)
  }
  val executions = buildSpan("generate dev-distribution plugin executions") {
    computeDevDistPluginExecutions(root = root, sections = sections, ownPackagePlans = ultimate?.ownPackagePlans)
  }
  val sectionFiles = if (root.writesDevSections) {
    buildSpan("write dev-distribution build sections") {
      writeDevDistBuildSectionFiles(projectRoot = projectRoot, sections = sections, index = index, files = files)
    }
  }
  else {
    null
  }
  val plan = buildSpan("generate dev-distribution plan") {
    computeDevDistPlan(
      root = root,
      outputProvider = outputProvider,
      products = products,
      walk = walk,
      sections = sections,
      executions = executions,
      targets = index.targets,
      runConfigurationRows = derivation.runConfigurations.rows,
    )
  }
  return DevDistBazelComputes(
    sections = sectionFiles,
    plan = plan,
    buildSections = sections,
    ownPackagePlans = DevDistOwnPackagePlans.of(executions.files, executions.rendering),
  )
}

/**
 * The sections of the community pass. The first computation finds the plugins whose `dev` section the pass cannot read,
 * see [foreignDevSections]. The second computation moves their leaf and their `dev_plugin` into the generated plugin
 * package. The census prints one line per such plugin.
 *
 * The community pass names only a `content_module_jar` call that the ultimate pass writes. Such a call must have the
 * text of the ultimate call, because only the ultimate pass writes it. A difference stops the run.
 */
private fun computeForeignAwareSections(
  ultimateSections: DevDistBuildSections,
  computeSections: (Set<String>) -> DevDistBuildSections,
): DevDistBuildSections {
  val first = computeSections(emptySet())
  val divergentCalls = divergentContentModuleJarCalls(community = first, ultimate = ultimateSections)
  check(divergentCalls.isEmpty()) {
    val module = divergentCalls.first()
    "The community pass states another content_module_jar call than the ultimate pass for $divergentCalls." +
    " Only the ultimate pass writes these calls. The call of '$module' in the community pass:\n" +
    first.contentModuleJarCalls.get(module) + "\nand in the ultimate pass:\n" + ultimateSections.contentModuleJarCalls.get(module)
  }
  val foreign = foreignDevSections(community = first, ultimate = ultimateSections)
  for (mainModule in foreign) {
    println("product package of $mainModule: the community pass states another leaf or packaging than its dev section")
  }
  println("community pass: ${foreign.size} product packages")
  return if (foreign.isEmpty()) first else computeSections(foreign)
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
    ?: return DevDistGenerationRoot.community(projectRoot).outputRoot.resolve("build/bazel-targets.json")
  val path = Path.of(configured)
  val file = if (path.isAbsolute) path else BazelRunfiles.resolveRunfilePath(configured)
  check(Files.exists(file)) {
    "The community targets JSON $file does not exist. The property $COMMUNITY_TARGETS_JSON_FILE_PROPERTY names '$configured'." +
    " Add @community//build:community_bazel_targets_json to the data of the target."
  }
  return file
}

/**
 * The community pass: the plan of the community products over the community JPS model, written under `community/`.
 *
 * The pass reads the community targets JSON, see [communityTargetsJson]. Both targets JSON files spell a community label
 * alike, so under Bazel a module jar resolves through the runfiles of the tool. Out of Bazel, a jar path of the community
 * targets JSON is below the community output directory of the monorepo, and the provider resolves it there. `null` when
 * the property is not set and the workspace file does not exist. The caller decides what that means.
 */
internal fun computeCommunityDevDistFiles(
  projectRoot: Path,
  ultimate: DevDistBazelComputes,
  verifyPlanUnits: Boolean,
  testPlugins: List<TestPluginSpec>,
): DevDistBazelComputes? {
  val root = DevDistGenerationRoot.community(projectRoot)
  val targetsFile = communityTargetsJson(projectRoot)
  if (!Files.exists(targetsFile)) {
    return null
  }
  val targets = buildSpan("load community bazel-targets.json") { communityTargetsSeenFromMonorepo(readBazelTargetsJson(targetsFile)) }
  val project = loadGeneratorJpsProject(root.outputRoot)
  val outputProvider = BazelModuleOutputProvider(
    state = BazelModuleOutputProviderState(
      modules = project.modules,
      projectHome = projectRoot,
      bazelTargetsLoader = { targets },
    ),
    lifetime = null,
    useTestCompilationOutput = true,
  )
  val derivation = productDerivation(root.outputRoot, outputProvider)
  val packings = buildSpan("derive community plugin packings") {
    derivePluginPackings(root = root, outputProvider = outputProvider, derivation = derivation)
  }
  return computeDevDistBazelFiles(
    root = root,
    outputProvider = outputProvider,
    products = derivation.products,
    derivation = packings,
    verifyPlanUnits = verifyPlanUnits,
    targets = targets,
    testPlugins = testPlugins,
    ultimate = ultimate,
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

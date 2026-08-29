// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

internal data class ModuleDescriptor(
  @JvmField val imlFile: Path,
  @JvmField val module: JpsModule,
  @JvmField val contentRoots: List<Path>,
  @JvmField val sources: List<SourceDirDescriptor>,
  @JvmField val resources: List<ResourceDescriptor>,
  @JvmField val testSources: List<SourceDirDescriptor>,
  @JvmField val testResources: List<ResourceDescriptor>,
  @JvmField val isCommunity: Boolean,
  @JvmField val bazelBuildFileDir: Path,
  @JvmField val relativePathFromProjectRoot: Path,
  @JvmField val targetName: String,
) {
  init {
    require(bazelBuildFileDir.isAbsolute) {
      "bazelBuildFileDir must be absolute: $bazelBuildFileDir"
    }

    require(!relativePathFromProjectRoot.isAbsolute) {
      "relativePathFromProjectRoot must be relative: $relativePathFromProjectRoot"
    }

    require(bazelBuildFileDir.endsWith(relativePathFromProjectRoot) || relativePathFromProjectRoot.toString().isEmpty()) {
      "bazelBuildFileDir must end with relativePathFromProjectRoot: bazelBuildFileDir=$bazelBuildFileDir, relativePathFromProjectRoot=$relativePathFromProjectRoot"
    }

    require(imlFile.isAbsolute) {
      "imlFile must be an absolute path: $imlFile"
    }

    require(imlFile.exists()) {
      "imlFile must be exist: $imlFile"
    }

    require(imlFile.isRegularFile()) {
      "imlFile must be a regular file: $imlFile"
    }
  }

  val targetAsLabel = BazelLabel(targetName, this)

  /**
   * The `module-content.yaml` recipe beside this module's first content root - which is where the content-report
   * writer puts it (`contentChecker.kt` resolves `module.contentRootsList.urls.first()`), and not always the directory
   * holding the `.iml`. Existence and parse are cached separately because they answer different questions:
   * `ModuleList.contentModuleNames` needs only existence, while a recipe that exists but holds several entries still
   * parses to a `null` [contentModuleRecipe].
   */
  val contentModuleRecipeFile: Path? by lazy(LazyThreadSafetyMode.NONE) {
    contentRoots.firstOrNull()?.resolve(CONTENT_MODULE_RECIPE_FILE_NAME)?.takeIf { it.isRegularFile() }
  }

  /**
   * [contentModuleRecipeFile], parsed at most once per module. Cached here because the plugin-content pass asks per
   * (plugin, member) relation, so an uncached read would re-parse the same file once per plugin shipping the module.
   */
  val contentModuleRecipe: RecipeEntry? by lazy(LazyThreadSafetyMode.NONE) { parseContentModuleRecipe(contentModuleRecipeFile) }

  /**
   * The content report of the plugin whose main module this is, if it has one; beside the first content root, the same
   * rule [contentModuleRecipeFile] follows.
   *
   * Existence only, deliberately. The Bazel side probes for exactly these files
   * (`_find_plugin_content_report_rel_path` in `@community//build:jps_model.bzl`) so that the hermetic
   * `bazel-targets.json` run is handed the same reports the full-checkout run reads, and it cannot parse YAML. Both
   * sides therefore have to agree only on *which file is a plugin's report*, which [JpsModuleToBazelTargetsOnly]
   * asserts; whether that report then yields a content target is [pluginContentReport]'s business alone.
   */
  val pluginContentReportFile: Path? by lazy(LazyThreadSafetyMode.NONE) {
    contentRoots.firstOrNull()?.resolve(PLUGIN_CONTENT_REPORT_FILE_NAME)?.takeIf { it.isRegularFile() }
  }

  /** [pluginContentReportFile], parsed at most once per module. */
  val pluginContentReport: List<RecipeEntry>? by lazy(LazyThreadSafetyMode.NONE) { parsePluginContentReport(pluginContentReportFile) }

  /**
   * The descriptor report of the plugin whose main module this is, if it has one; beside the first content root, the
   * same rule [pluginContentReportFile] follows.
   *
   * Existence only, for the reason [pluginContentReportFile] gives. `_find_plugin_descriptor_report_rel_path` in
   * `@community//build:jps_model.bzl` probes for exactly this file so that the hermetic `bazel-targets.json` run is
   * handed the same reports the full-checkout run reads, and it cannot parse YAML.
   */
  val pluginDescriptorReportFile: Path? by lazy(LazyThreadSafetyMode.NONE) {
    contentRoots.firstOrNull()?.resolve(PLUGIN_DESCRIPTOR_REPORT_FILE_NAME)?.takeIf { it.isRegularFile() }
  }

  /** [pluginDescriptorReportFile], parsed at most once per module. */
  val pluginDescriptorReport: Map<String, PluginDescriptorReportSection?>? by lazy(LazyThreadSafetyMode.NONE) {
    parsePluginDescriptorReport(pluginDescriptorReportFile)
  }

  /**
   * The dev-distribution residue of the plugin whose main module this is, if it has one; beside the first content root,
   * the same rule [pluginContentReportFile] follows.
   *
   * Existence only, for the reason [pluginContentReportFile] gives. `_find_dev_dist_residue_rel_path` in
   * `@community//build:jps_model.bzl` probes for exactly this file, so both sides agree on which file is a plugin's
   * residue without either of them parsing YAML.
   */
  val devDistResidueFile: Path? by lazy(LazyThreadSafetyMode.NONE) {
    contentRoots.firstOrNull()?.resolve(DEV_DIST_RESIDUE_FILE_NAME)?.takeIf { it.isRegularFile() }
  }

  /** [devDistResidueFile], parsed at most once per module. */
  val devDistResidue: DevDistResidueFile? by lazy(LazyThreadSafetyMode.NONE) { parseDevDistResidue(devDistResidueFile) }
}

internal data class ResourceDescriptor(
  @JvmField val baseDirectory: String,
  @JvmField val files: List<String>,
  @JvmField val relativeOutputPath: String,
  @JvmField val root: Path,
  @JvmField val excludes: List<String>,
)

internal data class SourceDirDescriptor(
  @JvmField val glob: List<String>,
  @JvmField val excludes: List<String>,
)
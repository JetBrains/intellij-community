// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.plugins

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.impl.BazelModuleOutputProvider
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.bazel.runBazelBuild
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

internal data class PluginsSplitByBuildingMethod(
  val inProcess: Collection<PluginLayout>,
  val byBazel: List<PluginBuiltByBazelDescriptor>,
)

internal data class PluginBuiltByBazelDescriptor(
  val mainModule: String,
  val bazelTarget: String,
  val pluginDistributionDirectory: Path,
)

internal fun partitionPluginsByBuildingMethod(pluginLayouts: Collection<PluginLayout>, buildContext: BuildContext): PluginsSplitByBuildingMethod {
  if (!buildContext.options.buildPluginsByBazel) return PluginsSplitByBuildingMethod(pluginLayouts, emptyList())
  if (buildContext.outputProvider !is BazelModuleOutputProvider) {
    buildContext.messages.logErrorAndThrow("Cannot build plugins by Bazel because output provider is not BazelModuleOutputProvider: ${buildContext.outputProvider}")
  }
  val outputProvider = buildContext.outputProvider as BazelModuleOutputProvider
  val pluginsToBuildByScripts = ArrayList<PluginLayout>()
  val pluginsToBuildByBazel = ArrayList<PluginBuiltByBazelDescriptor>()
  for (layout in pluginLayouts) {
    val bazelTargetDescription = outputProvider.findPluginDistributionTargetDescription(layout.mainModule)
    if (bazelTargetDescription != null) {
      pluginsToBuildByBazel.add(PluginBuiltByBazelDescriptor(layout.mainModule, bazelTargetDescription.target, buildContext.paths.projectHome.resolve(bazelTargetDescription.distributionDirectory)))
    }
    else {
      pluginsToBuildByScripts.add(layout)
    }
  }
  return PluginsSplitByBuildingMethod(pluginsToBuildByScripts, pluginsToBuildByBazel)
}

internal suspend fun buildPluginsByBazel(plugins: List<PluginBuiltByBazelDescriptor>, targetDir: Path, buildContext: BuildContext): List<PluginBuildResult> {
  if (plugins.isEmpty()) return emptyList()
  val pluginsTargets = plugins.map { it.bazelTarget }
  spanBuilder("build plugins by Bazel")
    .setAttribute(AttributeKey.stringArrayKey("targets"), pluginsTargets)
    .use {
      runBazelBuild(pluginsTargets, buildContext)
    }

  val buildResults = spanBuilder("copy plugins built by Bazel").use {
    plugins.mapConcurrent(workerDispatcher = Dispatchers.IO) { plugin ->
      if (!plugin.pluginDistributionDirectory.exists()) {
        buildContext.messages.logErrorAndThrow("Cannot copy the plugin distribution for '${plugin.mainModule}' because '${plugin.pluginDistributionDirectory}' does not exist")
      }
      val pluginTargetDir = targetDir.resolve(plugin.pluginDistributionDirectory.name)
      copyDir(plugin.pluginDistributionDirectory, pluginTargetDir)
      val pluginContentYamlPath = plugin.pluginDistributionDirectory.parent.resolve("plugin-content.yaml")
      if (!pluginContentYamlPath.exists()) {
        buildContext.messages.logErrorAndThrow("Cannot build '${plugin.mainModule}' because '${pluginContentYamlPath}' does not exist")
      }
      val distributionFileEntries = parsePluginContentYaml(pluginContentYamlPath, plugin.mainModule, plugin.pluginDistributionDirectory)
      PluginBuildResult(plugin.mainModule, pluginTargetDir, os = null, arch = null, distributionFileEntries)
    }
  }
  return buildResults
}
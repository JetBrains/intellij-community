// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.plugins

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.generateInclusionReasonForContentModule
import org.jetbrains.intellij.build.impl.BazelModuleOutputProvider
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.bazel.runBazelBuild
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.readEntryFromZip
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

internal suspend fun buildPluginsByBazel(
  plugins: List<PluginBuiltByBazelDescriptor>,
  targetDir: Path,
  descriptorCacheContainer: DescriptorCacheContainer,
  buildContext: BuildContext
): List<PluginBuildResult> {
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
      val distributionFileEntries = parsePluginContentYaml(pluginContentYamlPath, plugin.mainModule, pluginTargetDir)
      val pluginBuildResult = PluginBuildResult(plugin.mainModule, pluginTargetDir, os = null, arch = null, distributionFileEntries)
      storeXmlDescriptorsInCache(descriptorCacheContainer.forPlugin(pluginTargetDir), pluginBuildResult)
      pluginBuildResult
    }
  }
  return buildResults
}

/**
 * Stores content of plugin and module descriptors in the cache so [org.jetbrains.intellij.build.classPath.generatePluginClassPath] and `fetchPluginDescriptorDataForHeader` can
 * find them there.
 */
private fun storeXmlDescriptorsInCache(descriptorCacheContainer: ScopedCachedDescriptorContainer, pluginBuildResult: PluginBuildResult) {
  //todo optimize this either by exporting files as separate outputs from the rule or by migrating usages to use a different way to get the necessary information
  pluginBuildResult.distribution.asSequence().filterIsInstance<ModuleOutputEntry>().forEach { entry ->
    if (entry.owner.moduleName == pluginBuildResult.mainModule) {
      val pluginXmlContent = readEntryFromZip(entry.path, PLUGIN_XML_RELATIVE_PATH) ?: error("Cannot find $PLUGIN_XML_RELATIVE_PATH in ${entry.path}")
      descriptorCacheContainer.put(PLUGIN_XML_RELATIVE_PATH, pluginXmlContent)
    }
    if (entry.reason == generateInclusionReasonForContentModule(pluginBuildResult.mainModule)) {
      val moduleDescriptorPath = "${entry.owner.moduleName}.xml"
      val moduleDescriptorContent = readEntryFromZip(entry.path, moduleDescriptorPath)
      if (moduleDescriptorContent != null) {
        descriptorCacheContainer.put(moduleDescriptorPath, moduleDescriptorContent)
      }
    }
  }
}
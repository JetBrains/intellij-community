// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.plugins

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.deserializeContentData
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.generateInclusionReasonForContentModule
import org.jetbrains.intellij.build.impl.BazelModuleOutputProvider
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.bazel.runBazelBuild
import org.jetbrains.intellij.build.impl.isIncludePluginsInBuiltinCustomRepository
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.readEntryFromZip
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

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
    // An entry with no `target` is one that only records the plugin's dev-distribution content target, which says
    // nothing about whether Bazel can package the plugin - `ij_plugin` is what does, and it is opt-in per descriptor.
    if (bazelTargetDescription != null && bazelTargetDescription.target.isNotEmpty()) {
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
      val additionalArguments = listOfNotNull(
        buildContext.options.buildNumber?.let {
          "--ide_build_number=$it"
        },
        "--ide_stability_level=${computeIdeStabilityLevel(buildContext)}",
        "--ij_plugin_version=${buildContext.pluginBuildNumber}",
        "--ij_plugin_force_exact_build_compatibility".takeIf {
          isIncludePluginsInBuiltinCustomRepository(buildContext)
        },
      )
      runBazelBuild(pluginsTargets, additionalArguments, buildContext)
    }

  val buildResults = spanBuilder("copy plugins built by Bazel").use {
    plugins.mapConcurrent(workerDispatcher = Dispatchers.IO) { plugin ->
      if (!plugin.pluginDistributionDirectory.exists()) {
        buildContext.messages.logErrorAndThrow("Cannot copy the plugin distribution for '${plugin.mainModule}' because '${plugin.pluginDistributionDirectory}' does not exist")
      }
      val pluginTargetDir = targetDir.resolve(plugin.pluginDistributionDirectory.name)
      copyDir(plugin.pluginDistributionDirectory, pluginTargetDir)
      val packedModulesPath = plugin.pluginDistributionDirectory.parent.resolve("packed-modules.yaml")
      if (!packedModulesPath.exists()) {
        buildContext.messages.logErrorAndThrow("Cannot build '${plugin.mainModule}' because '${packedModulesPath}' does not exist")
      }
      val distributionFileEntries = readPackedModules(packedModulesPath, plugin.mainModule, pluginTargetDir)
      val pluginBuildResult = PluginBuildResult(plugin.mainModule, pluginTargetDir, os = null, arch = null, distributionFileEntries)
      storeXmlDescriptorsInCache(descriptorCacheContainer.forPlugin(pluginTargetDir), pluginBuildResult)
      pluginBuildResult
    }
  }
  return buildResults
}

/**
 * Reads the `packed-modules.yaml` that the `ij_plugin` Bazel rule writes for one plugin.
 *
 * The file names each jar of the plugin distribution, and under each jar the modules the packager put into it. Its
 * shape is the [FileEntry] shape, so that schema's own deserializer reads it. The conversion to [ModuleOutputEntry]
 * stays local, because nothing outside this builder wants it.
 *
 * Only [FileEntry.modules] and [FileEntry.contentModules] become entries, and [checkOnlyModuleLists] refuses a file
 * that carries anything else. `PackedModulesWriter` writes those two lists and a name, so nothing fails that check
 * today. It is here for the day the writer grows a third list. A silent drop would leave the plugin's distribution
 * entries short of that content.
 *
 * [ModuleOutputEntry] gets a size and a hash of 0, because no reader of these entries asks for a byte count.
 * [com.intellij.platform.distributionContent.ModuleEntry] offers a size, and [checkOnlyModuleLists]
 * refuses a module that states one.
 */
private fun readPackedModules(
  packedModulesPath: Path,
  pluginMainModule: String,
  pluginDistributionDirectory: Path,
): List<DistributionFileEntry> {
  return deserializeContentData(packedModulesPath.readText()).flatMap { entry ->
    checkOnlyModuleLists(packedModulesPath, entry)
    entry.modules.map { convertModuleEntry(it, entry.name, pluginMainModule, isContentModule = false, pluginDistributionDirectory) } +
    entry.contentModules.map { convertModuleEntry(it, entry.name, pluginMainModule, isContentModule = true, pluginDistributionDirectory) }
  }
}

/**
 * Fails when [entry] carries a field that [readPackedModules] does not convert.
 *
 * The comparison is against a copy that holds the name and the two module lists, so the check needs no list of the
 * fields it rejects and a new field in the schema cannot slip past it.
 */
private fun checkOnlyModuleLists(packedModulesPath: Path, entry: FileEntry) {
  check(entry == FileEntry(name = entry.name, modules = entry.modules, contentModules = entry.contentModules)) {
    "$packedModulesPath: entry '${entry.name}' sets a field that the build scripts do not convert: $entry"
  }
  for (module in entry.modules.asSequence() + entry.contentModules.asSequence()) {
    check(module == ModuleEntry(name = module.name)) {
      "$packedModulesPath: module '${module.name}' of '${entry.name}' sets a field that the build scripts do not convert: $module"
    }
  }
}

private fun convertModuleEntry(
  moduleEntry: ModuleEntry,
  relativeJarPath: String,
  pluginMainModule: String,
  isContentModule: Boolean,
  pluginDistributionDirectory: Path,
): ModuleOutputEntry {
  val moduleItem = ModuleItem(
    moduleName = moduleEntry.name,
    relativeOutputFile = relativeJarPath.removePrefix("lib/"),
    reason = if (isContentModule) generateInclusionReasonForContentModule(pluginMainModule) else null,
  )
  return ModuleOutputEntry(
    path = pluginDistributionDirectory.resolve(relativeJarPath),
    owner = moduleItem,
    size = 0,
    hash = 0,
    relativeOutputFile = moduleItem.relativeOutputFile,
    reason = moduleItem.reason,
  )
}

private fun computeIdeStabilityLevel(buildContext: BuildContext): String {
  return when {
    !buildContext.applicationInfo.isEAP -> "release"
    buildContext.options.buildNumber == null -> "snapshot"
    buildContext.isNightlyBuild -> "nightly"
    else -> "EAP"
  }
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
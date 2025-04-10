// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.dev

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.buildPlugins
import org.jetbrains.intellij.build.impl.copyAdditionalPlugins
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.impl.handleCustomPlatformSpecificAssets
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.satisfiesBundlingRequirements
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path

internal suspend fun buildPlugins(
  request: BuildRequest,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  artifactTask: Job,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  buildPlatformJob: Job,
  moduleOutputPatcher: ModuleOutputPatcher,
): Pair<List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, List<Pair<Path, List<Path>>>?> {
  val bundledMainModuleNames = getBundledMainModuleNames(context, request.additionalModules)

  val pluginRootDir = runDir.resolve("plugins")

  val plugins = getPluginLayoutsByJpsModuleNames(bundledMainModuleNames, context.productProperties.productLayout)
    .filter { isPluginApplicable(bundledMainModuleNames = bundledMainModuleNames, plugin = it, context = context) }

  withContext(Dispatchers.IO) {
    Files.createDirectories(pluginRootDir)
  }

  artifactTask.join()

  val platform = platformLayout.await()
  val pluginEntries = spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), plugins.size.toLong()).use {
    val targetPlatform = SupportedDistribution(OsFamily.currentOs, JvmArchitecture.currentJvmArch)
    buildPlugins(
      moduleOutputPatcher = moduleOutputPatcher,
      plugins = plugins,
      targetDir = pluginRootDir,
      state = DistributionBuilderState(platform = platform, pluginsToPublish = emptySet(), context = context),
      context = context,
      buildPlatformJob = buildPlatformJob,
      searchableOptionSet = searchableOptionSet,
      os = null,
      pluginBuilt = { layout, pluginDirOrFile ->
        val distEntries = ArrayList<DistributionFileEntry>()
        handleCustomPlatformSpecificAssets(
          layout = layout,
          targetPlatform = targetPlatform,
          context = context,
          pluginDir = pluginDirOrFile,
          distEntries = distEntries,
          isDevMode = true,
        )
        distEntries
      },
    )
  }
  val additionalPlugins = copyAdditionalPlugins(context, pluginRootDir)
  return pluginEntries to additionalPlugins
}

private fun isPluginApplicable(bundledMainModuleNames: Set<String>, plugin: PluginLayout, context: BuildContext): Boolean {
  if (!bundledMainModuleNames.contains(plugin.mainModule)) {
    return false
  }

  if (plugin.bundlingRestrictions == PluginBundlingRestrictions.NONE) {
    return true
  }

  return satisfiesBundlingRequirements(plugin = plugin, osFamily = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch, context = context) ||
         satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = JvmArchitecture.currentJvmArch, context = context)
}

private suspend fun getBundledMainModuleNames(context: BuildContext, additionalModules: List<String>): Set<String> {
  val bundledPluginModules = context.getBundledPluginModules()
  val result = LinkedHashSet<String>(bundledPluginModules.size + additionalModules.size)
  result.addAll(bundledPluginModules)
  result.addAll(additionalModules)
  return result
}
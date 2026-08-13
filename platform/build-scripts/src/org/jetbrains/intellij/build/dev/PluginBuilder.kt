// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.dev

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.buildPlatformSpecificPluginResources
import org.jetbrains.intellij.build.impl.copyAdditionalPlugins
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.impl.plugins.buildPlugins
import org.jetbrains.intellij.build.impl.plugins.scrambleAlreadyLaidOutPlugins
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.satisfiesBundlingRequirements
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path

internal data class PluginsLayoutResult(
  @JvmField val pluginEntries: List<PluginBuildResult>,
  @JvmField val additionalPlugins: List<Pair<Path, List<Path>>>?,
)

internal enum class DevModePluginBuildStrategy {
  NORMAL,
  LAYOUT_BEFORE_PLATFORM_SCRAMBLE,
}

internal fun selectDevModePluginBuildStrategy(request: BuildRequest, context: BuildContext, pluginLayouts: List<PluginLayout>): DevModePluginBuildStrategy {
  if (!context.productProperties.scrambleMainJar || request.scrambleTool == null || context.isStepSkipped(BuildOptions.SCRAMBLING_STEP)) {
    return DevModePluginBuildStrategy.NORMAL
  }
  return if (pluginLayouts.any { it.scrambleWithPlatform }) {
    DevModePluginBuildStrategy.LAYOUT_BEFORE_PLATFORM_SCRAMBLE
  }
  else {
    DevModePluginBuildStrategy.NORMAL
  }
}

internal suspend fun buildPluginsForDevMode(
  request: BuildRequest,
  pluginLayouts: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  platformEntriesProvider: suspend () -> List<DistributionFileEntry>,
): PluginsLayoutResult {
  val descriptors = buildPluginDescriptorsForDevMode(
    os = request.os,
    arch = request.arch,
    plugins = pluginLayouts,
    context = context,
    runDir = runDir,
    platformLayout = platformLayout,
    searchableOptionSet = searchableOptionSet,
    platformEntriesProvider = platformEntriesProvider,
    layoutOnly = false,
  )
  val additionalPlugins = copyAdditionalPlugins(runDir.resolve("plugins"), context)
  return PluginsLayoutResult(descriptors, additionalPlugins)
}

/**
 * Lays out ALL bundled plugins for dev mode (no scrambling). The result feeds the platform ZKM
 * run via `coScrambleEntriesProvider` / `classpathDirsProvider`, then per-plugin scramble runs
 * after platform scramble via [scrambleAlreadyLaidOutPluginsForDevMode].
 */
internal suspend fun layoutAllPluginsForDevMode(
  request: BuildRequest,
  pluginLayouts: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
): List<PluginBuildResult> {
  return buildPluginDescriptorsForDevMode(
    os = request.os,
    arch = request.arch,
    plugins = pluginLayouts,
    context = context,
    runDir = runDir,
    platformLayout = platformLayout,
    searchableOptionSet = searchableOptionSet,
    platformEntriesProvider = null,
    layoutOnly = true,
  )
}

private suspend fun buildPluginDescriptorsForDevMode(
  os: OsFamily,
  arch: JvmArchitecture,
  plugins: List<PluginLayout>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  platformEntriesProvider: (suspend () -> List<DistributionFileEntry>)?,
  layoutOnly: Boolean,
): List<PluginBuildResult> {
  if (plugins.isEmpty()) return emptyList()
  val pluginRootDir = runDir.resolve("plugins")
  withContext(Dispatchers.IO) {
    Files.createDirectories(pluginRootDir)
  }
  val platform = platformLayout.await()
  val spanName = if (layoutOnly) "lay out plugins" else "build plugins"
  return spanBuilder(spanName).setAttribute(AttributeKey.longKey("count"), plugins.size.toLong()).use {
    val targetPlatform = SupportedDistribution(os = os, arch = arch, libcImpl = LibcImpl.current(os))
    buildPlugins(
      plugins = plugins,
      os = null,
      arch = null,
      targetDir = pluginRootDir,
      state = DistributionBuilderState(platformLayout = platform, pluginsToPublish = emptySet(), context = context),
      platformEntriesProvider = platformEntriesProvider,
      searchableOptionSet = searchableOptionSet,
      descriptorCacheContainer = platform.descriptorCacheContainer,
      context = context,
      layoutOnly = layoutOnly,
    ) { layout, pluginDirOrFile ->
      buildPlatformSpecificPluginResources(
        plugin = layout,
        pluginDirs = listOf(targetPlatform to pluginDirOrFile),
        context = context,
        isDevMode = true,
      )
    }
  }
}

/** Per-plugin scramble for non-co-scramble plugins after platform scramble has completed (dev mode). */
internal suspend fun scrambleAlreadyLaidOutPluginsForDevMode(
  descriptors: List<PluginBuildResult>,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  layoutsOfPluginsToScramble: Map<String, PluginLayout>,
  platformEntriesProvider: suspend () -> List<DistributionFileEntry>,
): PluginsLayoutResult {
  val platform = platformLayout.await()
  val state = DistributionBuilderState(platformLayout = platform, pluginsToPublish = emptySet(), context = context)
  // wait for platform scramble before running per-plugin scramble (it needs the scrambled platform jars on classpath)
  val platformEntries = platformEntriesProvider()
  scrambleAlreadyLaidOutPlugins(
    descriptors = descriptors,
    state = state,
    platformEntries = platformEntries,
    layoutsOfPluginsToScramble = layoutsOfPluginsToScramble,
    context = context,
  )
  val pluginRootDir = runDir.resolve("plugins")
  val additionalPlugins = copyAdditionalPlugins(pluginRootDir, context)
  return PluginsLayoutResult(descriptors, additionalPlugins)
}

internal fun devModePluginCandidates(request: BuildRequest, context: BuildContext): List<PluginLayout> {
  val bundledMainModuleNames = getBundledMainModuleNames(context, request.additionalModules)
  return getPluginLayoutsByJpsModuleNames(bundledMainModuleNames, context.productProperties.productLayout)
    .filter {
      isPluginApplicable(
        bundledMainModuleNames = bundledMainModuleNames,
        plugin = it,
        os = request.os,
        arch = request.arch,
        context = context,
      )
    }
}

internal fun collectLayoutsOfPluginsToScramble(pluginLayouts: Collection<PluginLayout>): Map<String, PluginLayout> {
  return pluginLayouts.asSequence()
    .filter { it.pathsToScramble.isNotEmpty() }
    .groupBy { it.mainModule }
    .mapValues { it.value.singleOrNull() ?: error("Multiple layouts for plugin ${it.key}") }
}

internal fun isPluginApplicable(
  bundledMainModuleNames: Set<String>,
  plugin: PluginLayout,
  os: OsFamily,
  arch: JvmArchitecture,
  context: BuildContext,
): Boolean {
  if (!bundledMainModuleNames.contains(plugin.mainModule)) {
    return false
  }

  if (plugin.bundlingRestrictions == PluginBundlingRestrictions.NONE) {
    return true
  }

  return satisfiesBundlingRequirements(plugin = plugin, osFamily = os, arch = arch, context = context) ||
         satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = arch, context = context)
}

private fun getBundledMainModuleNames(context: BuildContext, additionalModules: List<String>): Set<String> {
  val bundledPluginModules = context.getBundledPluginModules()
  val result = LinkedHashSet<String>(bundledPluginModules.size + additionalModules.size)
  result.addAll(bundledPluginModules)
  result.addAll(additionalModules)
  return result
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.plugins

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.InMemoryDistFileContent
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.classPath.generatePluginClassPath
import org.jetbrains.intellij.build.classPath.generatePluginClassPathFromPrebuiltPluginFiles
import org.jetbrains.intellij.build.classPath.writePluginClassPathHeader
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PLUGINS_DIRECTORY
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import org.jetbrains.intellij.build.impl.PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.buildPlatformSpecificPluginResources
import org.jetbrains.intellij.build.impl.copyAdditionalPlugins
import org.jetbrains.intellij.build.impl.getOsAndArchSpecificDistDirectory
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.satisfiesBundlingRequirements
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.block
import org.jetbrains.intellij.build.telemetry.use
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Path

internal suspend fun buildBundledPluginsForAllPlatforms(
  state: DistributionBuilderState,
  pluginLayouts: Set<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Deferred<List<DistributionFileEntry>>,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  moduleOutputPatcher: ModuleOutputPatcher,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
): List<PluginBuildDescriptor> = coroutineScope {
  val commonDeferred = async(CoroutineName("build bundled plugins")) {
    buildBundledPlugins(
      state = state,
      plugins = pluginLayouts,
      isUpdateFromSources = isUpdateFromSources,
      buildPlatformJob = buildPlatformJob,
      searchableOptionSet = searchableOptionSetDescriptor,
      moduleOutputPatcher = moduleOutputPatcher,
      descriptorCacheContainer = descriptorCacheContainer,
      context = context,
    )
  }

  val additionalDeferred = async(CoroutineName("build additional plugins")) {
    copyAdditionalPlugins(pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY), context = context)
  }

  val pluginDirs = getPluginDirs(context, isUpdateFromSources)
  val specificDeferred = async(CoroutineName("build OS-specific bundled plugins")) {
    buildOsSpecificBundledPlugins(
      state = state,
      plugins = pluginLayouts,
      isUpdateFromSources = isUpdateFromSources,
      buildPlatformJob = buildPlatformJob,
      context = context,
      searchableOptionSet = searchableOptionSetDescriptor,
      pluginDirs = pluginDirs,
      moduleOutputPatcher = moduleOutputPatcher,
      descriptorCacheContainer = descriptorCacheContainer,
    )
  }

  val common = commonDeferred.await()
  val specific = specificDeferred.await()
  buildPlatformJob.join()
  writePluginInfo(
    pluginDirs = pluginDirs,
    common = common,
    specific = specific,
    additional = additionalDeferred.await(),
    platformLayout = state.platformLayout,
    descriptorCacheContainer = descriptorCacheContainer,
    context = context,
  )
  common + specific.values.flatten()
}

private suspend fun buildOsSpecificBundledPlugins(
  state: DistributionBuilderState,
  plugins: Set<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Deferred<List<DistributionFileEntry>>,
  context: BuildContext,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  descriptorCacheContainer: DescriptorCacheContainer,
  moduleOutputPatcher: ModuleOutputPatcher,
): Map<SupportedDistribution, List<PluginBuildDescriptor>> {
  return spanBuilder("build os-specific bundled plugins")
    .setAttribute("isUpdateFromSources", isUpdateFromSources)
    .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), context.options.bundledPluginDirectoriesToSkip.toList())
    .use {
      pluginDirs.mapNotNull { (dist, targetDir) ->
        val (os, arch) = dist
        if (!context.shouldBuildDistributionForOS(os, arch)) {
          return@mapNotNull null
        }

        val osSpecificPlugins = plugins.filter {
          satisfiesBundlingRequirements(plugin = it, osFamily = os, arch = arch, context = context)
        }
        if (osSpecificPlugins.isEmpty()) {
          return@mapNotNull null
        }

        dist to async(CoroutineName("build bundled plugins")) {
          spanBuilder("build bundled plugins")
            .setAttribute("os", os.osName)
            .setAttribute("arch", arch.name)
            .setAttribute("count", osSpecificPlugins.size.toLong())
            .setAttribute("outDir", targetDir.toString())
            .use {
              buildPlugins(
                moduleOutputPatcher = moduleOutputPatcher,
                plugins = osSpecificPlugins,
                os = os,
                arch = arch,
                targetDir = targetDir,
                state = state,
                buildPlatformJob = buildPlatformJob,
                searchableOptionSet = searchableOptionSet,
                descriptorCacheContainer = descriptorCacheContainer,
                context = context,
              )
            }
        }
      }
    }
    .associateBy(keySelector = { it.first }, valueTransform = { it.second.getCompleted() })
}

internal suspend fun buildBundledPlugins(
  state: DistributionBuilderState,
  plugins: Collection<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Deferred<List<DistributionFileEntry>>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  moduleOutputPatcher: ModuleOutputPatcher,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
): List<PluginBuildDescriptor> {
  return spanBuilder("build bundled plugins")
    .setAttribute("isUpdateFromSources", isUpdateFromSources)
    .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), context.options.bundledPluginDirectoriesToSkip.toList())
    .setAttribute("count", plugins.size.toLong())
    .block { span ->
      val pluginsToBundle = ArrayList<PluginLayout>(plugins.size)
      plugins.filterTo(pluginsToBundle) { satisfiesBundlingRequirements(plugin = it, osFamily = null, arch = null, context = context) }
      span.setAttribute("satisfiableCount", pluginsToBundle.size.toLong())

      // doesn't make sense to require passing here a list with a stable order (unnecessary complication, sorting by main module is enough)
      pluginsToBundle.sortWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
      val targetDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
      val platformSpecificPluginDirs = getPluginDirs(context, isUpdateFromSources)
      val entries = buildPlugins(
        moduleOutputPatcher = moduleOutputPatcher,
        plugins = pluginsToBundle,
        os = null,
        arch = null,
        targetDir = targetDir,
        state = state,
        buildPlatformJob = buildPlatformJob,
        searchableOptionSet = searchableOptionSet,
        descriptorCacheContainer = descriptorCacheContainer,
        context = context,
      ) { layout, _ ->
        if (layout.hasPlatformSpecificResources) {
          buildPlatformSpecificPluginResources(plugin = layout, targetDirs = platformSpecificPluginDirs, context = context)
        }
        else {
          emptyList()
        }
      }

      entries
    }
}

private fun getPluginDirs(context: BuildContext, isUpdateFromSources: Boolean): List<Pair<SupportedDistribution, Path>> {
  if (isUpdateFromSources) {
    return listOf(
      SupportedDistribution(os = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch, libcImpl = LibcImpl.current(OsFamily.currentOs)) to
        context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
    )
  }
  else {
    return SUPPORTED_DISTRIBUTIONS.map {
      it to getOsAndArchSpecificDistDirectory(osFamily = it.os, arch = it.arch, libc = it.libcImpl, context = context).resolve(PLUGINS_DIRECTORY)
    }
  }
}

private suspend fun writePluginInfo(
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  common: List<PluginBuildDescriptor>,
  specific: Map<SupportedDistribution, List<PluginBuildDescriptor>>,
  additional: List<Pair<Path, List<Path>>>?,
  platformLayout: PlatformLayout,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
) {
  val commonClassPath = generatePluginClassPath(
    pluginEntries = common,
    descriptorFileProvider = descriptorCacheContainer,
    platformLayout = platformLayout,
    context = context,
  )
  val additionalClassPath = additional?.let { generatePluginClassPathFromPrebuiltPluginFiles(it) }

  for ((supportedDist) in pluginDirs) {
    val specificList = specific.get(supportedDist)
    val specificClasspath = specificList?.let {
      generatePluginClassPath(
        pluginEntries = it,
        descriptorFileProvider = descriptorCacheContainer,
        platformLayout = platformLayout,
        context = context,
      )
    }

    val byteOut = ByteArrayOutputStream()
    val out = DataOutputStream(byteOut)
    val pluginCount = common.size + (additional?.size ?: 0) + (specificList?.size ?: 0)
    writePluginClassPathHeader(
      out = out,
      isJarOnly = true,
      pluginCount = pluginCount,
      platformLayout = platformLayout,
      descriptorCacheContainer = descriptorCacheContainer,
      context = context,
    )
    withContext(Dispatchers.IO) {
      out.write(commonClassPath)
      additionalClassPath?.let { out.write(it) }
      specificClasspath?.let { out.write(it) }
      out.close()
    }

    context.addDistFile(
      DistFile(
        content = InMemoryDistFileContent(byteOut.toByteArray()),
        relativePath = PLUGIN_CLASSPATH,
        os = supportedDist.os,
        libcImpl = supportedDist.libcImpl,
        arch = supportedDist.arch,
      )
    )
  }
}

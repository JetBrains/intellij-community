// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.plugins

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ScrambleTool
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.antToRegex
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.hasModuleOutputPath
import org.jetbrains.intellij.build.impl.BUILT_IN_HELP_MODULE_NAME
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.layoutDistribution
import org.jetbrains.intellij.build.impl.patchPluginXml
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path

private class ScrambleTask(@JvmField val pluginLayout: PluginLayout, @JvmField val pluginDir: Path, @JvmField val targetDir: Path)

internal suspend fun buildPlugins(
  moduleOutputPatcher: ModuleOutputPatcher,
  plugins: Collection<PluginLayout>,
  os: OsFamily?,
  targetDir: Path,
  state: DistributionBuilderState,
  context: BuildContext,
  buildPlatformJob: Job?,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  descriptorCacheContainer: DescriptorCacheContainer,
  pluginBuilt: (suspend (PluginLayout, pluginDirOrFile: Path) -> List<DistributionFileEntry>)? = null,
): List<PluginBuildDescriptor> {
  val scrambleTool = context.proprietaryBuildTools.scrambleTool
  val isScramblingSkipped = context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)
  val results = coroutineScope {
    plugins.map { pluginLayout ->
      buildPlugin(
        pluginLayout = pluginLayout,
        targetDir = targetDir,
        moduleOutputPatcher = moduleOutputPatcher,
        state = state,
        descriptorCacheContainer = descriptorCacheContainer,
        searchableOptionSet = searchableOptionSet,
        scrambleTool = scrambleTool,
        isScramblingSkipped = isScramblingSkipped,
        os = os,
        context = context,
        pluginBuilt = pluginBuilt,
      )
    }
  }

  val scrambleTasks = results.mapNotNull { it.second }
  if (scrambleTasks.isNotEmpty()) {
    checkNotNull(scrambleTool)

    // scrambling can require classes from the platform
    buildPlatformJob?.let { task ->
      spanBuilder("wait for platform lib for scrambling").use { task.join() }
    }
    coroutineScope {
      for (scrambleTask in scrambleTasks) {
        launch(CoroutineName("scramble plugin ${scrambleTask.pluginLayout.directoryName}")) {
          scrambleTool.scramblePlugin(
            pluginLayout = scrambleTask.pluginLayout,
            platformLayout = state.platformLayout,
            targetDir = scrambleTask.pluginDir,
            additionalPluginDir = scrambleTask.targetDir,
            layouts = plugins,
            context = context,
          )
        }
      }
    }
  }
  return results.map { it.first }
}

private suspend fun CoroutineScope.buildPlugin(
  pluginLayout: PluginLayout,
  targetDir: Path,
  moduleOutputPatcher: ModuleOutputPatcher,
  state: DistributionBuilderState,
  descriptorCacheContainer: DescriptorCacheContainer,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  scrambleTool: ScrambleTool?,
  isScramblingSkipped: Boolean,
  os: OsFamily?,
  context: BuildContext,
  pluginBuilt: (suspend (PluginLayout, Path) -> List<DistributionFileEntry>)?,
): Pair<PluginBuildDescriptor, ScrambleTask?> {
  val directoryName = pluginLayout.directoryName
  val pluginDir = targetDir.resolve(directoryName)

  if (pluginLayout.mainModule != BUILT_IN_HELP_MODULE_NAME) {
    launch {
      checkOutputOfPluginModules(
        mainPluginModule = pluginLayout.mainModule,
        includedModules = pluginLayout.includedModules,
        moduleExcludes = pluginLayout.moduleExcludes,
        context = context,
      )
    }

    patchPluginXml(
      moduleOutputPatcher = moduleOutputPatcher,
      pluginLayout = pluginLayout,
      releaseDate = context.applicationInfo.majorReleaseDate,
      releaseVersion = context.applicationInfo.releaseVersionForLicensing,
      pluginsToPublish = state.pluginsToPublish,
      platformDescriptorCache = descriptorCacheContainer.forPlatform(state.platformLayout),
      pluginDescriptorCache = descriptorCacheContainer.forPlugin(pluginDir),
      platformLayout = state.platformLayout,
      context = context,
    )
  }

  val task = async(CoroutineName("Build plugin (module=${pluginLayout.mainModule})")) {
    spanBuilder("plugin").setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString()).use {
      val (entries, file) = layoutDistribution(
        layout = pluginLayout,
        platformLayout = state.platformLayout,
        targetDir = pluginDir,
        copyFiles = true,
        moduleOutputPatcher = moduleOutputPatcher,
        includedModules = pluginLayout.includedModules,
        searchableOptionSet = searchableOptionSet,
        cachedDescriptorWriterProvider = descriptorCacheContainer.forPlugin(pluginDir),
        context = context,
      )

      if (pluginBuilt == null) {
        entries
      }
      else {
        entries + pluginBuilt(pluginLayout, file)
      }
    }
  }

  var scrambleTask: ScrambleTask? = null
  if (!pluginLayout.pathsToScramble.isEmpty()) {
    val attributes = Attributes.of(AttributeKey.stringKey("plugin"), directoryName)
    if (scrambleTool == null) {
      Span.current().addEvent("skip scrambling plugin because scrambleTool isn't defined, but plugin defines paths to be scrambled", attributes)
    }
    else if (isScramblingSkipped) {
      Span.current().addEvent("skip scrambling plugin because step is disabled", attributes)
    }
    else {
      // we cannot start executing right now because the plugin can use other plugins in a scramble classpath
      scrambleTask = ScrambleTask(pluginLayout = pluginLayout, pluginDir = pluginDir, targetDir = targetDir)
    }
  }
  return PluginBuildDescriptor(dir = pluginDir, os = os, layout = pluginLayout, distribution = task.await()) to scrambleTask
}

private fun checkOutputOfPluginModules(
  mainPluginModule: String,
  includedModules: Collection<ModuleItem>,
  moduleExcludes: Map<String, List<String>>,
  context: BuildContext,
) {
  for (module in includedModules.asSequence().map { it.moduleName }.distinct()) {
    if (module != "intellij.java.guiForms.rt" ||
        !containsFileInOutput(module, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes.get(module) ?: emptyList(), context)) {
      continue
    }

    error(
      "Runtime classes of GUI designer must not be packaged to '$module' module in '$mainPluginModule' plugin, " +
      "because they are included into a platform JAR. Make sure that 'Automatically copy form runtime classes " +
      "to the output directory' is disabled in Settings | Editor | GUI Designer."
    )
  }
}

private fun containsFileInOutput(moduleName: String, @Suppress("SameParameterValue") filePath: String, excludes: Collection<String>, context: BuildContext): Boolean {
  val exists = hasModuleOutputPath(module = context.findRequiredModule(moduleName), relativePath = filePath, context = context)
  if (!exists) {
    return false
  }

  for (exclude in excludes) {
    if (antToRegex(exclude).matches(FileUtilRt.toSystemIndependentName(filePath))) {
      return false
    }
  }

  return true
}
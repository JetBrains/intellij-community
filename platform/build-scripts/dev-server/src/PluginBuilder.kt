// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.UNMODIFIED_MARK_FILE_NAME
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.LongAdder

internal data class PluginBuildDescriptor(@JvmField val dir: Path,
                                          @JvmField val layout: PluginLayout,
                                          @JvmField val moduleNames: List<String>)

internal suspend fun buildPlugins(pluginBuildDescriptors: List<PluginBuildDescriptor>,
                                  outDir: Path,
                                  pluginCacheRootDir: Path,
                                  platformLayout: PlatformLayout,
                                  context: BuildContext): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), pluginBuildDescriptors.size.toLong()).useWithScope { span ->
    val counter = LongAdder()
    val pluginEntries = coroutineScope {
      pluginBuildDescriptors.map { plugin ->
        async {
          plugin to buildPluginIfNotCached(plugin = plugin,
                                           platformLayout = platformLayout,
                                           outDir = outDir,
                                           pluginCacheRootDir = pluginCacheRootDir,
                                           context = context,
                                           reusedPluginsCounter = counter)
        }
      }
    }.map { it.getCompleted() }
    span.setAttribute("reusedCount", counter.toLong())
    pluginEntries
  }
}

internal suspend fun buildPluginIfNotCached(plugin: PluginBuildDescriptor,
                                            outDir: Path,
                                            pluginCacheRootDir: Path,
                                            platformLayout: PlatformLayout,
                                            context: BuildContext,
                                            reusedPluginsCounter: LongAdder): List<DistributionFileEntry> {
  val mainModule = plugin.layout.mainModule

  val reason = withContext(Dispatchers.IO) {
    // check cache
    val reason = checkCache(plugin = plugin, projectOutDir = outDir, pluginCacheRootDir = pluginCacheRootDir)

    if (reason == null || reason == "initial build") {
      if (plugin.layout.mainModule == "intellij.rider.plugins.clion.radler" && hasResourcePaths(plugin.layout)) {
        // copy custom resources
        spanBuilder("build plugin")
          .setAttribute("mainModule", mainModule)
          .setAttribute("dir", plugin.layout.directoryName)
          .setAttribute("reason", "copy custom resources")
          .useWithScope2 {
            layoutResourcePaths(layout = plugin.layout, context = context, targetDirectory = plugin.dir, overwrite = true)
          }
      }
    }

    if (reason == null) return@withContext null

    if (mainModule != "intellij.platform.builtInHelp") {
      checkOutputOfPluginModules(mainPluginModule = mainModule,
                                 includedModules = plugin.layout.includedModules,
                                 moduleExcludes = plugin.layout.moduleExcludes,
                                 context = context)
    }
    reason
  }
  if (reason == null) {
    reusedPluginsCounter.add(1)
    if (context.generateRuntimeModuleRepository) {
      return buildPlugin(plugin = plugin,
                         reason = "generate runtime module repository",
                         platformLayout = platformLayout,
                         context = context,
                         copyFiles = false)
    }
    return emptyList()
  }

  return buildPlugin(plugin = plugin,
                     reason = reason,
                     platformLayout = platformLayout,
                     context = context,
                     copyFiles = true)
}

private suspend fun buildPlugin(plugin: PluginBuildDescriptor,
                                reason: String,
                                platformLayout: PlatformLayout,
                                context: BuildContext,
                                copyFiles: Boolean): List<DistributionFileEntry> {
  val moduleOutputPatcher = ModuleOutputPatcher()
  return spanBuilder("build plugin")
    .setAttribute("mainModule", plugin.layout.mainModule)
    .setAttribute("dir", plugin.layout.directoryName)
    .setAttribute("reason", reason)
    .useWithScope {
      val (pluginEntries, _) = layoutDistribution(layout = plugin.layout,
                                                  platformLayout = platformLayout, targetDirectory = plugin.dir,

                                                  moduleOutputPatcher = moduleOutputPatcher,
                                                  includedModules = plugin.layout.includedModules,
                                                  copyFiles = copyFiles,
                                                  // searchable options are not generated in dev mode
                                                  moduleWithSearchableOptions = emptySet(),
                                                  context = context)
      pluginEntries
    }
}

private fun checkCache(plugin: PluginBuildDescriptor, projectOutDir: Path, pluginCacheRootDir: Path): String? {
  val dirName = plugin.layout.directoryName
  val cacheDir = pluginCacheRootDir.resolve(dirName).takeIf { Files.exists(it) }
  if (cacheDir == null) {
    return "initial build"
  }

  val reason = isCacheUpToDate(plugin, projectOutDir)
  if (reason == null) {
    Files.move(cacheDir, plugin.dir)
    return null
  }
  else {
    return reason
  }
}

private fun isCacheUpToDate(plugin: PluginBuildDescriptor, projectOutDir: Path): String? {
  for (moduleName in plugin.moduleNames) {
    if (Files.notExists(projectOutDir.resolve(moduleName).resolve(UNMODIFIED_MARK_FILE_NAME))) {
      return "at least $moduleName is changed"
    }
  }
  return null
}

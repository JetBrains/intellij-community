// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.diagnostic.telemetry.impl.useWithScope2
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.checkOutputOfPluginModules
import org.jetbrains.intellij.build.impl.layoutDistribution
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.atomic.LongAdder

private val TOUCH_OPTIONS = EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE)

internal fun createMarkFile(file: Path) {
  try {
    Files.newByteChannel(file, TOUCH_OPTIONS)
  }
  catch (ignore: NoSuchFileException) {
  }
}

internal data class PluginBuildDescriptor(@JvmField val dir: Path,
                                          @JvmField val layout: PluginLayout,
                                          @JvmField val moduleNames: List<String>) {
  fun markAsBuilt(outDir: Path) {
    for (moduleName in moduleNames) {
      createMarkFile(outDir.resolve(moduleName).resolve(UNMODIFIED_MARK_FILE_NAME))
    }
  }
}

internal suspend fun buildPlugins(pluginBuildDescriptors: List<PluginBuildDescriptor>,
                                  outDir: Path,
                                  pluginCacheRootDir: Path,
                                  context: BuildContext) {
  spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), pluginBuildDescriptors.size.toLong()).useWithScope2 { span ->
    val counter = LongAdder()
    coroutineScope {
      for (plugin in pluginBuildDescriptors) {
        launch {
          if (buildPlugin(plugin = plugin, outDir = outDir, pluginCacheRootDir = pluginCacheRootDir, context = context)) {
            counter.add(1)
          }
        }
      }
    }
    span.setAttribute("reusedCount", counter.toLong())
  }
}

internal suspend fun buildPlugin(plugin: PluginBuildDescriptor, outDir: Path, pluginCacheRootDir: Path, context: BuildContext): Boolean {
  val mainModule = plugin.layout.mainModule

  val reason = withContext(Dispatchers.IO) {
    // check cache
    val reason = checkCache(plugin = plugin, projectOutDir = outDir, pluginCacheRootDir = pluginCacheRootDir)
    if (reason == null) {
      return@withContext null
    }

    if (mainModule != "intellij.platform.builtInHelp") {
      checkOutputOfPluginModules(mainPluginModule = mainModule,
                                 includedModules = plugin.layout.includedModules,
                                 moduleExcludes = plugin.layout.moduleExcludes,
                                 context = context)
    }
    reason
  } ?: return true

  val moduleOutputPatcher = ModuleOutputPatcher()
  return spanBuilder("build plugin")
    .setAttribute("mainModule", mainModule)
    .setAttribute("dir", plugin.layout.directoryName)
    .setAttribute("reason", reason)
    .useWithScope2 {
      layoutDistribution(layout = plugin.layout,
                         targetDirectory = plugin.dir,
                         moduleOutputPatcher = moduleOutputPatcher,
                         includedModules = plugin.layout.includedModules,
                         context = context)
      withContext(Dispatchers.IO) {
        plugin.markAsBuilt(outDir)
      }

      false
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

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("BlockingMethodInNonBlockingContext", "PrivatePropertyName")

package org.jetbrains.intellij.build.devServer

import com.intellij.diagnostic.telemetry.useWithScope2
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
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

internal data class PluginBuildDescriptor(
  @JvmField val dir: Path,
  @JvmField val layout: PluginLayout,
  @JvmField val moduleNames: List<String>,
) {
  fun markAsBuilt(outDir: Path) {
    for (moduleName in moduleNames) {
      createMarkFile(outDir.resolve(moduleName).resolve(UNMODIFIED_MARK_FILE_NAME))
    }
  }
}

internal suspend fun buildPlugins(pluginBuildDescriptors: List<PluginBuildDescriptor>, pluginBuilder: PluginBuilder) {
  spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), pluginBuildDescriptors.size.toLong()).useWithScope2 { span ->
    val counter = LongAdder()
    coroutineScope {
      for (plugin in pluginBuildDescriptors) {
        launch {
          if (pluginBuilder.buildPlugin(plugin = plugin)) {
            counter.add(1)
          }
        }
      }
    }
    span.setAttribute("reusedCount", counter.toLong())
  }
}

internal class PluginBuilder(private val outDir: Path,
                             private val pluginCacheRootDir: Path,
                             @JvmField val context: BuildContext) {
  private val dirtyPlugins = HashSet<PluginBuildDescriptor>()

  @Synchronized
  fun addDirtyPluginDir(item: PluginBuildDescriptor, reason: Any) {
    if (dirtyPlugins.add(item)) {
      Span.current().addEvent("${item.layout.directoryName} is changed" +
                              " (at least ${if (reason is Path) outDir.relativize(reason) else reason} is changed)")
    }
  }

  @Synchronized
  private fun getDirtyPluginsAndClear(): Collection<PluginBuildDescriptor> {
    if (dirtyPlugins.isEmpty()) {
      return emptyList()
    }

    val result = dirtyPlugins.toList()
    dirtyPlugins.clear()
    return result
  }

  suspend fun buildChanged(): String {
    val dirtyPlugins = getDirtyPluginsAndClear()
    if (dirtyPlugins.isEmpty()) {
      return "All plugins are up to date"
    }

    withContext(Dispatchers.IO) {
      for (plugin in dirtyPlugins) {
        try {
          launch {
            clearDirContent(plugin.dir)
            buildPlugin(plugin = plugin)
          }
        }
        catch (e: Throwable) {
          // put back (that's ok to add already processed plugins - doesn't matter, no need to complicate)
          for (dirtyPlugin in dirtyPlugins) {
            addDirtyPluginDir(dirtyPlugin, "<internal error>")
          }
          throw e
        }
      }
    }
    return "Plugins ${dirtyPlugins.joinToString { it.dir.fileName.toString() }} were updated"
  }

  internal suspend fun buildPlugin(plugin: PluginBuildDescriptor): Boolean {
    val mainModule = plugin.layout.mainModule

    val reason = withContext(Dispatchers.IO) {
      // check cache
      val reason = checkCache(plugin, outDir)
      if (reason == null) {
        return@withContext null
      }

      if (mainModule != "intellij.platform.builtInHelp") {
        checkOutputOfPluginModules(mainPluginModule = mainModule,
                                   jarToModules = plugin.layout.jarToModules,
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
                           jarToModule = plugin.layout.jarToModules,
                           context = context)
        withContext(Dispatchers.IO) {
          plugin.markAsBuilt(outDir)
        }

        false
      }
  }

  private fun checkCache(plugin: PluginBuildDescriptor, projectOutDir: Path): String? {
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
}

private fun isCacheUpToDate(plugin: PluginBuildDescriptor, projectOutDir: Path): String? {
  for (moduleName in plugin.moduleNames) {
    if (Files.notExists(projectOutDir.resolve(moduleName).resolve(UNMODIFIED_MARK_FILE_NAME))) {
      return "at least $moduleName is changed"
    }
  }
  return null
}
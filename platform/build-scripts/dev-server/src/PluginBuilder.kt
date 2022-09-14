// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("BlockingMethodInNonBlockingContext")

package org.jetbrains.intellij.build.devServer

import com.intellij.diagnostic.telemetry.useWithScope2
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
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

internal fun CoroutineScope.initialBuild(pluginBuildDescriptors: List<PluginBuildDescriptor>, pluginBuilder: PluginBuilder) {
  for (plugin in pluginBuildDescriptors) {
    launch {
      pluginBuilder.buildPlugin(plugin = plugin)
    }
  }
}

internal class PluginBuilder(private val outDir: Path,
                             private val pluginRootDir: Path,
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

  internal suspend fun buildPlugin(plugin: PluginBuildDescriptor) {
    val mainModule = plugin.layout.mainModule
    val moduleOutputPatcher = ModuleOutputPatcher()
    spanBuilder("build plugin")
      .setAttribute("mainModule", mainModule)
      .setAttribute("dir", plugin.layout.directoryName)
      .useWithScope2 { span ->
        val isCached = withContext(Dispatchers.IO) {
          // check cache
          if (checkCache(plugin, outDir, span)) {
            return@withContext true
          }

          if (mainModule != "intellij.platform.builtInHelp") {
            checkOutputOfPluginModules(mainPluginModule = mainModule,
                                       jarToModules = plugin.layout.jarToModules,
                                       moduleExcludes = plugin.layout.moduleExcludes,
                                       context = context)
          }
          false
        }

        if (isCached) {
          return@useWithScope2
        }

        layoutDistribution(layout = plugin.layout,
                           targetDirectory = plugin.dir,
                           copyFiles = true,
                           simplify = true,
                           moduleOutputPatcher = moduleOutputPatcher,
                           jarToModule = plugin.layout.jarToModules,
                           context = context)
        withContext(Dispatchers.IO) {
          plugin.markAsBuilt(outDir)
        }
      }
  }

  private fun checkCache(plugin: PluginBuildDescriptor, projectOutDir: Path, span: Span): Boolean {
    val dirName = plugin.layout.directoryName
    val jarFilename = "$dirName.jar"
    val cacheJarFile = pluginCacheRootDir.resolve(jarFilename)
    val asJarExists = Files.exists(cacheJarFile)
    val cacheDir = if (asJarExists) null else pluginCacheRootDir.resolve(dirName).takeIf { Files.exists(it) }
    if ((asJarExists || cacheDir != null) && isCacheUpToDate(plugin, projectOutDir, span)) {
      if (asJarExists) {
        Files.move(cacheJarFile, pluginRootDir.resolve(jarFilename))
      }
      else {
        Files.move(cacheDir!!, plugin.dir)
      }
      span.addEvent("reuse $dirName from cache")
      return true
    }
    return false
  }
}

private fun isCacheUpToDate(plugin: PluginBuildDescriptor, projectOutDir: Path, span: Span): Boolean {
  for (moduleName in plugin.moduleNames) {
    if (Files.notExists(projectOutDir.resolve(moduleName).resolve(UNMODIFIED_MARK_FILE_NAME))) {
      span.addEvent("previously built is not reused because at least $moduleName is changed")
      return false
    }
  }
  return true
}
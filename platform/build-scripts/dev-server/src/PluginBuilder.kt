// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer

import com.intellij.diagnostic.telemetry.useWithScope
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.TraceManager
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.checkOutputOfPluginModules
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

private val TOUCH_OPTIONS = EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE)

data class BuildItem(val dir: Path, val layout: PluginLayout) {
  val moduleNames = HashSet<String>()

  fun markAsBuilt(outDir: Path) {
    for (moduleName in moduleNames) {
      try {
        Files.newByteChannel(outDir.resolve(moduleName).resolve(UNMODIFIED_MARK_FILE_NAME), TOUCH_OPTIONS)
      }
      catch (ignore: NoSuchFileException) {
      }
    }
  }
}

class PluginBuilder(val buildContext: BuildContext, private val outDir: Path) {
  private val dirtyPlugins = HashSet<BuildItem>()

  suspend fun initialBuild(plugins: List<BuildItem>) {
    coroutineScope {
      for (plugin in plugins) {
        launch {
          buildPlugin(plugin, buildContext, outDir)
        }
      }
    }
  }

  @Synchronized
  fun addDirtyPluginDir(item: BuildItem, reason: Any) {
    if (dirtyPlugins.add(item)) {
      LOG.info("${item.dir.fileName} is changed (at least ${if (reason is Path) outDir.relativize(reason) else reason} is changed)")
    }
  }

  @Synchronized
  private fun getDirtyPluginsAndClear(): Collection<BuildItem> {
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

    coroutineScope {
      for (plugin in dirtyPlugins) {
        try {
          clearDirContent(plugin.dir)
          launch { buildPlugin(plugin, buildContext, outDir) }
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
}

private suspend fun buildPlugin(plugin: BuildItem, buildContext: BuildContext, projectOutDir: Path) {
  val mainModule = plugin.layout.mainModule
  if (skippedPluginModules.contains(mainModule)) {
    return
  }

  val moduleOutputPatcher = ModuleOutputPatcher()
  TraceManager.spanBuilder("build plugin")
    .setAttribute("mainModule", mainModule)
    .setAttribute("dir", plugin.dir.fileName.toString())
    .useWithScope {
      Span.current().addEvent("build ${mainModule}")

      if (mainModule != "intellij.platform.builtInHelp") {
        checkOutputOfPluginModules(mainModule, plugin.layout.moduleJars, plugin.layout.moduleExcludes, buildContext)
      }

      DistributionJARsBuilder.layout(plugin.layout,
                                     plugin.dir,
                                     true,
                                     moduleOutputPatcher,
                                     plugin.layout.moduleJars,
                                     buildContext)
      plugin.markAsBuilt(projectOutDir)
    }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.TracerManager
import org.jetbrains.intellij.build.tasks.useWithScope
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ForkJoinTask

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

  fun initialBuild(plugins: List<BuildItem>) {
    val tasks = mutableListOf<ForkJoinTask<*>>()
    for (plugin in plugins) {
      tasks.add(ForkJoinTask.adapt {
        buildPlugin(plugin, buildContext, outDir)
      })
    }
    ForkJoinTask.invokeAll(tasks)
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

  fun buildChanged(): String {
    val dirtyPlugins = getDirtyPluginsAndClear()
    if (dirtyPlugins.isEmpty()) {
      return "All plugins are up to date"
    }

    for (plugin in dirtyPlugins) {
      try {
        clearDirContent(plugin.dir)
        buildPlugin(plugin, buildContext, outDir)
      }
      catch (e: Throwable) {
        // put back (that's ok to add already processed plugins - doesn't matter, no need to complicate)
        for (dirtyPlugin in dirtyPlugins) {
          addDirtyPluginDir(dirtyPlugin, "<internal error>")
        }
        throw e
      }
    }
    return "Plugins ${dirtyPlugins.joinToString { it.dir.fileName.toString() }} were updated"
  }
}

private fun buildPlugin(plugin: BuildItem, buildContext: BuildContext, projectOutDir: Path) {
  val mainModule = plugin.layout.mainModule
  if (skippedPluginModules.contains(mainModule)) {
    return
  }

  val moduleOutputPatcher = ModuleOutputPatcher()
  TracerManager.spanBuilder("build plugin")
    .setAttribute("mainModule", mainModule)
    .setAttribute("dir", plugin.dir.fileName.toString())
    .startSpan()
    .useWithScope {
      Span.current().addEvent("build ${mainModule}")

      if (mainModule != "intellij.platform.builtInHelp") {
        DistributionJARsBuilder.checkOutputOfPluginModules(mainModule, plugin.layout.moduleJars, plugin.layout.moduleExcludes, buildContext)
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
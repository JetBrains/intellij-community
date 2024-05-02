// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.jetbrains.intellij.build.dev

import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import java.util.concurrent.atomic.LongAdder

internal suspend fun buildPlugins(
  pluginBuildDescriptors: List<PluginBuildDescriptor>,
  platformLayout: PlatformLayout,
  context: BuildContext,
  jarsWithSearchableOptions: SearchableOptionSetDescriptor?,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), pluginBuildDescriptors.size.toLong()).useWithScope { span ->
    val counter = LongAdder()
    val pluginEntries = coroutineScope {
      pluginBuildDescriptors.map { plugin ->
        async {
          plugin to buildPluginIfNotCached(plugin = plugin, platformLayout = platformLayout, context = context, jarsWithSearchableOptions = jarsWithSearchableOptions)
        }
      }
    }.map { it.getCompleted() }
    span.setAttribute("reusedCount", counter.toLong())
    pluginEntries
  }
}

internal suspend fun buildPluginIfNotCached(
  plugin: PluginBuildDescriptor,
  platformLayout: PlatformLayout,
  context: BuildContext,
  jarsWithSearchableOptions: SearchableOptionSetDescriptor?,
): List<DistributionFileEntry> {
  val mainModule = plugin.layout.mainModule

  withContext(Dispatchers.IO) {
    // check cache
    if (plugin.layout.mainModule == "intellij.rider.plugins.clion.radler" && hasResourcePaths(plugin.layout)) {
      // copy custom resources
      spanBuilder("build plugin")
        .setAttribute("mainModule", mainModule)
        .setAttribute("dir", plugin.layout.directoryName)
        .setAttribute("reason", "copy custom resources")
        .useWithScope {
          layoutResourcePaths(layout = plugin.layout, context = context, targetDirectory = plugin.dir, overwrite = true)
        }
    }
  }

  return buildPlugin(plugin = plugin, platformLayout = platformLayout, context = context, copyFiles = true, jarsWithSearchableOptions = jarsWithSearchableOptions)
}

private suspend fun buildPlugin(
  plugin: PluginBuildDescriptor,
  platformLayout: PlatformLayout,
  context: BuildContext,
  copyFiles: Boolean,
  jarsWithSearchableOptions: SearchableOptionSetDescriptor?,
): List<DistributionFileEntry> {
  val moduleOutputPatcher = ModuleOutputPatcher()
  return spanBuilder("build plugin")
    .setAttribute("mainModule", plugin.layout.mainModule)
    .setAttribute("dir", plugin.layout.directoryName)
    .useWithScope {
      val (pluginEntries, _) = layoutDistribution(
        layout = plugin.layout,
        platformLayout = platformLayout, targetDirectory = plugin.dir,

        moduleOutputPatcher = moduleOutputPatcher,
        includedModules = plugin.layout.includedModules,
        copyFiles = copyFiles,
        jarsWithSearchableOptions = jarsWithSearchableOptions,
        context = context,
      )
      pluginEntries
    }
}
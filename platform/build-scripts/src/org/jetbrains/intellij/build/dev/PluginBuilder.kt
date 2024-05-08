// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.jetbrains.intellij.build.dev

import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry

internal suspend fun buildPlugins(
  pluginBuildDescriptors: List<PluginBuildDescriptor>,
  platformLayout: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), pluginBuildDescriptors.size.toLong()).useWithScope {
    coroutineScope {
      pluginBuildDescriptors.map { plugin ->
        async {
          plugin to buildPlugin(plugin = plugin, platformLayout = platformLayout, searchableOptionSet = searchableOptionSet, context = context)
        }
      }
    }.map { it.getCompleted() }
  }
}

private suspend fun buildPlugin(
  plugin: PluginBuildDescriptor,
  platformLayout: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
): List<DistributionFileEntry> {
  if (plugin.layout.mainModule == "intellij.rider.plugins.clion.radler" && hasResourcePaths(plugin.layout)) {
    // copy custom resources
    spanBuilder("build plugin")
      .setAttribute("mainModule", plugin.layout.mainModule)
      .setAttribute("dir", plugin.layout.directoryName)
      .setAttribute("reason", "copy custom resources")
      .useWithScope(Dispatchers.IO) {
        layoutResourcePaths(layout = plugin.layout, context = context, targetDirectory = plugin.dir, overwrite = true)
      }
  }

  return spanBuilder("build plugin")
    .setAttribute("mainModule", plugin.layout.mainModule)
    .setAttribute("dir", plugin.layout.directoryName)
    .useWithScope {
      val (pluginEntries, _) = layoutDistribution(
        layout = plugin.layout,
        platformLayout = platformLayout,
        targetDirectory = plugin.dir,
        moduleOutputPatcher = ModuleOutputPatcher(),
        includedModules = plugin.layout.includedModules,
        copyFiles = true,
        searchableOptionSet = searchableOptionSet,
        context = context,
      )
      pluginEntries
    }
}
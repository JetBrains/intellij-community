// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.dev

import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import java.nio.file.Path

internal suspend fun buildPlugins(
  plugins: List<PluginLayout>,
  platformLayout: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
  pluginRootDir: Path,
  buildPlatformJob: Job,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), plugins.size.toLong()).useWithScope {
    launch {
      val plugin = plugins.firstOrNull { it.mainModule == "intellij.rider.plugins.clion.radler" && hasResourcePaths(it) } ?: return@launch
      // copy custom resources
      spanBuilder("build plugin")
        .setAttribute("mainModule", plugin.mainModule)
        .setAttribute("dir", plugin.directoryName)
        .setAttribute("reason", "copy custom resources")
        .useWithScope(Dispatchers.IO) {
          layoutResourcePaths(layout = plugin, context = context, targetDirectory = pluginRootDir.resolve(plugin.directoryName), overwrite = true)
        }
    }

    buildPlugins(
      moduleOutputPatcher = ModuleOutputPatcher(),
      plugins = plugins,
      targetDir = pluginRootDir,
      state = DistributionBuilderState(platform = platformLayout, pluginsToPublish = emptySet(), context = context),
      context = context,
      buildPlatformJob = buildPlatformJob,
      searchableOptionSet = searchableOptionSet,
    )
  }
}
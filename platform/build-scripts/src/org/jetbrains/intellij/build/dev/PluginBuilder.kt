// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.dev

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Job
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.telemetry.useWithScope
import java.nio.file.Path

internal suspend fun buildPlugins(
  plugins: List<PluginLayout>,
  platformLayout: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
  pluginRootDir: Path,
  buildPlatformJob: Job,
  moduleOutputPatcher: ModuleOutputPatcher,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), plugins.size.toLong()).useWithScope {
    buildPlugins(
      moduleOutputPatcher = moduleOutputPatcher,
      plugins = plugins,
      targetDir = pluginRootDir,
      state = DistributionBuilderState(platform = platformLayout, pluginsToPublish = emptySet(), context = context),
      context = context,
      buildPlatformJob = buildPlatformJob,
      searchableOptionSet = searchableOptionSet,
      os = null,
    )
  }
}
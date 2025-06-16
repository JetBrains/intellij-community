// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext

suspend fun createDistributionBuilderState(pluginsToPublish: Set<PluginLayout>, context: BuildContext): DistributionBuilderState {
  val pluginsToPublishEffective = pluginsToPublish.toMutableSet()
  filterPluginsToPublish(pluginsToPublishEffective, context)
  val platform = createPlatformLayout(context)
  return DistributionBuilderState(platform, pluginsToPublishEffective, context)
}

suspend fun createDistributionBuilderState(context: BuildContext): DistributionBuilderState {
  val platform = createPlatformLayout(context)
  return DistributionBuilderState(platform, pluginsToPublish = emptySet(), context)
}

class DistributionBuilderState internal constructor(
  @JvmField val platform: PlatformLayout,
  @JvmField val pluginsToPublish: Set<PluginLayout>,
  context: BuildContext,
) {
  init {
    val releaseDate = context.applicationInfo.majorReleaseDate
    require(!releaseDate.startsWith("__")) {
      "Unresolved release-date: $releaseDate"
    }
  }

  val platformModules: Sequence<String>
    get() = platform.includedModules.asSequence().map { it.moduleName }.distinct() + getToolModules().asSequence()
}

internal fun filterPluginsToPublish(plugins: MutableSet<PluginLayout>, context: BuildContext) {
  if (plugins.isEmpty()) {
    return
  }

  val toInclude = context.options.nonBundledPluginDirectoriesToInclude
  if (toInclude.isEmpty()) {
    return
  }

  if (toInclude.size == 1 && toInclude.contains("none")) {
    plugins.clear()
    return
  }

  plugins.removeIf { !toInclude.contains(it.directoryName) }
}

/**
 * @return module names which are required to run the necessary tools from build scripts
 */
internal fun getToolModules(): List<String> = listOf("intellij.java.rt", "intellij.platform.starter", "intellij.tools.updater")

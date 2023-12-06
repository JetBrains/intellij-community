// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext

suspend fun createDistributionBuilderState(pluginsToPublish: Set<PluginLayout>, context: BuildContext): DistributionBuilderState {
  val pluginsToPublishEffective = pluginsToPublish.toMutableSet()
  filterPluginsToPublish(pluginsToPublishEffective, context)
  val platform = createPlatformLayout(pluginsToPublishEffective, context)
  return DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublishEffective, context = context)
}

suspend fun createDistributionBuilderState(context: BuildContext): DistributionBuilderState {
  val platform = createPlatformLayout(pluginsToPublish = emptySet(), context = context)
  return DistributionBuilderState(platform = platform, pluginsToPublish = emptySet(), context = context)
}

class DistributionBuilderState(@JvmField val platform: PlatformLayout,
                               @JvmField val pluginsToPublish: Set<PluginLayout>,
                               context: BuildContext) {
  init {
    val releaseDate = context.applicationInfo.majorReleaseDate
    require(!releaseDate.startsWith("__")) {
      "Unresolved release-date: $releaseDate"
    }
  }

  val platformModules: Collection<String>
    get() = (platform.includedModules.asSequence().map { it.moduleName }.distinct() + getToolModules().asSequence()).toList()

  fun getModulesForPluginsToPublish(): Set<String> {
    return getModulesForPluginsToPublish(platform, pluginsToPublish)
  }
}

internal fun getModulesForPluginsToPublish(platform: PlatformLayout, pluginsToPublish: Set<PluginLayout>): Set<String> {
  val result = LinkedHashSet<String>()
  result.addAll((platform.includedModules.asSequence().map { it.moduleName }.distinct() + getToolModules().asSequence()))
  pluginsToPublish.flatMapTo(result) { layout -> layout.includedModules.asSequence().map { it.moduleName } }
  return result
}

internal fun filterPluginsToPublish(plugins: MutableSet<PluginLayout>, context: BuildContext) {
  if (plugins.isEmpty()) {
    return
  }

  // Kotlin Multiplatform Mobile plugin is excluded since:
  // * is compatible with Android Studio only;
  // * has release cycle of its
  // * shadows IntelliJ utility modules included via Kotlin Compiler;
  // * breaks searchable options index and jar order generation steps.
  plugins.removeIf { it.mainModule == "kotlin-ultimate.kmm-plugin" }
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
 * @return module names which are required to run necessary tools from build scripts
 */
internal fun getToolModules(): List<String> {
  return java.util.List.of("intellij.java.rt", "intellij.platform.main",
                           /*required to build searchable options index*/ "intellij.platform.updater")
}
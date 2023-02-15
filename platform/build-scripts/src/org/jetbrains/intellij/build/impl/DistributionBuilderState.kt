// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModuleReference

suspend fun createDistributionBuilderState(pluginsToPublish: Set<PluginLayout>, context: BuildContext): DistributionBuilderState {
  val pluginsToPublishFiltered = filterPluginsToPublish(pluginsToPublish, context)
  val platform = createPlatformLayout(pluginsToPublishFiltered, context)
  return DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublishFiltered, context = context)
}

class DistributionBuilderState(@JvmField val platform: PlatformLayout,
                               @JvmField val pluginsToPublish: Set<PluginLayout>,
                               private val context: BuildContext) {
  init {
    val releaseDate = context.applicationInfo.majorReleaseDate
    require(!releaseDate.startsWith("__")) {
      "Unresolved release-date: $releaseDate"
    }
  }

  val platformModules: Collection<String>
    get() = (platform.includedModules.asSequence().map { it.moduleName }.distinct() + getToolModules().asSequence()).toList()

  fun getModulesForPluginsToPublish(): Set<String> {
    val result = LinkedHashSet<String>()
    result.addAll(platformModules)
    pluginsToPublish.flatMapTo(result) { layout -> layout.includedModules.asSequence().map { it.moduleName } }
    return result
  }

  fun getIncludedProjectArtifacts(): Set<String> {
    val result = LinkedHashSet<String>()
    result.addAll(platform.includedArtifacts.keys)

    getPluginLayoutsByJpsModuleNames(modules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish,
                                                                       productProperties = context.productProperties),
                                     productLayout = context.productProperties.productLayout)
      .flatMapTo(result) { it.includedArtifacts.keys }
    return result
  }
}

private fun filterPluginsToPublish(plugins: Set<PluginLayout>, context: BuildContext): Set<PluginLayout> {
  if (plugins.isEmpty()) {
    return plugins
  }

  val result = LinkedHashSet(plugins)
  // Kotlin Multiplatform Mobile plugin is excluded since:
  // * is compatible with Android Studio only;
  // * has release cycle of its
  // * shadows IntelliJ utility modules included via Kotlin Compiler;
  // * breaks searchable options index and jar order generation steps.
  result.removeIf { it.mainModule == "kotlin-ultimate.kmm-plugin" }
  if (result.isEmpty()) {
    return emptySet()
  }

  val toInclude = context.options.nonBundledPluginDirectoriesToInclude
  if (toInclude.isEmpty()) {
    return result
  }

  if (toInclude.size == 1 && toInclude.contains("none")) {
    return emptySet()
  }

  result.removeIf { !toInclude.contains(it.directoryName) }
  return result
}

/**
 * @return module names which are required to run necessary tools from build scripts
 */
internal fun getToolModules(): List<String> {
  return java.util.List.of("intellij.java.rt", "intellij.platform.main",
                           /*required to build searchable options index*/ "intellij.platform.updater")
}

internal fun isProjectLibraryUsedByPlugin(library: JpsLibrary, plugin: BaseLayout): Boolean {
  return library.createReference().parentReference !is JpsModuleReference && !plugin.hasLibrary(library.name)
}

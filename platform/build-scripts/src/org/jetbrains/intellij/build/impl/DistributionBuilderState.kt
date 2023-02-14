// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.*

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

suspend fun createPlatformLayout(pluginsToPublish: Set<PluginLayout>, context: BuildContext): PlatformLayout {
  val productLayout = context.productProperties.productLayout
  val enabledPluginModules = getEnabledPluginModules(pluginsToPublish, context.productProperties)
  val projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules, context)
  return createPlatformLayout(productLayout = productLayout,
                              hasPlatformCoverage = hasPlatformCoverage(productLayout, enabledPluginModules, context),
                              additionalProjectLevelLibraries = projectLibrariesUsedByPlugins,
                              context = context)
}

private fun getEnabledPluginModules(pluginsToPublish: Set<PluginLayout>, productProperties: ProductProperties): Set<String> {
  val result = LinkedHashSet<String>()
  result.addAll(productProperties.productLayout.bundledPluginModules)
  pluginsToPublish.mapTo(result) { it.mainModule }
  return result
}

private fun computeProjectLibsUsedByPlugins(enabledPluginModules: Set<String>, context: BuildContext): SortedSet<ProjectLibraryData> {
  val result = ObjectLinkedOpenHashSet<ProjectLibraryData>()

  for (plugin in getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)) {
    for (moduleName in plugin.includedModules.asSequence().map { it.moduleName }.distinct()) {
      val dependencies = JpsJavaExtensionService.dependencies(context.findRequiredModule(moduleName))
      dependencies.includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).processLibraries(com.intellij.util.Consumer {library ->
        if (!isProjectLibraryUsedByPlugin(library = library, plugin = plugin)) {
          return@Consumer
        }

        val name = library.name
        val packMode = PLATFORM_CUSTOM_PACK_MODE.getOrDefault(name, LibraryPackMode.MERGED)
        result.addOrGet(ProjectLibraryData(name, packMode))
          .dependentModules
          .computeIfAbsent(plugin.directoryName) { ArrayList<String>() }
          .add(moduleName)
      })
    }
  }
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

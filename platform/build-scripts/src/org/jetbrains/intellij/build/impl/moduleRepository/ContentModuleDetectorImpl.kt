// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleTestOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

/**
 * Provide information about [JpsModule] registered as content modules using data [org.jetbrains.intellij.build.impl.DescriptorCacheContainer].
 */
internal class ContentModuleDetectorImpl(
  platformLayout: PlatformLayout,
  corePluginDescriptorModuleName: String,
  platformEntries: List<DistributionFileEntry>,
  bundledPlugins: List<PluginBuildDescriptor>,
  embeddedFrontendDescriptorModuleName: String?,
  project: JpsProject,
) : ContentModuleDetector {
  private data class ContentModuleInPlugin(val pluginId: String, val moduleName: String)
  private val contentModules = mutableMapOf<String, ContentModuleRegistrationDataForHeader>()
  private val loadingRulesForContentModules = mutableMapOf<ContentModuleInPlugin, RuntimeModuleLoadingRule>()
  private val requiredIfAvailableAttributeForContentModules = mutableMapOf<ContentModuleInPlugin, RuntimeModuleId?>()
  val pluginHeaders: List<RuntimePluginHeader>

  init {
    val pluginDescriptorsData = fetchPluginDescriptorsData(platformLayout, corePluginDescriptorModuleName, embeddedFrontendDescriptorModuleName, bundledPlugins)

    val pluginDescriptorModuleNameToId = HashMap<String, String>()
    for (pluginDescriptorData in pluginDescriptorsData) {
      pluginDescriptorData.contentModules.forEach { contentModule ->
        contentModules[contentModule.name] = contentModule
        val key = ContentModuleInPlugin(pluginDescriptorData.pluginId, contentModule.name)
        loadingRulesForContentModules[key] = contentModule.loadingRule
        requiredIfAvailableAttributeForContentModules[key] = contentModule.requiredIfAvailable
      }
      pluginDescriptorModuleNameToId[pluginDescriptorData.pluginDescriptorJpsModuleName] = pluginDescriptorData.pluginId
    }
    val corePluginModuleId = createModuleId(corePluginDescriptorModuleName, project)
    val corePluginHeader = createCorePluginHeader(platformEntries, corePluginModuleId, project)
    pluginHeaders = listOf(corePluginHeader) + bundledPlugins.map { plugin ->
      createPluginHeader(plugin, pluginId = pluginDescriptorModuleNameToId.getValue(plugin.layout.mainModule), project = project)
    }
  }

  override fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationDataForHeader? {
    return contentModules[jpsModule.name]
  }

  override fun findContentModuleDataForTests(jpsModule: JpsModule): ContentModuleRegistrationDataForHeader? {
    val data = contentModules["${jpsModule.name}._test"]
    if (data != null) return data
    if (hasTestSourcesAndNoProductionSources(jpsModule)) {
      //some modules (e.g. intellij.rider.test.cases.common) don't use `._test` suffix
      return contentModules[jpsModule.name]
    }
    return null
  }

  private fun createPluginHeader(plugin: PluginBuildDescriptor, pluginId: String, project: JpsProject): RuntimePluginHeader {
    val pluginDescriptorModuleId = createModuleId(plugin.layout.mainModule, project)
    val includedModules = convertDistributionEntriesToIncludedModules(plugin.distribution, project, pluginId)
    return RuntimePluginHeaderImpl(pluginId, pluginDescriptorModuleId, includedModules)
  }

  private fun convertDistributionEntriesToIncludedModules(
    entries: Collection<DistributionFileEntry>,
    project: JpsProject,
    pluginId: String,
  ): List<IncludedRuntimeModule> {
    val includedModules = entries.mapNotNull { entry ->
      val relativeOutputPath = entry.relativeOutputFile ?: ""
      when (entry) {
        is ModuleOutputEntry -> {
          val moduleName = entry.owner.moduleName
          val key = ContentModuleInPlugin(pluginId, moduleName)
          val loadingRule = loadingRulesForContentModules[key] ?: RuntimeModuleLoadingRule.EMBEDDED
          val requiredIfAvailableId = requiredIfAvailableAttributeForContentModules[key]
          IncludedRuntimeModuleImpl(createModuleId(moduleName, project), loadingRule, requiredIfAvailableId)
        }
        is ProjectLibraryEntry -> {
          IncludedRuntimeModuleImpl(RuntimeModuleId.projectLibrary(entry.data.libraryName), RuntimeModuleLoadingRule.EMBEDDED, null)
        }
        is ModuleTestOutputEntry ->
          IncludedRuntimeModuleImpl(RuntimeModuleId.moduleTests(entry.moduleName), RuntimeModuleLoadingRule.EMBEDDED, null)
        is ModuleLibraryFileEntry -> null // module-level libraries are included in the runtime descriptor for corresponding module
        is CustomAssetEntry -> null
      }?.takeIf { shouldIncludeInPluginHeader(it.moduleId, relativeOutputPath) }
    }
    return includedModules
  }

  private fun createCorePluginHeader(platformEntries: List<DistributionFileEntry>, pluginDescriptorModuleId: RuntimeModuleId, project: JpsProject): RuntimePluginHeader {
    val pluginId = "com.intellij"
    val includedModules = convertDistributionEntriesToIncludedModules(platformEntries, project, pluginId)
    return RuntimePluginHeaderImpl(pluginId, pluginDescriptorModuleId, includedModules)
  }

  /**
   * Returns `true` if the module [moduleId] should be included in the plugin header: if its JAR is located directly in `lib/` directory, or the JAR is in `lib/modules/` directory,
   * and it's a real content module.
   */
  private fun shouldIncludeInPluginHeader(moduleId: RuntimeModuleId, relativeOutputPath: String): Boolean {
    if (!relativeOutputPath.contains('/')) return true
    if (!relativeOutputPath.startsWith("modules/") || relativeOutputPath.removePrefix("modules/").contains('/')) return false
    val namespace = moduleId.namespace
    return namespace != RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE && namespace != RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE
           && namespace != RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE
  }

  private fun createModuleId(moduleName: String, project: JpsProject): RuntimeModuleId {
    val pluginDescriptorModuleData = contentModules[moduleName]
    val pluginDescriptorModuleId = if (pluginDescriptorModuleData != null) {
      RuntimeModuleId.contentModule(pluginDescriptorModuleData.name, pluginDescriptorModuleData.namespace)
    }
    else {
      val module = project.findModuleByName(moduleName)
      if (module != null && hasTestSourcesAndNoProductionSources(module)) {
        return RuntimeModuleId.moduleTests(moduleName)
      }
      else {
        RuntimeModuleId.legacyJpsModule(moduleName)
      }
    }
    return pluginDescriptorModuleId
  }
}

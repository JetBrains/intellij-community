// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleTestOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.jps.model.JpsProject

internal fun generateRuntimePluginHeader(
  pluginDescriptorData: PluginDescriptorDataForHeader,
  distributionEntries: Collection<DistributionFileEntry>,
  sharedContentModuleData: Map<String, ContentModuleRegistrationDataForHeader>,
  project: JpsProject,
): RuntimePluginHeader {
  val pluginDescriptorModuleId = createModuleId(pluginDescriptorData.pluginDescriptorJpsModuleName, project, sharedContentModuleData)
  val includedModules = convertDistributionEntriesToIncludedModules(distributionEntries, pluginDescriptorData, project, sharedContentModuleData)
  return RuntimePluginHeaderImpl(pluginDescriptorData.pluginId, pluginDescriptorModuleId, includedModules)
}

private fun convertDistributionEntriesToIncludedModules(
  entries: Collection<DistributionFileEntry>,
  pluginDescriptorData: PluginDescriptorDataForHeader,
  project: JpsProject,
  sharedContentModuleData: Map<String, ContentModuleRegistrationDataForHeader>,
): List<IncludedRuntimeModule> {
  val includedModules = entries.mapNotNull { entry ->
    val relativeOutputPath = entry.relativeOutputFile ?: ""
    when (entry) {
      is ModuleOutputEntry -> {
        val moduleName = entry.owner.moduleName
        val contentModuleData = pluginDescriptorData.contentModules[moduleName]
        val loadingRule = contentModuleData?.loadingRule ?: RuntimeModuleLoadingRule.EMBEDDED
        val requiredIfAvailableId = contentModuleData?.requiredIfAvailable
        IncludedRuntimeModuleImpl(createModuleId(moduleName, project, sharedContentModuleData), loadingRule, requiredIfAvailableId)
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

private fun createModuleId(moduleName: String, project: JpsProject, contentModules: Map<String, ContentModuleRegistrationDataForHeader>): RuntimeModuleId {
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
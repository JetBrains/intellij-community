// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import com.intellij.util.containers.MultiMap
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.jps.model.JpsNamedElement
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Contains information about modules and libraries included in the plugin.
 */
internal class RuntimePluginHeaderData(
  val header: RuntimePluginHeader,
  /** Modules included in the plugin distribution but aren't loaded by the IDE process */
  val additionalModules: List<RuntimeModuleId>,
  val visibilityOfModules: Map<RuntimeModuleId, RuntimeModuleVisibility>,
  val includedElementToId: Map<JpsNamedElement, RuntimeModuleId>,
  val moduleIdToJpsElement: Map<RuntimeModuleId, JpsNamedElement>,
  /** Mapping from ID of a module to the list of paths from its classpath (relative to the root of the installation directory) */
  val classpathEntries: MultiMap<RuntimeModuleId, String>,
) {
  override fun toString(): String {
    return "RuntimePluginHeaderData{pluginId=${header.pluginId}, pluginDescriptorModuleId=${header.pluginDescriptorModuleId}}"
  }
}

/**
 * Collects information about modules and libraries included in the plugins using data from their plugin descriptors ([pluginDescriptorsData]) and files included in the plugin
 * distribution ([pluginConfigurationModuleToDistributionEntries]).
 */
internal fun generateRuntimePluginHeaders(
  pluginDescriptorsData: List<PluginDescriptorDataForHeader>,
  pluginConfigurationModuleToDistributionEntries: Map<String, Collection<DistributionFileEntry>>,
  repositoryPathRelativizer: (Path) -> Path?,
  project: JpsProject,
): List<RuntimePluginHeaderData> {
  val jpsElementToPlugins = pluginDescriptorsData
    .filterNot { it.additionalFrontendOnlyPlugin }
    .flatMap { pluginDescriptorsData ->
      val distributionEntries = pluginConfigurationModuleToDistributionEntries.getValue(pluginDescriptorsData.pluginDescriptorJpsModuleName)
      collectJpsElementsIncludedInPlugin(distributionEntries, project).map { jpsElement -> jpsElement to pluginDescriptorsData }
    }
    .groupBy({ it.first }, { it.second })
  val elementsIncludedInMultiplePlugins = jpsElementToPlugins.asSequence().filter { it.value.size > 1 }.mapTo(HashSet()) { it.key }

  val (regularPluginDescriptorData, frontendOnlyPluginDescriptorData) = pluginDescriptorsData.partition { !it.additionalFrontendOnlyPlugin }
  val regularPluginHeaderData = regularPluginDescriptorData.map { pluginDescriptorsData ->
    val distributionEntries = pluginConfigurationModuleToDistributionEntries.getValue(pluginDescriptorsData.pluginDescriptorJpsModuleName)
    generateRuntimePluginHeader(pluginDescriptorsData, distributionEntries, project, repositoryPathRelativizer, elementsIncludedInMultiplePlugins, existingIdsToReuse = emptyMap())
  }

  /* generate headers for additional frontend-only plugins; these plugins don't register their own modules, they reuse modules registered by regular plugins instead;
     this part can be removed as soon as we get rid of such plugins (IJPL-220139) */
  val existingIdsToReuse = regularPluginHeaderData.fold(emptyMap<JpsNamedElement, RuntimeModuleId>()) { acc, data -> acc + data.includedElementToId }
  val frontendOnlyPluginHeaderData = frontendOnlyPluginDescriptorData.map { pluginDescriptorsData ->
    val distributionEntries = pluginConfigurationModuleToDistributionEntries.getValue(pluginDescriptorsData.pluginDescriptorJpsModuleName)
    generateRuntimePluginHeader(pluginDescriptorsData, distributionEntries, project, repositoryPathRelativizer, elementsIncludedInMultiplePlugins, existingIdsToReuse)
  }

  return regularPluginHeaderData + frontendOnlyPluginHeaderData
}

private fun collectJpsElementsIncludedInPlugin(distributionEntries: Collection<DistributionFileEntry>, project: JpsProject): Set<JpsNamedElement> {
  return distributionEntries.mapNotNullTo(LinkedHashSet()) { entry ->
    when (entry) {
      is ModuleOutputEntry -> project.findModuleByName(entry.owner.moduleName)
      is ProjectLibraryEntry -> project.libraryCollection.findLibrary(entry.data.libraryName)
      else -> null
    }
  }
}

private fun generateRuntimePluginHeader(
  pluginDescriptorData: PluginDescriptorDataForHeader,
  distributionEntries: Collection<DistributionFileEntry>,
  project: JpsProject,
  repositoryPathRelativizer: (Path) -> Path?,
  elementsIncludedInMultiplePlugins: Set<JpsNamedElement>,
  existingIdsToReuse: Map<JpsNamedElement, RuntimeModuleId>,
): RuntimePluginHeaderData {
  val includedModules = ArrayList<IncludedRuntimeModule>()
  val additionalModules = ArrayList<RuntimeModuleId>()
  val visibilityOfModules = HashMap<RuntimeModuleId, RuntimeModuleVisibility>()
  val includedElementToId = HashMap<JpsNamedElement, RuntimeModuleId>()
  val moduleIdToJpsElement = HashMap<RuntimeModuleId, JpsNamedElement>()
  val classpathEntries = MultiMap.createOrderedSet<RuntimeModuleId, String>()
  val moduleLibraryPaths = MultiMap.createOrderedSet<String, String>()
  distributionEntries.forEach { entry ->
    val pathRelativeToDistributionDir = repositoryPathRelativizer(entry.path)?.invariantSeparatorsPathString
    val includedModule =
      when (entry) {
        is ModuleOutputEntry -> {
          val moduleName = entry.owner.moduleName
          val contentModuleData = pluginDescriptorData.contentModules[moduleName]
          val loadingRule = contentModuleData?.loadingRule ?: RuntimeModuleLoadingRule.EMBEDDED
          val requiredIfAvailableId = contentModuleData?.requiredIfAvailable
          val jpsModuleName = moduleName.removeSuffix("._test") //todo remove this after IJPL-242652 is fixed
          val jpsModule = project.findModuleByName(jpsModuleName) ?: error("Cannot find module by name: $jpsModuleName")
          val moduleId = createIdForModule(jpsModule, pluginDescriptorData, elementsIncludedInMultiplePlugins, existingIdsToReuse)
          if (moduleId !in moduleIdToJpsElement) {
            moduleIdToJpsElement[moduleId] = jpsModule
            visibilityOfModules[moduleId] = contentModuleData?.visibility ?: RuntimeModuleVisibility.PUBLIC
            includedElementToId[jpsModule] = moduleId
            if (pathRelativeToDistributionDir != null) {
              classpathEntries.putValue(moduleId, pathRelativeToDistributionDir)
            }
            IncludedRuntimeModuleImpl(moduleId, loadingRule, requiredIfAvailableId)
          }
          else {
            //todo fix cases when a module is included multiple times in a plugin instead (IJPL-246384)
            null
          }
        }
        is ProjectLibraryEntry -> {
          val libraryName = entry.data.libraryName
          val jpsLibrary = project.libraryCollection.findLibrary(libraryName) ?: error("Cannot find library by name: $libraryName")
          val moduleId = createIdForJpsLibrary(jpsLibrary, pluginDescriptorData.pluginId, elementsIncludedInMultiplePlugins)
          if (pathRelativeToDistributionDir != null) {
            classpathEntries.putValue(moduleId, pathRelativeToDistributionDir)
          }
          if (moduleId !in moduleIdToJpsElement) {
            moduleIdToJpsElement[moduleId] = jpsLibrary
            includedElementToId[jpsLibrary] = moduleId
            IncludedRuntimeModuleImpl(moduleId, RuntimeModuleLoadingRule.EMBEDDED, null)
          }
          else {
            //if a library consists of several files, there will be multiple entries, so we need to register it only once
            null
          }
        }
        is ModuleLibraryFileEntry -> {
          if (pathRelativeToDistributionDir != null) {
            val jpsModuleName = entry.moduleName.removeSuffix("._test") //todo remove this after IJPL-242652 is fixed
            moduleLibraryPaths.putValue(jpsModuleName, pathRelativeToDistributionDir)
          }
          //no separate runtime modules are generated for module-level libraries; their paths are included in the descriptor corresponding to the containing module instead
          null
        }
        is CustomAssetEntry -> null
      }
    if (includedModule != null) {
      val outputPathRelativeToPluginLibDir = entry.relativeOutputFile ?: ""
      if (shouldIncludeInPluginHeader(includedModule.moduleId, outputPathRelativeToPluginLibDir)) {
        includedModules.add(includedModule)
      }
      else {
        //when on-demand loading mode is supported (IJPL-242789), we can get rid of `additionalModules` and register such modules as regular modules with on-demand loading rule
        additionalModules.add(includedModule.moduleId)
      }
    }
  }

  moduleLibraryPaths.entrySet().forEach { (moduleName, paths) ->
    val module = project.findModuleByName(moduleName) ?: error("Cannot find module $moduleName")
    val moduleId = includedElementToId[module] ?: RuntimeModuleId.raw(moduleName, "${pluginDescriptorData.pluginId}_${RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX}")
    classpathEntries.putValues(moduleId, paths)
  }

  val pluginDescriptorModule = project.findModuleByName(pluginDescriptorData.pluginDescriptorJpsModuleName) ?: error("Cannot find module ${pluginDescriptorData.pluginDescriptorJpsModuleName}")
  val pluginDescriptorModuleId = createIdForModule(pluginDescriptorModule, pluginDescriptorData, elementsIncludedInMultiplePlugins, existingIdsToReuse)
  val header = RuntimePluginHeaderImpl(pluginDescriptorData.pluginId, pluginDescriptorModuleId, includedModules)
  return RuntimePluginHeaderData(header, additionalModules, visibilityOfModules, includedElementToId, moduleIdToJpsElement, classpathEntries)
}

/**
 * Returns `true` if the module [moduleId] should be included in the plugin header: if its JAR is located directly in `lib/` directory, or the JAR is in `lib/modules/` directory,
 * and it's a real content module.
 */
private fun shouldIncludeInPluginHeader(moduleId: RuntimeModuleId, outputPathRelativeToPluginLibDir: String): Boolean {
  if (!outputPathRelativeToPluginLibDir.contains('/')) return true
  if (!outputPathRelativeToPluginLibDir.startsWith("modules/") || outputPathRelativeToPluginLibDir.removePrefix("modules/").contains('/')) return false
  val namespace = moduleId.namespace
  return !namespace.endsWith(RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE_SUFFIX) && !namespace.endsWith(RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE_SUFFIX)
         && !namespace.endsWith(RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX)
}

private fun createIdForModule(
  module: JpsModule,
  pluginDescriptorData: PluginDescriptorDataForHeader,
  elementsIncludedInMultiplePlugins: Set<JpsNamedElement>,
  existingIdsToReuse: Map<JpsNamedElement, RuntimeModuleId>
): RuntimeModuleId {
  val idToReuse = existingIdsToReuse[module]
  if (idToReuse != null) return idToReuse

  val contentModuleData = pluginDescriptorData.contentModules[module.name]
  if (contentModuleData != null) {
    return RuntimeModuleId.contentModule(contentModuleData.name, contentModuleData.namespace)
  }
  val namespaceSuffix =
    if (hasTestSourcesAndNoProductionSources(module)) RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE_SUFFIX
    else RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE_SUFFIX
  val namespace =
    if (pluginDescriptorData.pluginId == "com.intellij" || module !in elementsIncludedInMultiplePlugins) namespaceSuffix
    else "${pluginDescriptorData.pluginId}_$namespaceSuffix"
  return RuntimeModuleId.raw(module.name, namespace)
}

private fun createIdForJpsLibrary(library: JpsLibrary, pluginId: String, elementsIncludedInMultiplePlugins: Set<JpsNamedElement>): RuntimeModuleId {
  val namespace =
    if (pluginId == "com.intellij" || library !in elementsIncludedInMultiplePlugins) RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX
    else "${pluginId}_${RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX}"
  return RuntimeModuleId.raw(library.name, namespace)
}
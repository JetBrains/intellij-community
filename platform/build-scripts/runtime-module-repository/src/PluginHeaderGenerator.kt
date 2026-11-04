// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.runtimeModuleRepository

import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import com.intellij.util.containers.MultiMap

/**
 * Contains information about modules and libraries included in the plugin.
 */
internal class RuntimePluginHeaderData(
  val header: RuntimePluginHeader,
  /** Modules included in the plugin distribution but aren't loaded by the IDE process */
  val additionalModules: List<RuntimeModuleId>,
  val visibilityOfModules: Map<RuntimeModuleId, RuntimeModuleVisibility>,
  val includedElementToId: Map<RuntimeModelElement, RuntimeModuleId>,
  val moduleIdToElement: Map<RuntimeModuleId, RuntimeModelElement>,
  /** Mapping from a library to the list of content modules that include it in their JARs */
  val projectLibrariesToIncludingContentModules: MultiMap<RuntimeModelElement, RuntimeModuleId>,
  /** Mapping from ID of a module to the list of paths from its classpath (relative to the root of the installation directory) */
  val classpathEntries: MultiMap<RuntimeModuleId, String>,
  /** Mapping from ID of an included module to the list of its dependencies on plugin descriptor modules (via `<dependencies><plugin>` tag) */
  val dependenciesOnPluginDescriptorModules: LinkedHashMap<RuntimeModuleId, List<RuntimeModuleId>>,
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
  pluginConfigurationModuleToDistributionEntries: Map<String, Collection<PluginDistributionEntry>>,
  model: RuntimeModuleModel,
): List<RuntimePluginHeaderData> {
  val jpsElementToPlugins = pluginDescriptorsData
    .filterNot { it.additionalFrontendOnlyPlugin }
    .flatMap { pluginDescriptorsData ->
      val distributionEntries = pluginConfigurationModuleToDistributionEntries.getValue(pluginDescriptorsData.pluginDescriptorJpsModuleName)
      collectElementsIncludedInPlugin(distributionEntries, model).map { jpsElement -> jpsElement to pluginDescriptorsData }
    }
    .groupBy({ it.first }, { it.second })
  val elementsIncludedInMultiplePlugins = jpsElementToPlugins.asSequence().filter { it.value.size > 1 }.mapTo(HashSet()) { it.key }

  val (regularPluginDescriptorData, frontendOnlyPluginDescriptorData) = pluginDescriptorsData.partition { !it.additionalFrontendOnlyPlugin }
  val regularPluginHeaderData = regularPluginDescriptorData.map { pluginDescriptorsData ->
    val distributionEntries = pluginConfigurationModuleToDistributionEntries.getValue(pluginDescriptorsData.pluginDescriptorJpsModuleName)
    generateRuntimePluginHeader(pluginDescriptorsData, distributionEntries, model, elementsIncludedInMultiplePlugins, existingIdsToReuse = emptyMap())
  }

  /* generate headers for additional frontend-only plugins; these plugins don't register their own modules, they reuse modules registered by regular plugins instead;
     this part can be removed as soon as we get rid of such plugins (IJPL-220139) */
  val existingIdsToReuse = regularPluginHeaderData.fold(emptyMap<RuntimeModelElement, RuntimeModuleId>()) { acc, data -> acc + data.includedElementToId }
  val frontendOnlyPluginHeaderData = frontendOnlyPluginDescriptorData.map { pluginDescriptorsData ->
    val distributionEntries = pluginConfigurationModuleToDistributionEntries.getValue(pluginDescriptorsData.pluginDescriptorJpsModuleName)
    generateRuntimePluginHeader(pluginDescriptorsData, distributionEntries, model, elementsIncludedInMultiplePlugins, existingIdsToReuse)
  }

  return regularPluginHeaderData + frontendOnlyPluginHeaderData
}

private fun collectElementsIncludedInPlugin(distributionEntries: Collection<PluginDistributionEntry>, model: RuntimeModuleModel): Set<RuntimeModelElement> {
  return distributionEntries.mapNotNullTo(LinkedHashSet()) { entry ->
    when (entry.kind) {
      PluginDistributionEntry.Kind.MODULE_OUTPUT -> if (model.findModule(entry.name) != null) moduleElement(entry.name) else null
      PluginDistributionEntry.Kind.PROJECT_LIBRARY -> if (model.hasProjectLibrary(entry.name)) projectLibraryElement(entry.name) else null
      PluginDistributionEntry.Kind.MODULE_LIBRARY -> null
    }
  }
}

private fun generateRuntimePluginHeader(
  pluginDescriptorData: PluginDescriptorDataForHeader,
  distributionEntries: Collection<PluginDistributionEntry>,
  model: RuntimeModuleModel,
  elementsIncludedInMultiplePlugins: Set<RuntimeModelElement>,
  existingIdsToReuse: Map<RuntimeModelElement, RuntimeModuleId>,
): RuntimePluginHeaderData {
  val includedModules = ArrayList<IncludedRuntimeModule>()
  val additionalModules = ArrayList<RuntimeModuleId>()
  val visibilityOfModules = HashMap<RuntimeModuleId, RuntimeModuleVisibility>()
  val includedElementToId = HashMap<RuntimeModelElement, RuntimeModuleId>()
  val moduleIdToElement = HashMap<RuntimeModuleId, RuntimeModelElement>()
  val classpathEntries = MultiMap.createOrderedSet<RuntimeModuleId, String>()
  val moduleLibraryPaths = MultiMap.createOrderedSet<String, String>()
  val projectLibrariesToIncludingContentModules = MultiMap.createOrderedSet<RuntimeModelElement, RuntimeModuleId>()
  distributionEntries.forEach { entry ->
    val pathRelativeToDistributionDir = entry.path
    val outputPathRelativeToPluginLibDir = entry.relativeOutputFile ?: ""
    val includedModule =
      when (entry.kind) {
        PluginDistributionEntry.Kind.MODULE_OUTPUT -> {
          val moduleName = entry.name
          val contentModuleData = pluginDescriptorData.contentModules[moduleName]
          val loadingRule = contentModuleData?.loadingRule ?: RuntimeModuleLoadingRule.EMBEDDED
          val requiredIfAvailableId = contentModuleData?.requiredIfAvailable
          val moduleFacts = model.findModule(moduleName) ?: error("Cannot find module by name: $moduleName")
          val module = moduleElement(moduleName)
          val moduleId = createIdForModule(module, moduleFacts, contentModuleData, pluginDescriptorData, elementsIncludedInMultiplePlugins, existingIdsToReuse)
          if (moduleId !in moduleIdToElement) {
            moduleIdToElement[moduleId] = module
            visibilityOfModules[moduleId] = contentModuleData?.visibility ?: RuntimeModuleVisibility.PUBLIC
            includedElementToId[module] = moduleId
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
        PluginDistributionEntry.Kind.PROJECT_LIBRARY -> {
          val libraryName = entry.name
          if (!model.hasProjectLibrary(libraryName)) {
            error("Cannot find library by name: $libraryName")
          }
          val library = projectLibraryElement(libraryName)
          val containingContentModule =
            if (outputPathRelativeToPluginLibDir.startsWith("modules/")) pluginDescriptorData.contentModules[outputPathRelativeToPluginLibDir.removePrefix("modules/").removeSuffix(".jar")]
            else null
          if (containingContentModule != null) {
            projectLibrariesToIncludingContentModules.putValue(library, RuntimeModuleId.contentModule(containingContentModule.name, containingContentModule.namespace))
            null
          }
          else {
            val moduleId = createIdForLibrary(library, pluginDescriptorData.pluginId, elementsIncludedInMultiplePlugins)
            if (pathRelativeToDistributionDir != null) {
              classpathEntries.putValue(moduleId, pathRelativeToDistributionDir)
            }
            if (moduleId !in moduleIdToElement) {
              moduleIdToElement[moduleId] = library
              includedElementToId[library] = moduleId
              IncludedRuntimeModuleImpl(moduleId, RuntimeModuleLoadingRule.EMBEDDED, null)
            }
            else {
              //if a library consists of several files, there will be multiple entries, so we need to register it only once
              null
            }
          }
        }
        PluginDistributionEntry.Kind.MODULE_LIBRARY -> {
          if (pathRelativeToDistributionDir != null) {
            moduleLibraryPaths.putValue(entry.name, pathRelativeToDistributionDir)
          }
          //no separate runtime modules are generated for module-level libraries; their paths are included in the descriptor corresponding to the containing module instead
          null
        }
      }
    if (includedModule != null) {
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
    if (model.findModule(moduleName) == null) {
      error("Cannot find module $moduleName")
    }
    val moduleId = includedElementToId[moduleElement(moduleName)] ?: RuntimeModuleId.raw(moduleName, "${pluginDescriptorData.pluginId}_${RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX}")
    classpathEntries.putValues(moduleId, paths)
  }

  val pluginDescriptorModuleName = pluginDescriptorData.pluginDescriptorJpsModuleName
  val pluginDescriptorModuleFacts = model.findModule(pluginDescriptorModuleName) ?: error("Cannot find module $pluginDescriptorModuleName")
  val pluginDescriptorModuleId = createIdForModule(
    moduleElement(pluginDescriptorModuleName),
    pluginDescriptorModuleFacts,
    pluginDescriptorData.contentModules[pluginDescriptorModuleName],
    pluginDescriptorData,
    elementsIncludedInMultiplePlugins,
    existingIdsToReuse
  )
  val header = RuntimePluginHeaderImpl(pluginDescriptorData.pluginId, pluginDescriptorModuleId, includedModules)
  val dependenciesOnPluginDescriptorModules = LinkedHashMap<RuntimeModuleId, List<RuntimeModuleId>>()
  if (pluginDescriptorData.pluginDescriptorDependenciesOnPluginDescriptorModules.isNotEmpty()) {
    dependenciesOnPluginDescriptorModules[pluginDescriptorModuleId] = pluginDescriptorData.pluginDescriptorDependenciesOnPluginDescriptorModules
  }
  for (includedModule in header.includedModules) {
    val dependencies = pluginDescriptorData.contentModules[includedModule.moduleId.name]?.dependenciesOnPluginDescriptorModules
    if (!dependencies.isNullOrEmpty()) {
      val existing = dependenciesOnPluginDescriptorModules[includedModule.moduleId]
      dependenciesOnPluginDescriptorModules[includedModule.moduleId] = if (existing == null) dependencies else existing + dependencies
    }
  }
  return RuntimePluginHeaderData(header, additionalModules, visibilityOfModules, includedElementToId, moduleIdToElement, projectLibrariesToIncludingContentModules, classpathEntries,
                                 dependenciesOnPluginDescriptorModules)
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
  module: RuntimeModelElement,
  moduleFacts: RuntimeModuleFacts,
  contentModuleData: ContentModuleRegistrationDataForHeader?,
  pluginDescriptorData: PluginDescriptorDataForHeader,
  elementsIncludedInMultiplePlugins: Set<RuntimeModelElement>,
  existingIdsToReuse: Map<RuntimeModelElement, RuntimeModuleId>
): RuntimeModuleId {
  val idToReuse = existingIdsToReuse[module]
  if (idToReuse != null) return idToReuse

  if (contentModuleData != null) {
    return RuntimeModuleId.contentModule(contentModuleData.name, contentModuleData.namespace)
  }
  val namespaceSuffix =
    if (moduleFacts.testOnly) RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE_SUFFIX
    else RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE_SUFFIX
  val namespace =
    if (pluginDescriptorData.pluginId == "com.intellij" || module !in elementsIncludedInMultiplePlugins) namespaceSuffix
    else "${pluginDescriptorData.pluginId}_$namespaceSuffix"
  return RuntimeModuleId.raw(module.name, namespace)
}

private fun createIdForLibrary(library: RuntimeModelElement, pluginId: String, elementsIncludedInMultiplePlugins: Set<RuntimeModelElement>): RuntimeModuleId {
  val namespace =
    if (pluginId == "com.intellij" || library !in elementsIncludedInMultiplePlugins) RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX
    else "${pluginId}_${RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX}"
  return RuntimeModuleId.raw(library.name, namespace)
}

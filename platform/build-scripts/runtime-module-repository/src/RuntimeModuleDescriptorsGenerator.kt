// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.runtimeModuleRepository

import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor

internal fun generateRuntimeModuleDescriptors(pluginHeadersData: List<RuntimePluginHeaderData>, model: RuntimeModuleModel): List<RawRuntimeModuleDescriptor> {
  val elementToIds = pluginHeadersData
    .flatMap { it.includedElementToId.entries }
    .groupBy({ it.key }, { it.value })
  return pluginHeadersData.asSequence().flatMap {
    generateRuntimeModuleDescriptorsForPluginHeader(it, elementToIds, model)
  }.toList()
}

private fun generateRuntimeModuleDescriptorsForPluginHeader(
  pluginHeaderData: RuntimePluginHeaderData,
  elementToIds: Map<RuntimeModelElement, List<RuntimeModuleId>>,
  model: RuntimeModuleModel,
): Sequence<RawRuntimeModuleDescriptor> {
  val allIncludedModuleIds = pluginHeaderData.header.includedModules.asSequence().map { it.moduleId } + pluginHeaderData.additionalModules
  return allIncludedModuleIds.map { moduleId ->
    val visibility = pluginHeaderData.visibilityOfModules[moduleId] ?: RuntimeModuleVisibility.PUBLIC
    val dependencies = generateDependenciesForModule(moduleId, pluginHeaderData, elementToIds, model)
    val classpathEntries = pluginHeaderData.classpathEntries[moduleId].map {
      //converts a path relative to the root of the installation to a path relative to the runtime module repository
      if (it.startsWith("$RUNTIME_REPOSITORY_MODULES_DIR_NAME/")) it.removePrefix("$RUNTIME_REPOSITORY_MODULES_DIR_NAME/") else "../$it"
    }
    RawRuntimeModuleDescriptor.create(moduleId, visibility, classpathEntries, dependencies)
  }
}

private fun generateDependenciesForModule(
  moduleId: RuntimeModuleId,
  pluginHeaderData: RuntimePluginHeaderData,
  elementToIds: Map<RuntimeModelElement, List<RuntimeModuleId>>,
  model: RuntimeModuleModel,
): List<RuntimeModuleId> {
  val element = pluginHeaderData.moduleIdToElement[moduleId] ?: return emptyList()
  val moduleFacts = when (element.kind) {
    RuntimeModelElement.Kind.MODULE -> model.findModule(element.name) ?: error("Cannot find module by name: ${element.name}")
    RuntimeModelElement.Kind.PROJECT_LIBRARY -> return emptyList()
  }
  val dependencies = ArrayList<RuntimeModuleId>()
  for (dependency in moduleFacts.dependencies) {
    val moduleName = dependency.module
    if (moduleName != null) {
      dependencies.add(findTargetModuleId(moduleElement(moduleName), dependency.contentModule, pluginHeaderData, elementToIds))
      continue
    }
    val library = projectLibraryElement(dependency.library!!)
    val containingContentModules = pluginHeaderData.projectLibrariesToIncludingContentModules[library]
    if (containingContentModules.isEmpty()) {
      dependencies.add(findTargetModuleId(library, isContentModule = false, pluginHeaderData, elementToIds))
    }
    else if (moduleId !in containingContentModules) {
      require(containingContentModules.size == 1) {
        "Library ${library.name} is included in multiple content modules in the same plugin ${pluginHeaderData.header.pluginId} ($containingContentModules) and used as a dependency in another module ${moduleId.displayName}"
      }
      dependencies.add(containingContentModules.single())
    }
  }
  val additional = pluginHeaderData.dependenciesOnPluginDescriptorModules[moduleId]
  if (!additional.isNullOrEmpty()) {
    dependencies.addAll(additional)
  }
  return dependencies
}

/**
 * Returns the ID of the dependency target [element]. [isContentModule] tells whether a module outside the distribution has its `<name>.xml`
 * descriptor in a production root.
 */
private fun findTargetModuleId(
  element: RuntimeModelElement,
  isContentModule: Boolean,
  pluginHeaderData: RuntimePluginHeaderData,
  elementToIds: Map<RuntimeModelElement, List<RuntimeModuleId>>,
): RuntimeModuleId {
  val inThisPlugin = pluginHeaderData.includedElementToId[element]
  if (inThisPlugin != null) return inThisPlugin

  val inOtherPlugin = elementToIds[element]?.singleOrNull()
  if (inOtherPlugin != null) return inOtherPlugin

  return when (element.kind) {
    RuntimeModelElement.Kind.PROJECT_LIBRARY -> RuntimeModuleId.projectLibrary(element.name)
    RuntimeModelElement.Kind.MODULE -> {
      if (isContentModule) RuntimeModuleId.contentModule(element.name, RuntimeModuleId.DEFAULT_NAMESPACE) else RuntimeModuleId.legacyJpsModule(element.name)
    }
  }
}

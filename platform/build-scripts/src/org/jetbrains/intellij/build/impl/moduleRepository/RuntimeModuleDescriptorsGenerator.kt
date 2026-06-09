// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import org.jetbrains.jps.model.JpsNamedElement
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import java.util.concurrent.ConcurrentHashMap

internal fun generateRuntimeModuleDescriptors(pluginHeadersData: List<RuntimePluginHeaderData>): List<RawRuntimeModuleDescriptor> {
  val elementToIds = pluginHeadersData
    .flatMap { it.includedElementToId.entries }
    .groupBy({ it.key }, { it.value })
  val contentModuleDetector = ContentModuleDetector()
  return pluginHeadersData.asSequence().flatMap {
    generateRuntimeModuleDescriptorsForPluginHeader(it, elementToIds, contentModuleDetector)
  }.toList()
}

private fun generateRuntimeModuleDescriptorsForPluginHeader(
  pluginHeaderData: RuntimePluginHeaderData,
  elementToIds: Map<JpsNamedElement, List<RuntimeModuleId>>,
  contentModuleDetector: ContentModuleDetector
): Sequence<RawRuntimeModuleDescriptor> {
  val allIncludedModuleIds = pluginHeaderData.header.includedModules.asSequence().map { it.moduleId } + pluginHeaderData.additionalModules
  return allIncludedModuleIds.map { moduleId ->
    val visibility = pluginHeaderData.visibilityOfModules[moduleId] ?: RuntimeModuleVisibility.PUBLIC
    val dependencies = generateDependenciesForModule(moduleId, pluginHeaderData, elementToIds, contentModuleDetector)
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
  elementToIds: Map<JpsNamedElement, List<RuntimeModuleId>>,
  contentModuleDetector: ContentModuleDetector,
): List<RuntimeModuleId> {
  val jpsElement = pluginHeaderData.moduleIdToJpsElement[moduleId]
  if (jpsElement !is JpsModule) {
    return emptyList()
  }
  val dependencies = ArrayList<RuntimeModuleId>()
  JpsJavaExtensionService.dependencies(jpsElement).withoutSdk().withoutModuleSourceEntries().runtimeOnly().productionOnly().forEachModuleAndLibrary(
    { module ->
      dependencies.add(findTargetModuleId(module, pluginHeaderData, elementToIds, contentModuleDetector))
    },
    { library ->
      if (library.isProjectLevel) {
        dependencies.add(findTargetModuleId(library, pluginHeaderData, elementToIds, contentModuleDetector))
      }
    }
  )
  return dependencies
}

private val JpsLibrary.isProjectLevel: Boolean
  get() = (this as JpsElementBase<*>).parent.parent is JpsProject

private fun findTargetModuleId(
  jpsElement: JpsNamedElement,
  pluginHeaderData: RuntimePluginHeaderData,
  elementToIds: Map<JpsNamedElement, List<RuntimeModuleId>>,
  contentModuleDetector: ContentModuleDetector,
): RuntimeModuleId {
  val inThisPlugin = pluginHeaderData.includedElementToId[jpsElement]
  if (inThisPlugin != null) return inThisPlugin

  val inOtherPlugin = elementToIds[jpsElement]?.singleOrNull()
  if (inOtherPlugin != null) return inOtherPlugin

  return when (jpsElement) {
    is JpsLibrary -> RuntimeModuleId.projectLibrary(jpsElement.name)
    is JpsModule if contentModuleDetector.isContentModule(jpsElement) -> RuntimeModuleId.contentModule(jpsElement.name, RuntimeModuleId.DEFAULT_NAMESPACE)
    is JpsModule -> RuntimeModuleId.legacyJpsModule(jpsElement.name)
    else -> error("Unexpected JPS element $jpsElement in dependencies of $pluginHeaderData")
  }
}

private class ContentModuleDetector {
  private val isContentModuleCache = ConcurrentHashMap<JpsModule, Boolean>()

  fun isContentModule(jpsModule: JpsModule): Boolean {
    return isContentModuleCache.computeIfAbsent(jpsModule) {
      /* we have a few modules that contain the module XML descriptor but aren't registered as content modules (IJPL-210868), but given that this function is used only to compute
         dependencies on modules not included in the current distribution, this should lead to problems */
      JpsJavaExtensionService.getInstance().findSourceFileInProductionRoots(jpsModule, "${jpsModule.name}.xml") != null
    }
  }
}


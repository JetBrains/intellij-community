// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class ModifiableWorkspaceModelImpl internal constructor(
  private val state: ExternalProjectsWorkspace.State,
  private val modelsProvider: IdeModifiableModelsProvider,
) : ModifiableWorkspaceModel {

  private val librarySubstitutions = state.librarySubstitutions.toMutableMap()

  @RequiresWriteLock
  override fun commit() {
    state.librarySubstitutions = librarySubstitutions.toMutableMap()
  }

  override fun isLibrarySubstituted(library: Library): Boolean {
    return library.name != null && librarySubstitutions.containsValue(library.name)
  }

  override fun updateLibrarySubstitutions() {
    val libraryToModuleMap = buildLibraryToModuleMap()

    for (module in modelsProvider.modules) {
      val modifiableModule = modelsProvider.getModifiableRootModel(module)

      val entries = modifiableModule.orderEntries
      var changed = false

      for (i in entries.indices) {
        val orderEntry = entries[i]
        val newOrderEntry = updateLibrarySubstitution(modifiableModule, orderEntry, libraryToModuleMap)
        if (newOrderEntry !== orderEntry) {
          entries[i] = newOrderEntry
          changed = true
        }
      }

      if (changed) {
        modifiableModule.rearrangeOrderEntries(entries)
      }
    }
  }

  private fun buildLibraryToCoordinateMap(): Map<String, ProjectCoordinate> {
    val result = HashMap<String, ProjectCoordinate>()
    ExternalSystemCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
      for (library in modelsProvider.allLibraries) {
        val libraryName = library.name ?: continue
        val libraryCoordinate = contributor.findLibraryCoordinate(library) ?: continue
        result.put(libraryName, libraryCoordinate)
      }
    }
    return result
  }

  private fun buildCoordinateToModuleMap(): Map<ProjectCoordinate, String> {
    val result = CollectionFactory.createCustomHashingStrategyMap<ProjectCoordinate, String>(ProjectCoordinateHashingStrategy())
    ExternalSystemCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
      for (module in modelsProvider.modules) {
        val moduleCoordinate = contributor.findModuleCoordinate(module) ?: continue
        result.put(moduleCoordinate, module.name)
      }
    }
    return result
  }

  private fun buildLibraryToModuleMap(): Map<String, String> {
    val libraryToCoordinateMap = buildLibraryToCoordinateMap()
    val coordinateToModuleMap = buildCoordinateToModuleMap()
    val result = HashMap<String, String>()
    for ((libraryName, libraryCoordinate) in libraryToCoordinateMap) {
      val moduleName = coordinateToModuleMap[libraryCoordinate] ?: continue
      result[libraryName] = moduleName
    }
    return result
  }

  private fun updateLibrarySubstitution(
    modifiableModule: ModifiableRootModel,
    orderEntry: OrderEntry,
    libraryToModuleMap: Map<String, String>,
  ): OrderEntry {
    var newOrderEntry = orderEntry
    if (newOrderEntry is ModuleOrderEntry) {
      newOrderEntry = replaceModuleWithLibraryOrNull(modifiableModule, newOrderEntry, libraryToModuleMap)
                      ?: newOrderEntry
    }
    if (newOrderEntry is LibraryOrderEntry) {
      newOrderEntry = replaceLibraryWithModuleOrNull(modifiableModule, newOrderEntry, libraryToModuleMap)
                      ?: newOrderEntry
    }
    return newOrderEntry
  }

  private fun replaceModuleWithLibraryOrNull(
    modifiableModule: ModifiableRootModel,
    moduleOrderEntry: ModuleOrderEntry,
    libraryToModuleMap: Map<String, String>,
  ): LibraryOrderEntry? {
    val ownerModuleName = modifiableModule.module.name
    val moduleName = moduleOrderEntry.moduleName
    val moduleScope = moduleOrderEntry.scope
    val moduleScopeName = moduleScope.displayName
    val isModuleExported = moduleOrderEntry.isExported
    val substitutionId = getLibrarySubstitutionId(ownerModuleName, moduleName, moduleScopeName)

    val libraryName = librarySubstitutions[substitutionId] ?: return null

    if (libraryToModuleMap[libraryName] == moduleName) {
      return null
    }

    val library = modelsProvider.getLibraryByName(libraryName) ?: return null
    val libraryOrderEntry = modifiableModule.addLibraryEntry(library)
    libraryOrderEntry.setScope(moduleScope)
    libraryOrderEntry.setExported(isModuleExported)

    modifiableModule.removeOrderEntry(moduleOrderEntry)

    librarySubstitutions.remove(substitutionId)

    return libraryOrderEntry
  }

  private fun replaceLibraryWithModuleOrNull(
    modifiableModule: ModifiableRootModel,
    libraryOrderEntry: LibraryOrderEntry,
    libraryToModuleMap: Map<String, String>,
  ): ModuleOrderEntry? {

    if (libraryOrderEntry.isModuleLevel) {
      return null
    }

    val ownerModuleName = modifiableModule.module.name
    val libraryName = libraryOrderEntry.libraryName ?: return null
    val libraryScope = libraryOrderEntry.scope
    val libraryScopeName = libraryScope.displayName
    val isLibraryExported = libraryOrderEntry.isExported

    val moduleName = libraryToModuleMap[libraryName] ?: return null

    val module = modelsProvider.findIdeModule(moduleName) ?: return null
    val moduleOrderEntry = modifiableModule.addModuleOrderEntry(module)
    moduleOrderEntry.setScope(libraryScope)
    moduleOrderEntry.setExported(isLibraryExported)

    modifiableModule.removeOrderEntry(libraryOrderEntry)

    val substitutionId: String = getLibrarySubstitutionId(ownerModuleName, moduleName, libraryScopeName)
    librarySubstitutions.put(substitutionId, libraryName)

    return moduleOrderEntry
  }

  private class ProjectCoordinateHashingStrategy : HashingStrategy<ProjectCoordinate> {

    override fun equals(o1: ProjectCoordinate, o2: ProjectCoordinate?): Boolean {
      return o2 != null &&
             o1.groupId == o2.groupId &&
             o1.artifactId == o2.artifactId &&
             o1.version == o2.version
    }

    override fun hashCode(o: ProjectCoordinate): Int {
      return Objects.hash(o.groupId, o.artifactId, o.version)
    }
  }

  companion object {

    private fun getLibrarySubstitutionId(ownerModuleName: String, moduleName: String, scopeName: String): String {
      return ownerModuleName + "_" + moduleName + '_' + scopeName
    }
  }
}

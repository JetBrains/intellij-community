// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.annotations.ApiStatus
import kotlin.collections.iterator

@ApiStatus.Internal
object DependencySubstitutionUtil {

  private val TELEMETRY by lazy {
    TelemetryManager.getInstance().getTracer(DependencySubstitution)
  }

  @JvmStatic
  fun isLibrarySubstituted(storage: MutableEntityStorage, library: LibraryId): Boolean {
    return storage.entities<DependencySubstitutionEntity>()
      .find { it.library == library } != null
  }

  @JvmStatic
  fun updateDependencySubstitutions(storage: MutableEntityStorage) {
    if (!Registry.`is`("external.system.substitute.library.dependencies")) {
      return
    }
    TELEMETRY.spanBuilder("updateDependencySubstitutions").use {
      val libraryToModuleMap = buildLibraryToModuleMap(storage)
      for (module in storage.entities<ModuleEntity>()) {
        storage.modifyModuleEntity(module) {
          for (i in dependencies.indices) {
            dependencies[i] = updateDependencySubstitution(storage, module, dependencies[i], libraryToModuleMap)
          }
        }
      }
    }
  }

  fun updateDependencySubstitution(
    storage: MutableEntityStorage,
    module: ModuleEntity,
    dependency: ModuleDependencyItem,
    libraryToModuleMap: Map<LibraryId, ModuleId>,
  ): ModuleDependencyItem {
    var newDependency = dependency
    if (newDependency is ModuleDependency) {
      newDependency = replaceModuleWithLibraryOrNull(storage, module, newDependency, libraryToModuleMap) ?: newDependency
    }
    if (newDependency is LibraryDependency) {
      newDependency = replaceLibraryWithModuleOrNull(storage, module, newDependency, libraryToModuleMap) ?: newDependency
    }
    return newDependency
  }

  private fun replaceModuleWithLibraryOrNull(
    storage: MutableEntityStorage,
    ownerModule: ModuleEntity,
    moduleDependency: ModuleDependency,
    libraryToModuleMap: Map<LibraryId, ModuleId>,
  ): LibraryDependency? {

    val ownerId = ownerModule.symbolicId

    val moduleId = moduleDependency.module
    val scope = moduleDependency.scope
    val exported = moduleDependency.exported

    val substitutionId = DependencySubstitutionId(ownerId, moduleId, scope)

    val substitution = storage.resolve(substitutionId) ?: return null

    val libraryId = substitution.library

    if (libraryToModuleMap[libraryId] == moduleId) {
      return null
    }

    val libraryDependency = LibraryDependency(libraryId, exported, scope)

    storage.removeEntity(substitution)

    return libraryDependency
  }

  private fun replaceLibraryWithModuleOrNull(
    storage: MutableEntityStorage,
    ownerModule: ModuleEntity,
    libraryDependency: LibraryDependency,
    libraryToModuleMap: Map<LibraryId, ModuleId>,
  ): ModuleDependency? {

    val ownerId = ownerModule.symbolicId
    val entitySource = ownerModule.entitySource

    val libraryId = libraryDependency.library
    val scope = libraryDependency.scope
    val exported = libraryDependency.exported

    val moduleId = libraryToModuleMap[libraryId] ?: return null

    val moduleDependency = ModuleDependency(moduleId, exported, scope, productionOnTest = false)

    storage addEntity DependencySubstitutionEntity(ownerId, libraryId, moduleId, scope, entitySource)

    return moduleDependency
  }

  private fun buildLibraryToCoordinateMap(storage: EntityStorage): Map<LibraryId, Any> {
    TELEMETRY.spanBuilder("buildLibraryToCoordinateMap").use {
      val result = HashMap<LibraryId, Any>()
      DependencySubstitutionCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
        TELEMETRY.spanBuilder("findLibraryCoordinate").use { span ->
          span.setAttribute("contributor", contributor.javaClass.name)
          for (library in storage.entities<LibraryEntity>()) {
            val libraryCoordinate = contributor.findLibraryCoordinate(library) ?: continue
            result[library.symbolicId] = libraryCoordinate
          }
        }
      }
      return result
    }
  }

  private fun buildCoordinateToModuleMap(storage: EntityStorage): Map<Any, ModuleId> {
    TELEMETRY.spanBuilder("buildCoordinateToModuleMap").use {
      val result = HashMap<Any, ModuleId>()
      DependencySubstitutionCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
        TELEMETRY.spanBuilder("findModuleCoordinate").use { span ->
          span.setAttribute("contributor", contributor.javaClass.name)
          for (module in storage.entities<ModuleEntity>()) {
            val moduleCoordinate = contributor.findModuleCoordinate(module) ?: continue
            result[moduleCoordinate] = module.symbolicId
          }
        }
      }
      return result
    }
  }

  private fun buildLibraryToModuleMap(storage: EntityStorage): Map<LibraryId, ModuleId> {
    TELEMETRY.spanBuilder("buildLibraryToModuleMap").use {
      val libraryToCoordinateMap = buildLibraryToCoordinateMap(storage)
      val coordinateToModuleMap = buildCoordinateToModuleMap(storage)
      val result = HashMap<LibraryId, ModuleId>()
      for ((libraryId, libraryCoordinate) in libraryToCoordinateMap) {
        val moduleId = coordinateToModuleMap[libraryCoordinate] ?: continue
        result[libraryId] = moduleId
      }
      return result
    }
  }
}

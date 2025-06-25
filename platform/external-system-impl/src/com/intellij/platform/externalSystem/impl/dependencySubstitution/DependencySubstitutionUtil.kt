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
          updateDependencySubstitution(storage, libraryToModuleMap)
        }
      }
    }
  }

  private fun ModuleEntity.Builder.updateDependencySubstitution(
    storage: MutableEntityStorage,
    libraryToModuleMap: Map<LibraryId, ModuleId>,
  ) {
    val ownerId = ModuleId(name)

    // Tries to replace outdated module substitution by original library
    for ((dependencyOrder, dependency) in dependencies.withIndex()) {
      if (dependency is ModuleDependency) {

        val moduleId = dependency.module
        val scope = dependency.scope
        val exported = dependency.exported

        val substitutionId = DependencySubstitutionId(ownerId, moduleId, scope)

        val substitution = storage.resolve(substitutionId) ?: continue

        val libraryId = substitution.library

        if (libraryToModuleMap[libraryId] == moduleId) {
          continue
        }

        storage.removeEntity(substitution)

        dependencies[dependencyOrder] = LibraryDependency(libraryId, exported, scope)
      }
    }

    // Tries to replace library by module dependency
    for ((dependencyOrder, dependency) in dependencies.withIndex()) {
      if (dependency is LibraryDependency) {

        val libraryId = dependency.library
        val scope = dependency.scope
        val exported = dependency.exported

        val moduleId = libraryToModuleMap[libraryId] ?: continue

        storage.addEntity(DependencySubstitutionEntity(ownerId, libraryId, moduleId, scope, entitySource))

        dependencies[dependencyOrder] = ModuleDependency(moduleId, exported, scope, productionOnTest = false)
      }
    }
  }

  private fun buildLibraryToModuleMap(storage: EntityStorage): Map<LibraryId, ModuleId> {
    TELEMETRY.spanBuilder("buildLibraryToModuleMap").use {
      val result = HashMap<LibraryId, ModuleId>()
      DependencySubstitutionExtension.EP_NAME.forEachExtensionSafe { contributor ->
        TELEMETRY.spanBuilder("buildDependencyMap").use { span ->
          span.setAttribute("contributor", contributor.javaClass.name)
          result.putAll(contributor.buildLibraryToModuleMap(storage))
        }
      }
      return result
    }
  }

  @ApiStatus.Internal
  fun <K: Any?, V1, V2> Map<K, V1>.intersect(other: Map<K, V2>): Map<V1, V2> {
    val result = HashMap<V1, V2>()
    for ((key, value1) in this) {
      result[value1] = other[key ?: continue] ?: continue
    }
    return result
  }
}

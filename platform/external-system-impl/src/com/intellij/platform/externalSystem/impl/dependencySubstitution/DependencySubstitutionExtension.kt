// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityStorage
import org.jetbrains.annotations.ApiStatus

/**
 * Defines mapping between ide library and module entities.
 *
 * The import can use that mapping for library dependencies substitution on module dependencies
 * across all external projects.
 */
@ApiStatus.Internal
interface DependencySubstitutionExtension {

  /**
   * Builds relation between library and module dependencies based on any library and module characteristic.
   *
   * Note: this function may be executed under write action.
   */
  fun buildLibraryToModuleMap(storage: EntityStorage): Map<LibraryId, ModuleId>

  companion object {

    val EP_NAME: ExtensionPointName<DependencySubstitutionExtension> =
      ExtensionPointName.create("com.intellij.dependencySubstitutionCoordinateContributor")
  }
}
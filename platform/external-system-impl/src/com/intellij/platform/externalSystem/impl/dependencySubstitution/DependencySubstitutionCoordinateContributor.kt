// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import org.jetbrains.annotations.ApiStatus

/**
 * Defines mapping between ide entities and entities' global coordinates.
 * The import can use that mapping for binary dependencies substitution on module dependencies
 * across unrelated projects based on any build system or another kind of project modules generator.
 *
 * For example, maven coordinates can be used as global coordinates.
 */
@ApiStatus.Experimental
interface DependencySubstitutionCoordinateContributor {

  /**
   * Finds global module coordinates. For example, maven coordinates.
   *
   * @param module is a module to find artifact coordinates of a corresponding external project.
   * @return found global module coordinates.
   */
  fun findModuleCoordinate(module: ModuleEntity): Any? = null

  /**
   * Finds global library coordinates. For example, maven coordinates.
   *
   * @param library is a library to find artifact coordinates of a corresponding external library.
   * @return found global library coordinates.
   */
  fun findLibraryCoordinate(library: LibraryEntity): Any? = null

  companion object {

    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<DependencySubstitutionCoordinateContributor> =
      ExtensionPointName.create("com.intellij.dependencySubstitutionCoordinateContributor")
  }
}
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.annotations.ApiStatus

/**
 * Defines mapping between ide entities and maven-like artifact coordinates of these entities.
 * The import can use that mapping for binary dependencies substitution on module dependencies
 * across unrelated projects based on any build system or another kind of project modules generator.
 */
interface ExternalSystemCoordinateContributor {

  /**
   * Finds maven-like artifact coordinates for external project that corresponding to the given module.
   *
   * @param module is a module to find artifact coordinates of a corresponding external project.
   * @return found external artifact coordinates.
   */
  fun findModuleCoordinate(module: Module): ProjectCoordinate? {
    return null
  }

  /**
   * Finds maven-like artifact coordinates for external library that corresponding to the given library.
   *
   * @param library is a library to find artifact coordinates of a corresponding external library.
   * @return found external artifact coordinates.
   */
  fun findLibraryCoordinate(library: Library): ProjectCoordinate? {
    return null
  }

  companion object {

    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<ExternalSystemCoordinateContributor> =
      ExtensionPointName.create("com.intellij.externalSystemCoordinateContributor")
  }
}

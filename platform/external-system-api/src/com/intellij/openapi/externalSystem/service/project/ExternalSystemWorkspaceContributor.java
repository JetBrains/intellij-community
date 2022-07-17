package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Defines mapping between ide modules and maven-like artifact coordinates of these modules.
 * That mapping can be used by the import for binary dependencies substitution on module dependencies
 * across unrelated projects based on any build system or another kind of project modules generator.
 */
@ApiStatus.Experimental
public interface ExternalSystemWorkspaceContributor {
  /**
   * Finds project coordinates for external project that corresponding to given module.
   *
   * @param module         is a module to find project coordinates of corresponding external project.
   * @return found external project coordinates.
   */
  @Nullable ProjectCoordinate findProjectId(Module module);
}

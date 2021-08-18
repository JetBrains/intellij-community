package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts common data from external system data that is specifically smeared across workspace model.
 */
@ApiStatus.Experimental
public interface ExternalSystemWorkspaceContributor {
  /**
   * Finds project coordinates for external project that corresponding to given module.
   *
   * @param module         is a module to find project coordinates of corresponding external project.
   * @param modelsProvider is a modifiable model of current modification session.
   * @return found external project coordinates.
   */
  @Nullable ProjectCoordinate findProjectId(Module module, IdeModifiableModelsProvider modelsProvider);
}

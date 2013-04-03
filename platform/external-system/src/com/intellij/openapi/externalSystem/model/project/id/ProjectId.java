package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:34 PM
 */
public class ProjectId extends AbstractExternalEntityId {

  public ProjectId(@NotNull ProjectSystemId owner) {
    super(ProjectEntityType.PROJECT, owner);
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    if (ProjectSystemId.IDE.equals(getOwner())) {
      return ideProject;
    }
    else {
      return services.getChangesModel().getExternalProject(getOwner(), ideProject);
    }
  }
}

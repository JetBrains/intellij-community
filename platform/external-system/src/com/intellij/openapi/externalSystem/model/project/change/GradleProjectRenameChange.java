package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Describes project name change.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:54 PM
 */
public class GradleProjectRenameChange extends AbstractConflictingPropertyChange<String> {

  public GradleProjectRenameChange(@NotNull String gradleName, @NotNull String intellijName) {
    super(new ProjectId(ProjectSystemId.IDE), ExternalSystemBundle.message("change.project.name"), gradleName, intellijName);
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}

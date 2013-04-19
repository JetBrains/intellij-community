package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectId;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:30 PM
 */
public class ProjectData extends AbstractNamedData {

  private static final long serialVersionUID = 1L;

  @NotNull private String myProjectFileDirectoryPath;

  public ProjectData(@NotNull ProjectSystemId owner,
                     @NotNull String projectFileDirectoryPath)
  {
    super(owner, "unnamed");
    myProjectFileDirectoryPath = ExternalSystemUtil.toCanonicalPath(projectFileDirectoryPath);
  }

  @NotNull
  @Override
  public ProjectEntityId getId(@Nullable DataNode<?> dataNode) {
    return new ProjectId(getOwner());
  }

  @NotNull
  public String getProjectFileDirectoryPath() {
    return myProjectFileDirectoryPath;
  }

  public void setProjectFileDirectoryPath(@NotNull String projectFileDirectoryPath) {
    myProjectFileDirectoryPath = ExternalSystemUtil.toCanonicalPath(projectFileDirectoryPath);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myProjectFileDirectoryPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ProjectData project = (ProjectData)o;

    if (!myProjectFileDirectoryPath.equals(project.myProjectFileDirectoryPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s project '%s'", getOwner().toString().toLowerCase(), getName());
  }
}

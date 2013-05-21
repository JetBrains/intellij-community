package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:30 PM
 */
public class ProjectData extends AbstractNamedData implements ExternalConfigPathAware {

  private static final long serialVersionUID = 1L;

  @NotNull private final String myLinkedExternalProjectPath;

  @NotNull private String myIdeProjectFileDirectoryPath;

  public ProjectData(@NotNull ProjectSystemId owner,
                     @NotNull String ideProjectFileDirectoryPath,
                     @NotNull String linkedExternalProjectPath)
  {
    super(owner, "unnamed");
    myLinkedExternalProjectPath = linkedExternalProjectPath;
    myIdeProjectFileDirectoryPath = ExternalSystemApiUtil.toCanonicalPath(ideProjectFileDirectoryPath);
  }

  @NotNull
  public String getIdeProjectFileDirectoryPath() {
    return myIdeProjectFileDirectoryPath;
  }

  public void setIdeProjectFileDirectoryPath(@NotNull String ideProjectFileDirectoryPath) {
    myIdeProjectFileDirectoryPath = ExternalSystemApiUtil.toCanonicalPath(ideProjectFileDirectoryPath);
  }

  @NotNull
  public String getLinkedExternalProjectPath() {
    return myLinkedExternalProjectPath;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myIdeProjectFileDirectoryPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ProjectData project = (ProjectData)o;

    if (!myIdeProjectFileDirectoryPath.equals(project.myIdeProjectFileDirectoryPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s project '%s'", getOwner().toString().toLowerCase(), getName());
  }
}

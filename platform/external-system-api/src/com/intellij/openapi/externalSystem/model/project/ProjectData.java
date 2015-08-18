package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 8/1/11 1:30 PM
 */
public class ProjectData extends AbstractNamedData implements ExternalConfigPathAware, Identifiable {

  private static final long serialVersionUID = 1L;

  @NotNull private final String myLinkedExternalProjectPath;

  @NotNull private String myIdeProjectFileDirectoryPath;
  @Nullable private String myDescription;
  private String myGroup;
  private String myVersion;

  @Deprecated
  public ProjectData(@NotNull ProjectSystemId owner,
                     @NotNull String ideProjectFileDirectoryPath,
                     @NotNull String linkedExternalProjectPath) {
    super(owner, "unnamed");
    myLinkedExternalProjectPath = ExternalSystemApiUtil.toCanonicalPath(linkedExternalProjectPath);
    myIdeProjectFileDirectoryPath = ExternalSystemApiUtil.toCanonicalPath(ideProjectFileDirectoryPath);
  }

  public ProjectData(@NotNull ProjectSystemId owner,
                     @NotNull String externalName,
                     @NotNull String ideProjectFileDirectoryPath,
                     @NotNull String linkedExternalProjectPath) {
    super(owner, externalName);
    myLinkedExternalProjectPath = ExternalSystemApiUtil.toCanonicalPath(linkedExternalProjectPath);
    myIdeProjectFileDirectoryPath = ExternalSystemApiUtil.toCanonicalPath(ideProjectFileDirectoryPath);
  }

  @Deprecated
  @Override
  public void setName(@NotNull String name) {
    super.setExternalName(name);
    super.setInternalName(name);
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
    return String.format("%s project '%s'", getOwner().toString().toLowerCase(), getExternalName());
  }

  @NotNull
  @Override
  public String getId() {
    return "";
  }

  public String getGroup() {
    return myGroup;
  }

  public void setGroup(String group) {
    myGroup = group;
  }

  public String getVersion() {
    return myVersion;
  }

  public void setVersion(String version) {
    myVersion = version;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
  }
}

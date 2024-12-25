// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// cannot be final because of ModuleModelDataServiceTest - mock is created for class
public class ProjectData extends AbstractNamedData implements ExternalConfigPathAware, Identifiable {
  private final @NotNull String linkedExternalProjectPath;

  private @NotNull String ideProjectFileDirectoryPath;
  private @Nullable String description;

  private String group;
  private String version;
  private String ideGrouping;

  @PropertyMapping({"owner", "externalName", "ideProjectFileDirectoryPath", "linkedExternalProjectPath"})
  public ProjectData(@NotNull ProjectSystemId owner,
                     @NotNull String externalName,
                     @NotNull String ideProjectFileDirectoryPath,
                     @NotNull String linkedExternalProjectPath) {
    super(owner, externalName);

    this.linkedExternalProjectPath = ExternalSystemApiUtil.toCanonicalPath(linkedExternalProjectPath);
    this.ideProjectFileDirectoryPath = ExternalSystemApiUtil.toCanonicalPath(ideProjectFileDirectoryPath);
  }

  public @NotNull String getIdeProjectFileDirectoryPath() {
    return ideProjectFileDirectoryPath;
  }

  public void setIdeProjectFileDirectoryPath(@NotNull String ideProjectFileDirectoryPath) {
    this.ideProjectFileDirectoryPath = ExternalSystemApiUtil.toCanonicalPath(ideProjectFileDirectoryPath);
  }

  @Override
  public @NotNull String getLinkedExternalProjectPath() {
    return linkedExternalProjectPath;
  }

  public @Nullable String getIdeGrouping() {
    return ideGrouping;
  }

  public void setIdeGrouping(@Nullable String ideGrouping) {
    this.ideGrouping = ideGrouping;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + ideProjectFileDirectoryPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ProjectData project = (ProjectData)o;

    if (!ideProjectFileDirectoryPath.equals(project.ideProjectFileDirectoryPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s project '%s'", StringUtil.toLowerCase(getOwner().toString()), getExternalName());
  }

  @Override
  public @NotNull String getId() {
    return "";
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public @Nullable @NlsSafe String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }
}

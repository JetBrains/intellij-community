// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Unique key which encapsulates information about target ide and external projects.
 * <p/>
 * Thread-safe.
 */
public class IntegrationKey {

  private final @NotNull String          myIdeProjectName;
  private final @NotNull String          myIdeProjectLocationHash;
  private final @NotNull ProjectSystemId myExternalSystemId;
  private final @NotNull String          myExternalProjectConfigPath;

  public IntegrationKey(@NotNull Project ideProject, @NotNull ProjectSystemId externalSystemId, @NotNull String externalProjectConfigPath) {
    this(ideProject.getName(), ideProject.getLocationHash(), externalSystemId, externalProjectConfigPath);
  }

  public IntegrationKey(@NotNull String ideProjectName,
                        @NotNull String ideProjectLocationHash,
                        @NotNull ProjectSystemId externalSystemId,
                        @NotNull String externalProjectConfigPath)
  {
    myIdeProjectName = ideProjectName;
    myIdeProjectLocationHash = ideProjectLocationHash;
    myExternalSystemId = externalSystemId;
    myExternalProjectConfigPath = externalProjectConfigPath;
  }

  public @NotNull String getIdeProjectName() {
    return myIdeProjectName;
  }

  public @NotNull String getIdeProjectLocationHash() {
    return myIdeProjectLocationHash;
  }

  public @NotNull ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  public @NotNull String getExternalProjectConfigPath() {
    return myExternalProjectConfigPath;
  }

  @Override
  public int hashCode() {
    int result = myIdeProjectName.hashCode();
    result = 31 * result + myIdeProjectLocationHash.hashCode();
    result = 31 * result + myExternalSystemId.hashCode();
    result = 31 * result + myExternalProjectConfigPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IntegrationKey key = (IntegrationKey)o;

    if (!myExternalSystemId.equals(key.myExternalSystemId)) return false;
    if (!myIdeProjectLocationHash.equals(key.myIdeProjectLocationHash)) return false;
    if (!myIdeProjectName.equals(key.myIdeProjectName)) return false;
    if (!myExternalProjectConfigPath.equals(key.myExternalProjectConfigPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s project '%s'", StringUtil.toLowerCase(myExternalSystemId.toString()), myIdeProjectName);
  }
}

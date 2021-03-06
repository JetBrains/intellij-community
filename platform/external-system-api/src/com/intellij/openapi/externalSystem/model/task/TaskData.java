// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.task;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container for external system task information.
 */
public final class TaskData extends AbstractExternalEntityData implements ExternalConfigPathAware, Comparable<TaskData> {
  @NotNull private final String name;
  @Nullable private final String description;
  @NotNull private final String linkedExternalProjectPath;
  @Nullable private String group;
  @Nullable private String type;
  private boolean inherited;
  private boolean isTest;

  @PropertyMapping({"owner", "name", "linkedExternalProjectPath", "description"})
  public TaskData(@NotNull ProjectSystemId owner,
                  @NotNull String name,
                  @NotNull String linkedExternalProjectPath,
                  @Nullable String description) {
    super(owner);
    this.name = name;
    this.linkedExternalProjectPath = linkedExternalProjectPath;
    this.description = description;
  }

  @NotNull
  public @NlsSafe String getName() {
    return name;
  }

  @Override
  @NotNull
  public String getLinkedExternalProjectPath() {
    return linkedExternalProjectPath;
  }

  @Nullable
  public @NlsSafe String getDescription() {
    return description;
  }

  @Nullable
  public String getGroup() {
    return group;
  }

  public void setGroup(@Nullable String group) {
    this.group = group;
  }

  @Nullable
  public String getType() {
    return type;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  public boolean isInherited() {
    return inherited;
  }

  public void setInherited(boolean inherited) {
    this.inherited = inherited;
  }

  public boolean isTest() {
    return isTest;
  }

  public void setTest(boolean test) {
    isTest = test;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + (group != null ? group.hashCode() : 0);
    result = 31 * result + linkedExternalProjectPath.hashCode();
    result = 31 * result + (inherited ? 1 : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    return result;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TaskData data = (TaskData)o;

    if (inherited != data.inherited) return false;
    if (!name.equals(data.name)) return false;
    if (group != null ? !group.equals(data.group) : data.group != null) return false;
    if (!linkedExternalProjectPath.equals(data.linkedExternalProjectPath)) return false;
    if (description != null ? !description.equals(data.description) : data.description != null) return false;

    return true;
  }

  @Override
  public int compareTo(@NotNull TaskData that) {
    return name.compareTo(that.getName());
  }

  @Override
  public @NlsSafe String toString() {
    return name;
  }
}

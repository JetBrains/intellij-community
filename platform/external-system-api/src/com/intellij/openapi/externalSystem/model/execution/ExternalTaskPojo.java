// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents {@link TaskData} at the ide side. Is required purely for IJ serialization because {@link TaskData} has only final
 * fields which are initialized at constructor and ide serialization is not capable to handle such scenario properly.
 */
public class ExternalTaskPojo implements Comparable<ExternalTaskPojo> {
  
  private @NotNull String myName;
  private @NotNull String myLinkedExternalProjectPath;
  
  private @Nullable String myDescription;

  @SuppressWarnings("UnusedDeclaration")
  public ExternalTaskPojo() {
    // Required for IJ serialization.
    this("___DUMMY___", "___DUMMY___", null);
  }

  public ExternalTaskPojo(@NotNull String name, @NotNull String linkedExternalProjectPath, @Nullable String description) {
    myName = name;
    myLinkedExternalProjectPath = linkedExternalProjectPath;
    myDescription = description;
  }

  public static @NotNull ExternalTaskPojo from(@NotNull TaskData data) {
    return new ExternalTaskPojo(data.getName(), data.getLinkedExternalProjectPath(), data.getDescription());
  }
  
  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public @Nullable @NlsSafe String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
  }

  public @NotNull String getLinkedExternalProjectPath() {
    return myLinkedExternalProjectPath;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setLinkedExternalProjectPath(@NotNull String linkedExternalProjectPath) {
    // Required for IJ serialization.
    myLinkedExternalProjectPath = linkedExternalProjectPath;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myLinkedExternalProjectPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalTaskPojo that = (ExternalTaskPojo)o;

    if (!myLinkedExternalProjectPath.equals(that.myLinkedExternalProjectPath)) return false;
    if (!myName.equals(that.myName)) return false;
    
    return true;
  }

  @Override
  public int compareTo(@NotNull ExternalTaskPojo that) {
    return myName.compareTo(that.getName());
  }

  @Override
  public String toString() {
    return myName;
  }
}

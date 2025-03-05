// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class ExternalProjectPojo implements Comparable<ExternalProjectPojo> {

  private @NotNull String myName;
  private @NotNull String myPath;

  @SuppressWarnings("UnusedDeclaration")
  public ExternalProjectPojo() {
    // Used by IJ serialization
    this("___DUMMY___", "___DUMMY___");
  }

  public ExternalProjectPojo(@NotNull String name, @NotNull String path) {
    myName = name;
    myPath = path;
  }

  public static @NotNull <T extends Named & ExternalConfigPathAware & Identifiable> ExternalProjectPojo from(@NotNull T data) {
    String projectUniqueName = StringUtil.isEmpty(data.getId()) ? data.getExternalName() : data.getId();
    return new ExternalProjectPojo(projectUniqueName, data.getLinkedExternalProjectPath());
  }

  public @NotNull String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public @NotNull String getPath() {
    return myPath;
  }

  public void setPath(@NotNull String path) {
    myPath = path;
  }

  @Override
  public int compareTo(@NotNull ExternalProjectPojo that) {
    return myName.compareTo(that.myName);
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalProjectPojo pojo = (ExternalProjectPojo)o;

    if (!myName.equals(pojo.myName)) return false;
    if (!myPath.equals(pojo.myPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return myName;
  }
}

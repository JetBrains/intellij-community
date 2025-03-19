// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ExternalModuleBuildClasspathPojo {

  private @NotNull String myPath;
  private @NotNull List<String> myEntries;

  @SuppressWarnings("UnusedDeclaration")
  public ExternalModuleBuildClasspathPojo() {
    // Used by IJ serialization
    this("___DUMMY___", Collections.emptyList());
  }

  public ExternalModuleBuildClasspathPojo(@NotNull String path, @NotNull List<String> entries) {
    myPath = path;
    myEntries = entries;
  }

  public @NotNull String getPath() {
    return myPath;
  }

  public void setPath(@NotNull String path) {
    myPath = path;
  }

  public @NotNull List<String> getEntries() {
    return myEntries;
  }

  public void setEntries(@NotNull List<String> entries) {
    myEntries = entries;
  }

  @Override
  public int hashCode() {
    int result = myEntries.hashCode();
    result = 31 * result + myPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalModuleBuildClasspathPojo pojo = (ExternalModuleBuildClasspathPojo)o;

    if (!myEntries.equals(pojo.myEntries)) return false;
    if (!myPath.equals(pojo.myPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return myPath;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public class ExternalProjectBuildClasspathPojo {

  /**
   * Common for all project modules build classpath. E.g. it can be build system SDK libraries, configured at project level.
   */
  private @NotNull List<String> myProjectBuildClasspath;
  private @NotNull Map<String, ExternalModuleBuildClasspathPojo> myModulesBuildClasspath;
  private @NotNull String myName;

  @SuppressWarnings("UnusedDeclaration")
  public ExternalProjectBuildClasspathPojo() {
    // Used by IJ serialization
    this("___DUMMY___", new ArrayList<>(), new HashMap<>());
  }

  public ExternalProjectBuildClasspathPojo(@NotNull String name,
                                           @NotNull List<String> projectBuildClasspath,
                                           @NotNull Map<String, ExternalModuleBuildClasspathPojo> modulesBuildClasspath) {
    myName = name;
    myProjectBuildClasspath = projectBuildClasspath;
    myModulesBuildClasspath = modulesBuildClasspath;
  }

  public @NotNull String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public @NotNull Map<String, ExternalModuleBuildClasspathPojo> getModulesBuildClasspath() {
    return myModulesBuildClasspath;
  }

  public void setModulesBuildClasspath(@NotNull Map<String, ExternalModuleBuildClasspathPojo> modulesBuildClasspath) {
    myModulesBuildClasspath = modulesBuildClasspath;
  }

  public @NotNull List<String> getProjectBuildClasspath() {
    return myProjectBuildClasspath;
  }

  public void setProjectBuildClasspath(@NotNull List<String> projectBuildClasspath) {
    myProjectBuildClasspath = projectBuildClasspath;
  }

  @Override
  public int hashCode() {
    int result = myModulesBuildClasspath.hashCode();
    result = 31 * result + myModulesBuildClasspath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalProjectBuildClasspathPojo pojo = (ExternalProjectBuildClasspathPojo)o;

    if (!myModulesBuildClasspath.equals(pojo.myModulesBuildClasspath)) return false;
    if (!myName.equals(pojo.myName)) return false;

    return true;
  }

  @Override
  public String toString() {
    return myName;
  }
}
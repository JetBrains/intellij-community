// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull private List<String> myProjectBuildClasspath;
  @NotNull private Map<String, ExternalModuleBuildClasspathPojo> myModulesBuildClasspath;
  @NotNull private String myName;

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

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public Map<String, ExternalModuleBuildClasspathPojo> getModulesBuildClasspath() {
    return myModulesBuildClasspath;
  }

  public void setModulesBuildClasspath(@NotNull Map<String, ExternalModuleBuildClasspathPojo> modulesBuildClasspath) {
    myModulesBuildClasspath = modulesBuildClasspath;
  }

  @NotNull
  public List<String> getProjectBuildClasspath() {
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
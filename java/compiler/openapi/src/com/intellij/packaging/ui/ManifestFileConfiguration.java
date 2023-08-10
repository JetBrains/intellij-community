// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ManifestFileConfiguration {
  private final boolean myWritable;
  private List<String> myClasspath = new ArrayList<>();
  private String myMainClass;
  private String myManifestFilePath;

  public ManifestFileConfiguration(@NotNull ManifestFileConfiguration configuration) {
    myWritable = configuration.isWritable();
    myClasspath.addAll(configuration.getClasspath());
    myMainClass = configuration.getMainClass();
    myManifestFilePath = configuration.getManifestFilePath();
  }

  public ManifestFileConfiguration(@NotNull String manifestFilePath, @Nullable List<String> classpath, @Nullable String mainClass, boolean isWritable) {
    myWritable = isWritable;
    if (classpath != null) {
      myClasspath.addAll(classpath);
    }
    myMainClass = mainClass;
    myManifestFilePath = manifestFilePath;
  }

  public List<String> getClasspath() {
    return myClasspath;
  }

  public boolean isWritable() {
    return myWritable;
  }

  public void setClasspath(List<String> classpath) {
    myClasspath = classpath;
  }

  public String getMainClass() {
    return myMainClass;
  }

  public void setMainClass(String mainClass) {
    myMainClass = mainClass;
  }

  public String getManifestFilePath() {
    return myManifestFilePath;
  }

  public void setManifestFilePath(String manifestFilePath) {
    myManifestFilePath = manifestFilePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof ManifestFileConfiguration that && myClasspath.equals(that.myClasspath) &&
           Objects.equals(myMainClass, that.myMainClass) && Objects.equals(myManifestFilePath, that.myManifestFilePath);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }

  public void addToClasspath(List<String> classpath) {
    for (String path : classpath) {
      if (!myClasspath.contains(path)) {
        myClasspath.add(path);
      }
    }
  }
}

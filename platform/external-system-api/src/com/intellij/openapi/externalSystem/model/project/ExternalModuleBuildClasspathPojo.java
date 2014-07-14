/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 1/14/14
 */
public class ExternalModuleBuildClasspathPojo {

  @NotNull private List<String> myEntries;
  @NotNull private String myPath;

  @SuppressWarnings("UnusedDeclaration")
  public ExternalModuleBuildClasspathPojo() {
    // Used by IJ serialization
    this("___DUMMY___", ContainerUtil.<String>newArrayList());
  }

  public ExternalModuleBuildClasspathPojo(@NotNull String path, @NotNull List<String> entries) {
    myPath = path;
    myEntries = entries;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  public void setPath(@NotNull String path) {
    myPath = path;
  }

  @NotNull
  public List<String> getEntries() {
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

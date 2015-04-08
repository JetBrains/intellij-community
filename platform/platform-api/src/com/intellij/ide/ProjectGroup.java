/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class ProjectGroup {
  private @NotNull String myName = "";
  private Set<String> myProjects = new HashSet<String>();
  private boolean myExpanded = false;

  public ProjectGroup(@NotNull String name) {
    myName = name;
  }

  public ProjectGroup() {}

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public void addProject(String path) {
    myProjects.add(path);
  }

  public Set<String> getProjects() {
    return Collections.unmodifiableSet(myProjects);
  }

  public void removeProject(String path) {
    myProjects.remove(path);
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public void setExpanded(boolean expanded) {
    myExpanded = expanded;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectGroup group = (ProjectGroup)o;

    if (!myName.equals(group.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }
}

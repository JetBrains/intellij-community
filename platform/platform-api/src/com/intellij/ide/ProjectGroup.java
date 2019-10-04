// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class ProjectGroup {
  private @NotNull String myName = "";
  private String myProjectPaths = "";
  private boolean myExpanded = false;
  private boolean myTutorials = false; //used in different places, i.e. closing tutorials group should hide all nested items too

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

  //do not remove. bean getter
  public String getProjectPaths() {
    return myProjectPaths;
  }

  //do not remove. bean setter
  public void setProjectPaths(String projectPaths) {
    myProjectPaths = projectPaths;
  }

  public void addProject(@SystemIndependent String path) {
    List<String> projects = new ArrayList<>(StringUtil.split(myProjectPaths, File.pathSeparator));
    projects.add(path);
    save(projects);
  }

  public boolean markProjectFirst(@SystemIndependent String path) {
    if (!myProjectPaths.contains(path)) {
      return false;
    }

    List<String> existing = StringUtil.split(myProjectPaths, File.pathSeparator);
    int index = existing.indexOf(path);
    if (index <= 0) {
      return false;
    }

    List<String> projects = new ArrayList<>(existing.size());
    projects.add(path);
    projects.addAll(existing.subList(0, index));
    projects.addAll(existing.subList(index + 1, existing.size()));
    save(projects);
    return true;
  }

  private void save(List<String> projects) {
    myProjectPaths = StringUtil.join(projects, File.pathSeparator);
  }

  public List<String> getProjects() {
    return new ArrayList<>(new HashSet<>(StringUtil.split(myProjectPaths, File.pathSeparator)));
  }

  public boolean removeProject(@SystemIndependent String path) {
    List<String> projects = StringUtil.split(myProjectPaths, File.pathSeparator);
    if (!projects.remove(path)) {
      return false;
    }

    save(projects);
    return true;
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public void setExpanded(boolean expanded) {
    myExpanded = expanded;
  }

  public boolean isTutorials() {
    return myTutorials;
  }

  public void setTutorials(boolean tutorials) {
    myTutorials = tutorials;
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

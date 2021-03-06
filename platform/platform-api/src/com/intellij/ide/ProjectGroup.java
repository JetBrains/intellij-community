// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class ProjectGroup {
  private @NotNull @NlsSafe String myName = "";
  private String myProjectPaths;
  private List<String> myProjects = new ArrayList<>();
  private boolean myExpanded = false;
  //used in different places, i.e. closing tutorials group should hide all nested items too
  private boolean myTutorials = false;

  public ProjectGroup(@NotNull @NlsSafe String name) {
    myName = name;
  }

  public ProjectGroup() {}

  @NotNull
  public @NlsSafe String getName() {
    return myName;
  }

  public void setName(@NotNull @NlsSafe String name) {
    myName = name;
  }

  public void setProjects(List<String> projects) {
    myProjects = projects;
  }

  //do not remove. bean getter
  public String getProjectPaths() {
    return null;
  }

  //do not remove. bean setter
  public void setProjectPaths(String projectPaths) {
    ArrayList<String> paths = new ArrayList<>(StringUtil.split(projectPaths, File.pathSeparator));
    paths.forEach(this::addProject);
  }

  public void addProject(@SystemIndependent String path) {
    if (!myProjects.contains(path)) {
      myProjects.add(path);
    }
  }

  public boolean markProjectFirst(@SystemIndependent String path) {
    if (!myProjects.contains(path)) {
      return false;
    }

    List<String> existing = new ArrayList<>(myProjects);
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

  private void save(@NotNull List<String> projects) {
    //myProjectPaths = String.join(File.pathSeparator, projects);
    myProjects = projects;
  }

  @NotNull
  public List<String> getProjects() {
    return myProjects;
  }

  public boolean removeProject(@SystemIndependent String path) {
    return myProjects.remove(path);
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

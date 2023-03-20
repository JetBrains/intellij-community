// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Konstantin Bulenkov
 */
public final class ProjectGroup implements ModificationTracker {
  private @NotNull @NlsSafe String myName = "";
  private String myProjectPaths;
  private List<String> myProjects = new ArrayList<>();
  private boolean myExpanded = false;
  //used in different places, i.e. closing tutorials group should hide all nested items too
  private boolean myTutorials = false;
  private boolean myBottomGroup = false;

  // To store ProjectGroup in persistance state when some fields were changed
  private final AtomicInteger modCounter = new AtomicInteger();

  public ProjectGroup(@NotNull @NlsSafe String name) {
    myName = name;
  }

  public ProjectGroup() { }

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
    modCounter.incrementAndGet();
  }

  public void addProject(@SystemIndependent String path) {
    if (!myProjects.contains(path)) {
      myProjects.add(path);
      modCounter.incrementAndGet();
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
    modCounter.incrementAndGet();
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
    modCounter.incrementAndGet();
    return myProjects.remove(path);
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public void setExpanded(boolean expanded) {
    myExpanded = expanded;
    modCounter.incrementAndGet();
  }

  public boolean isTutorials() {
    return myTutorials;
  }

  public void setTutorials(boolean tutorials) {
    myTutorials = tutorials;
    modCounter.incrementAndGet();
  }

  public boolean isBottomGroup() {
    return myBottomGroup;
  }

  public void setBottomGroup(boolean bottomGroup) {
    myBottomGroup = bottomGroup;
    modCounter.incrementAndGet();
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

  @Transient
  @Override
  public long getModificationCount() {
    return modCounter.get();
  }
}

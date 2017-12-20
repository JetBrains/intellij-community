/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.externalDependencies.ProjectExternalDependency;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
@State(name = "ExternalDependencies", storages = @Storage("externalDependencies.xml"))
public class ExternalDependenciesManagerImpl extends ExternalDependenciesManager implements PersistentStateComponent<ExternalDependenciesManagerImpl.ExternalDependenciesState> {
  private final Project myProject;

  public ExternalDependenciesManagerImpl(Project project) {
    myProject = project;
  }

  private static final Comparator<ProjectExternalDependency> DEPENDENCY_COMPARATOR = (o1, o2) -> {
    int i = o1.getClass().getSimpleName().compareToIgnoreCase(o2.getClass().getSimpleName());
    if (i != 0) return i;
    //noinspection unchecked
    return ((Comparable)o1).compareTo(o2);
  };
  private List<ProjectExternalDependency> myDependencies = new ArrayList<>();
  
  @NotNull
  @Override
  public <T extends ProjectExternalDependency> List<T> getDependencies(@NotNull Class<T> aClass) {
    return ContainerUtil.collect(myDependencies.iterator(), FilteringIterator.instanceOf(aClass));
  }

  @NotNull
  @Override
  public List<ProjectExternalDependency> getAllDependencies() {
    return Collections.unmodifiableList(myDependencies);
  }

  @Override
  public void setAllDependencies(@NotNull List<ProjectExternalDependency> dependencies) {
    myDependencies.clear();
    myDependencies.addAll(dependencies);
    Collections.sort(myDependencies, DEPENDENCY_COMPARATOR);
  }

  @Nullable
  @Override
  public ExternalDependenciesState getState() {
    ExternalDependenciesState state = new ExternalDependenciesState();
    for (ProjectExternalDependency dependency : myDependencies) {
      state.myDependencies.add(new DependencyOnPluginState((DependencyOnPlugin)dependency));
    }
    return state;
  }

  @Override
  public void loadState(ExternalDependenciesState state) {
    ArrayList<ProjectExternalDependency> oldDependencies = new ArrayList<>(myDependencies);
    myDependencies.clear();
    for (DependencyOnPluginState dependency : state.myDependencies) {
      myDependencies.add(new DependencyOnPlugin(dependency.myId, dependency.myMinVersion, dependency.myMaxVersion, dependency.myChannel));
    }
    if (!oldDependencies.equals(myDependencies) && !myDependencies.isEmpty()) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
        (DumbAwareRunnable)() -> CheckRequiredPluginsActivity.runCheck(myProject));
    }
  }

  public static class ExternalDependenciesState {
    @Property(surroundWithTag = false)
    @XCollection
    public List<DependencyOnPluginState> myDependencies = new ArrayList<>();
  }
}

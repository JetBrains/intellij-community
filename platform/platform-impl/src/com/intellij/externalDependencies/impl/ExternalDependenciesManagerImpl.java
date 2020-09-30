// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.externalDependencies.ProjectExternalDependency;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@State(name = "ExternalDependencies", storages = @Storage("externalDependencies.xml"))
public final class ExternalDependenciesManagerImpl extends ExternalDependenciesManager implements PersistentStateComponent<ExternalDependenciesManagerImpl.ExternalDependenciesState> {
  private final Project myProject;

  ExternalDependenciesManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  private static final Comparator<ProjectExternalDependency> DEPENDENCY_COMPARATOR = (o1, o2) -> {
    int i = o1.getClass().getSimpleName().compareToIgnoreCase(o2.getClass().getSimpleName());
    if (i != 0) return i;
    //noinspection unchecked
    return ((Comparable<ProjectExternalDependency>)o1).compareTo(o2);
  };

  private final List<ProjectExternalDependency> myDependencies = new ArrayList<>();

  @NotNull
  @Override
  public <T extends ProjectExternalDependency> List<T> getDependencies(@NotNull Class<T> aClass) {
    return ContainerUtil.filterIsInstance(myDependencies, aClass);
  }

  @NotNull
  @Override
  public List<ProjectExternalDependency> getAllDependencies() {
    return Collections.unmodifiableList(myDependencies);
  }

  @Override
  public void setAllDependencies(@NotNull List<? extends ProjectExternalDependency> dependencies) {
    myDependencies.clear();
    myDependencies.addAll(dependencies);
    myDependencies.sort(DEPENDENCY_COMPARATOR);
  }

  @NotNull
  @Override
  public ExternalDependenciesState getState() {
    ExternalDependenciesState state = new ExternalDependenciesState();
    for (DependencyOnPlugin dependency : getDependencies(DependencyOnPlugin.class)) {
      state.myDependencies.add(new DependencyOnPluginState(dependency));
    }
    return state;
  }

  @Override
  public void loadState(@NotNull ExternalDependenciesState state) {
    List<ProjectExternalDependency> oldDependencies = new ArrayList<>(myDependencies);
    myDependencies.clear();
    for (DependencyOnPluginState dependency : state.myDependencies) {
      myDependencies.add(new DependencyOnPlugin(dependency.myId, dependency.myMinVersion, dependency.myMaxVersion));
    }

    if (!oldDependencies.equals(myDependencies) && !myDependencies.isEmpty() && !ApplicationManager.getApplication().isUnitTestMode()) {
      // may be executed immediately if start-up activities are already passed, but must be not performed in loadState
      NonUrgentExecutor.getInstance().execute(() -> {
        StartupManager.getInstance(myProject).runAfterOpened(() -> CheckRequiredPluginsActivity.runCheck(myProject, this));
      });
    }
  }

  public static final class ExternalDependenciesState {
    @Property(surroundWithTag = false)
    @XCollection
    public final List<DependencyOnPluginState> myDependencies = new ArrayList<>();
  }
}

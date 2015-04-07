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
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.externalDependencies.ProjectExternalDependency;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
@State(
  name = "ExternalDependencies",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/externalDependencies.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class ExternalDependenciesManagerImpl extends ExternalDependenciesManager implements PersistentStateComponent<ExternalDependenciesManagerImpl.ExternalDependenciesState> {
  private static final Comparator<ProjectExternalDependency> DEPENDENCY_COMPARATOR = new Comparator<ProjectExternalDependency>() {
    @Override
    public int compare(ProjectExternalDependency o1, ProjectExternalDependency o2) {
      int i = o1.getClass().getSimpleName().compareToIgnoreCase(o2.getClass().getSimpleName());
      if (i != 0) return i;
      //noinspection unchecked
      return ((Comparable)o1).compareTo(o2);
    }
  };
  private List<ProjectExternalDependency> myDependencies = new ArrayList<ProjectExternalDependency>();
  
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
    myDependencies.clear();
    for (DependencyOnPluginState dependency : state.myDependencies) {
      myDependencies.add(new DependencyOnPlugin(dependency.myId, dependency.myMinVersion, dependency.myMaxVersion));
    }
  }

  public static class ExternalDependenciesState {
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public List<DependencyOnPluginState> myDependencies = new ArrayList<DependencyOnPluginState>();
  }
}

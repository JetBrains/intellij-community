/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.externalSystem.model.serialization.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds local project-level external system-related settings (should be kept at the '*.iws' or 'workspace.xml').
 * <p/>
 * For example, we don't want to store recent tasks list at common external system settings, hence, that data
 * is kept at user-local settings (workspace settings).
 * <p/>
 * <b>Note:</b> non-abstract sub-classes of this class are expected to be marked by {@link State} annotation configured
 * to be stored under a distinct name at a workspace file.
 * 
 * @author Denis Zhdanov
 * @since 4/4/13 4:51 PM
 */
public abstract class AbstractExternalSystemLocalSettings {
  private static final boolean PRESERVE_EXPAND_STATE
    = !SystemProperties.getBooleanProperty("external.system.forget.expand.nodes.state", false);

  private final AtomicReference<Map<String/*tree path*/, Boolean/*expanded*/>>             myExpandStates
                                                                                                               =
    new AtomicReference<Map<String, Boolean>>(new HashMap<String, Boolean>());
  private final AtomicReference<List<ExternalTaskPojo>>                                    myRecentTasks       =
    new AtomicReference<List<ExternalTaskPojo>>(
      ContainerUtilRt.<ExternalTaskPojo>newArrayList()
    );
  private final AtomicReference<Map<ExternalProjectPojo, Collection<ExternalProjectPojo>>> myAvailableProjects =
    new AtomicReference<Map<ExternalProjectPojo, Collection<ExternalProjectPojo>>>(
      ContainerUtilRt.<ExternalProjectPojo, Collection<ExternalProjectPojo>>newHashMap()
    );
  private final AtomicReference<Map<String, Collection<ExternalTaskPojo>>>                 myAvailableTasks    =
    new AtomicReference<Map<String, Collection<ExternalTaskPojo>>>(
      ContainerUtilRt.<String, Collection<ExternalTaskPojo>>newHashMap()
    );

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public Map<String, Boolean> getExpandStates() { // Necessary for the serialization.
    return myExpandStates.get();
  }

  @NotNull
  public Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> getAvailableProjects() {
    return myAvailableProjects.get();
  }

  public void setAvailableProjects(@NotNull Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> projects) {
    myAvailableProjects.set(projects);
  }

  @NotNull
  public Map<String, Collection<ExternalTaskPojo>> getAvailableTasks() {
    return myAvailableTasks.get();
  }

  public void setAvailableTasks(@NotNull Map<String, Collection<ExternalTaskPojo>> tasks) {
    myAvailableTasks.set(tasks);
  }

  @NotNull
  public List<ExternalTaskPojo> getRecentTasks() {
    return myRecentTasks.get();
  }

  public void setRecentTasks(@NotNull List<ExternalTaskPojo> tasks) {
    myRecentTasks.set(tasks);
  }

  public void fillState(@NotNull State state) {
    if (PRESERVE_EXPAND_STATE) {
      state.tasksExpandState = myExpandStates.get();
    }
    else {
      state.tasksExpandState = Collections.emptyMap();
    }
    state.recentTasks = myRecentTasks.get();
    state.availableProjects = myAvailableProjects.get();
    state.availableTasks = myAvailableTasks.get();
  }

  public void loadState(@NotNull State state) {
    setIfNotNull(myExpandStates, state.tasksExpandState);
    setIfNotNull(myAvailableProjects, state.availableProjects);
    setIfNotNull(myAvailableTasks, state.availableTasks);
    if (state.recentTasks != null) {
      List<ExternalTaskPojo> recentTasks = myRecentTasks.get();
      recentTasks.clear();
      recentTasks.addAll(state.recentTasks);
    }
  }

  private static <K, V> void setIfNotNull(@NotNull AtomicReference<Map<K, V>> ref, @Nullable Map<K, V> candidate) {
    if (candidate != null) {
      Map<K, V> map = ref.get();
      map.clear();
      map.putAll(candidate);
    }
  }

  public static class State {
    public Map<String, Boolean>                                        tasksExpandState  = ContainerUtilRt.newHashMap();
    public List<ExternalTaskPojo>                                      recentTasks       = ContainerUtilRt.newArrayList();
    public Map<ExternalProjectPojo, Collection<ExternalProjectPojo>>   availableProjects = ContainerUtilRt.newHashMap();
    public Map<String/* project name */, Collection<ExternalTaskPojo>> availableTasks    = ContainerUtilRt.newHashMap();
  }
}

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

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskDescriptor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private final AtomicReference<Map<String/*tree path*/, Boolean/*expanded*/>> myExpandStates
    = new AtomicReference<Map<String, Boolean>>(new HashMap<String, Boolean>());
  private final AtomicReference<Map<String, List<ExternalSystemTaskDescriptor>>> myRecentTasks =
    new AtomicReference<Map<String, List<ExternalSystemTaskDescriptor>>>(
      ContainerUtilRt.<String, List<ExternalSystemTaskDescriptor>>newHashMap()
    );
  private final AtomicReference<Map<String, List<ExternalSystemTaskDescriptor>>> myAvailableTasks =
    new AtomicReference<Map<String, List<ExternalSystemTaskDescriptor>>>(
      ContainerUtilRt.<String, List<ExternalSystemTaskDescriptor>>newHashMap()
    );

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public Map<String, Boolean> getExpandStates() { // Necessary for the serialization.
    return myExpandStates.get();
  }

  @NotNull
  public Map<String, List<ExternalSystemTaskDescriptor>> getAvailableTasks() {
    return myAvailableTasks.get();
  }

  @NotNull
  public Map<String, List<ExternalSystemTaskDescriptor>> getRecentTasks() {
    return myRecentTasks.get();
  }

  public void fillState(@NotNull State state) {
    if (PRESERVE_EXPAND_STATE) {
      state.tasksExpandState = myExpandStates.get();
    }
    else {
      state.tasksExpandState = Collections.emptyMap();
    }
    state.recentTasks = myRecentTasks.get();
    state.availableTasks = myAvailableTasks.get();
  }

  public void loadState(@NotNull State state) {
    setIfNotNull(myExpandStates, state.tasksExpandState);
    setIfNotNull(myRecentTasks, state.recentTasks);
    setIfNotNull(myAvailableTasks, state.availableTasks);
  }

  private static <K, V> void setIfNotNull(@NotNull AtomicReference<Map<K, V>> ref, @Nullable Map<K, V> candidate) {
    if (candidate != null) {
      Map<K, V> map = ref.get();
      map.clear();
      map.putAll(candidate);
    }
  }
  
  public static class State {
    public Map<String, Boolean>                                              tasksExpandState = ContainerUtilRt.newHashMap();
    public Map<String/* project name */, List<ExternalSystemTaskDescriptor>> recentTasks      = ContainerUtilRt.newHashMap();
    public Map<String/* project name */, List<ExternalSystemTaskDescriptor>> availableTasks   = ContainerUtilRt.newHashMap();
  }
}

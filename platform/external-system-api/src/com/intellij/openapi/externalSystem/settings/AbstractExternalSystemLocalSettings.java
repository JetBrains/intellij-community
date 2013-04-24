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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskDescriptor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds local project-level gradle-related settings (should be kept at the '*.iws' or 'workspace.xml').
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
public abstract class AbstractExternalSystemLocalSettings<S extends AbstractExternalSystemLocalSettings<S>>
  implements PersistentStateComponent<S>
{
  private static final boolean PRESERVE_EXPAND_STATE
    = !SystemProperties.getBooleanProperty("external.system.forget.expand.nodes.state", false);

  /** Holds changes confirmed by the end-user. */
  private final AtomicReference<Map<String/*tree path*/, Boolean/*expanded*/>> myExpandStates
    = new AtomicReference<Map<String, Boolean>>(new HashMap<String, Boolean>());
  /** @see #getWorkingExpandStates() */
  private final AtomicReference<Map<String/*tree path*/, Boolean/*expanded*/>> myWorkingExpandStates
    = new AtomicReference<Map<String, Boolean>>(new HashMap<String, Boolean>());

  private final AtomicReference<List<ExternalSystemTaskDescriptor>> myRecentTasks    =
    new AtomicReference<List<ExternalSystemTaskDescriptor>>(ContainerUtilRt.<ExternalSystemTaskDescriptor>newArrayList());
  private final AtomicReference<Collection<ExternalSystemTaskDescriptor>>   myAvailableTasks =
    new AtomicReference<Collection<ExternalSystemTaskDescriptor>>(ContainerUtilRt.<ExternalSystemTaskDescriptor>newArrayList());

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public S getState() {
    myExpandStates.get().clear();
    if (PRESERVE_EXPAND_STATE) {
      myExpandStates.get().putAll(myWorkingExpandStates.get());
    }
    return (S)this;
  }

  @Override
  public void loadState(S state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public Map<String, Boolean> getExpandStates() { // Necessary for the serialization.
    return myExpandStates.get();
  }

  /**
   * It's possible to configure the gradle integration to not persist 'expand states' (see {@link #PRESERVE_EXPAND_STATE}).
   * <p/>
   * However, we want the state to be saved during the single IDE session even if we don't want to persist it between the
   * different sessions.
   * <p/>
   * This method allows to retrieve that 'non-persistent state'.
   *
   * @return project structure changes tree nodes 'expand state' to use
   */
  @NotNull
  public Map<String, Boolean> getWorkingExpandStates() {
    return myWorkingExpandStates.get();
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setExpandStates(@Nullable Map<String, Boolean> state) { // Necessary for the serialization.
    if (state != null) {
      myExpandStates.get().putAll(state);
      myWorkingExpandStates.get().putAll(state);
    }
  }

  @NotNull
  public Collection<ExternalSystemTaskDescriptor> getAvailableTasks() {
    return myAvailableTasks.get();
  }

  public void setAvailableTasks(@NotNull Collection<ExternalSystemTaskDescriptor> taskNames) {
    myAvailableTasks.set(taskNames);
  }

  @NotNull
  public List<ExternalSystemTaskDescriptor> getRecentTasks() {
    return myRecentTasks.get();
  }

  public void setRecentTasks(@NotNull List<ExternalSystemTaskDescriptor> taskNames) {
    myRecentTasks.set(taskNames);
  }
}

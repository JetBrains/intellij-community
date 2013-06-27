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

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.service.DisposableExternalSystemService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * @author Denis Zhdanov
 * @since 4/4/13 4:14 PM
 */
public class ExternalSystemSettingsManager implements DisposableExternalSystemService {

  @NotNull private final NotNullLazyValue<Holder> myHolder = new NotNullLazyValue<Holder>() {
    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    protected Holder compute() {
      Holder result = new Holder();
      for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemManager.EP_NAME.getExtensions()) {
        result.register(manager);
      }
      return result;
    }
  };

  @SuppressWarnings("unchecked")
  @NotNull
  public AbstractExternalSystemSettings getSettings(
    @NotNull Project project,
    @NotNull ProjectSystemId externalSystemId) throws IllegalArgumentException
  {
    Holder holder = myHolder.getValue();
    Function<Project, ? extends AbstractExternalSystemSettings<?, ?, ?>> provider = holder.settingsProviders.get(externalSystemId);
    if (provider == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve settings for external system with id '%s'. Reason: no such system is registered. Known systems: %s",
        externalSystemId, holder.settingsProviders.keySet()
      ));
    }
    return provider.fun(project);
  }

  @SuppressWarnings("unchecked")
  public <S extends AbstractExternalSystemLocalSettings> S getLocalSettings(@NotNull Project project,
                                                                            @NotNull ProjectSystemId externalSystemId)
    throws IllegalArgumentException
  {
    Holder holder = myHolder.getValue();
    Function<Project, ? extends AbstractExternalSystemLocalSettings> provider = holder.localSettingsProviders.get(externalSystemId);
    if (provider == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve local settings for external system with id '%s'. Reason: no such system is registered. Known systems: %s",
        externalSystemId, holder.localSettingsProviders.keySet()
      ));
    }
    return (S)provider.fun(project);
  }
  
  @SuppressWarnings("unchecked")
  public <S extends ExternalSystemExecutionSettings> S getExecutionSettings(@NotNull Project project,
                                                                            @NotNull String linkedProjectPath,
                                                                            @NotNull ProjectSystemId externalSystemId)
    throws IllegalArgumentException
  {
    Holder holder = myHolder.getValue();
    Function<Pair<Project, String>, ? extends ExternalSystemExecutionSettings> provider
      = holder.executionSettingsProviders.get(externalSystemId);
    if (provider == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve execution settings for external system with id '%s'. Reason: no such system is registered. Known systems: %s",
        externalSystemId, holder.executionSettingsProviders.keySet()
      ));
    }
    return (S)provider.fun(Pair.create(project, linkedProjectPath));
  }
  
  @Override
  public void onExternalSystemUnlinked(@NotNull ProjectSystemId externalSystemId, @NotNull Project ideProject) {
    myHolder.getValue().clear(externalSystemId);
  }
  
  private static class Holder {
    @NotNull
    public final ConcurrentMap<ProjectSystemId, Function<Project, ? extends AbstractExternalSystemSettings<?, ?, ?>>> settingsProviders
      = ContainerUtil.newConcurrentMap();

    @NotNull
    public final ConcurrentMap<ProjectSystemId, Function<Project, ? extends AbstractExternalSystemLocalSettings>> localSettingsProviders
      = ContainerUtil.newConcurrentMap();

    @NotNull
    public final ConcurrentMap<ProjectSystemId, Function<Pair<Project, String>, ? extends ExternalSystemExecutionSettings>>
      executionSettingsProviders
      = ContainerUtil.newConcurrentMap();

    public void register(@NotNull ExternalSystemManager<?, ?, ?, ?, ?> manager) {
      settingsProviders.put(manager.getSystemId(), manager.getSettingsProvider());
      localSettingsProviders.put(manager.getSystemId(), manager.getLocalSettingsProvider());
      executionSettingsProviders.put(manager.getSystemId(), manager.getExecutionSettingsProvider());
    }

    public void clear(@NotNull ProjectSystemId externalSystemId) {
      settingsProviders.remove(externalSystemId);
      localSettingsProviders.remove(externalSystemId);
      executionSettingsProviders.remove(externalSystemId);
    }
  }
}

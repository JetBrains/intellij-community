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

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.DisposableExternalSystemService;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * @author Denis Zhdanov
 * @since 4/4/13 4:14 PM
 */
public class ExternalSystemSettingsManager implements DisposableExternalSystemService {

  @NotNull
  private final ConcurrentMap<ProjectSystemId, Function<Project, ? extends AbstractExternalSystemSettings<?, ?>>> mySettingsProviders =
    ContainerUtil.newConcurrentMap();

  @NotNull
  private final ConcurrentMap<ProjectSystemId, Function<Project, ? extends AbstractExternalSystemLocalSettings<?>>> myLocalSettingsProviders
    = ContainerUtil.newConcurrentMap();

  @SuppressWarnings("unchecked")
  @NotNull
  public <L extends ExternalSystemSettingsListener, S extends AbstractExternalSystemSettings<L, S>> S getSettings(
    @NotNull Project project,
    @NotNull ProjectSystemId externalSystemId) throws IllegalArgumentException
  {
    Function<Project, ? extends AbstractExternalSystemSettings<?, ?>> provider = mySettingsProviders.get(externalSystemId);
    if (provider == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve settings for external system with id '%s'. Reason: no such system is registered. Known systems: %s",
        externalSystemId, mySettingsProviders.keySet()
      ));
    }
    return (S)provider.fun(project);
  }

  @SuppressWarnings("unchecked")
  public <S extends AbstractExternalSystemLocalSettings<S>> S getLocalSettings(@NotNull Project project,
                                                                               @NotNull ProjectSystemId externalSystemId)
    throws IllegalArgumentException
  {
    Function<Project, ? extends AbstractExternalSystemLocalSettings<?>> provider = myLocalSettingsProviders.get(externalSystemId);
    if (provider == null) {
      throw new IllegalArgumentException(String.format(
        "Can't retrieve local settings for external system with id '%s'. Reason: no such system is registered. Known systems: %s",
        externalSystemId, myLocalSettingsProviders.keySet()
      ));
    }
    return (S)provider.fun(project);
  }

  @Override
  public void onExternalSystemUnlinked(@NotNull ProjectSystemId externalSystemId, @NotNull Project ideProject) {
    mySettingsProviders.remove(externalSystemId);
    myLocalSettingsProviders.remove(externalSystemId);
  }
}

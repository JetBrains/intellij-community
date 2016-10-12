/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.externalComponents;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registry for {@link ExternalComponentSource}s, used for integrating with the {@link UpdateChecker}.
 * Keeps track of the external components and component sources that can be updated.
 */
public abstract class ExternalComponentManager {
  @NotNull
  public static ExternalComponentManager getInstance() {
    return ServiceManager.getService(ExternalComponentManager.class);
  }

  @NotNull
  public abstract Iterable<ExternalComponentSource> getComponentSources();

  public abstract void registerComponentSource(@NotNull ExternalComponentSource site);

  /**
   * Finds an installed component that could be updated by the given component.
   *
   * @param update The potential update.
   * @param source The source for the update.
   * @return A component from the same source for which the given component is an update, or null if no such component is found.
   */
  @Nullable
  public abstract UpdatableExternalComponent findExistingComponentMatching(@NotNull UpdatableExternalComponent update,
                                                                           @NotNull ExternalComponentSource source);
}
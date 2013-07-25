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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 6/24/13 6:23 PM
 */
public class DelegatingExternalSystemSettingsListener<S extends ExternalProjectSettings> implements ExternalSystemSettingsListener<S> {
  
  @NotNull private final ExternalSystemSettingsListener<S> myDelegate;

  public DelegatingExternalSystemSettingsListener(@NotNull ExternalSystemSettingsListener<S> delegate) {
    myDelegate = delegate;
  }

  @Override
  public void onProjectRenamed(@NotNull String oldName, @NotNull String newName) {
    myDelegate.onProjectRenamed(oldName, newName);
  }

  @Override
  public void onProjectsLinked(@NotNull Collection<S> settings) {
    myDelegate.onProjectsLinked(settings); 
  }

  @Override
  public void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
    myDelegate.onProjectsUnlinked(linkedProjectPaths); 
  }

  @Override
  public void onUseAutoImportChange(boolean currentValue, @NotNull String linkedProjectPath) {
    myDelegate.onUseAutoImportChange(currentValue, linkedProjectPath); 
  }

  @Override
  public void onBulkChangeStart() {
    myDelegate.onBulkChangeStart(); 
  }

  @Override
  public void onBulkChangeEnd() {
    myDelegate.onBulkChangeEnd(); 
  }
}

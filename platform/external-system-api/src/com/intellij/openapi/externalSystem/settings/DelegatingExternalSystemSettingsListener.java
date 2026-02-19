// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public class DelegatingExternalSystemSettingsListener<S extends ExternalProjectSettings> implements ExternalSystemSettingsListener<S> {
  
  private final @NotNull ExternalSystemSettingsListener<S> myDelegate;

  public DelegatingExternalSystemSettingsListener(@NotNull ExternalSystemSettingsListener<S> delegate) {
    myDelegate = delegate;
  }

  @Override
  public void onProjectRenamed(@NotNull String oldName, @NotNull String newName) {
    myDelegate.onProjectRenamed(oldName, newName);
  }

  @Override
  public void onProjectsLoaded(@NotNull Collection<S> settings) {
    myDelegate.onProjectsLoaded(settings);
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
  public void onBulkChangeStart() {
    myDelegate.onBulkChangeStart(); 
  }

  @Override
  public void onBulkChangeEnd() {
    myDelegate.onBulkChangeEnd(); 
  }
}

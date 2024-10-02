// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "ProjectInspectionProfilesVisibleTreeState", storages = @Storage(StoragePathMacros.CACHE_FILE))
public final class ProjectInspectionProfilesVisibleTreeState implements PersistentStateComponent<VisibleTreeStateComponent> {
  private VisibleTreeStateComponent myComponent = new VisibleTreeStateComponent();

  public static ProjectInspectionProfilesVisibleTreeState getInstance(Project project) {
    return project.getService(ProjectInspectionProfilesVisibleTreeState.class);
  }

  @Override
  public VisibleTreeStateComponent getState() {
    return myComponent;
  }

  @Override
  public void loadState(@NotNull VisibleTreeStateComponent state) {
    myComponent = state;
  }

  public VisibleTreeState getVisibleTreeState(@NotNull InspectionProfile profile) {
    return myComponent.getVisibleTreeState(profile);
  }
}

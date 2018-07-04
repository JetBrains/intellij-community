/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "ProjectInspectionProfilesVisibleTreeState", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ProjectInspectionProfilesVisibleTreeState implements PersistentStateComponent<VisibleTreeStateComponent> {
  private final VisibleTreeStateComponent myComponent = new VisibleTreeStateComponent();

  public static ProjectInspectionProfilesVisibleTreeState getInstance(Project project) {
    return ServiceManager.getService(project, ProjectInspectionProfilesVisibleTreeState.class);
  }

  @Override
  public VisibleTreeStateComponent getState() {
    return myComponent;
  }

  @Override
  public void loadState(@NotNull VisibleTreeStateComponent state) {
    myComponent.copyFrom(state);
  }

  public VisibleTreeState getVisibleTreeState(InspectionProfile profile) {
    return myComponent.getVisibleTreeState(profile);
  }
}

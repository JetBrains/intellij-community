// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(
  name = "AppInspectionProfilesVisibleTreeState",
  storages = @Storage(value = "other.xml", roamingType = RoamingType.DISABLED)
)
public final class AppInspectionProfilesVisibleTreeState implements PersistentStateComponent<VisibleTreeStateComponent> {
  private final VisibleTreeStateComponent myComponent = new VisibleTreeStateComponent();

  public static AppInspectionProfilesVisibleTreeState getInstance() {
    return ApplicationManager.getApplication().getService(AppInspectionProfilesVisibleTreeState.class);
  }

  @Override
  public VisibleTreeStateComponent getState() {
    return myComponent;
  }

  @Override
  public void loadState(final @NotNull VisibleTreeStateComponent state) {
    myComponent.copyFrom(state);
  }

  public @NotNull VisibleTreeState getVisibleTreeState(@NotNull InspectionProfile profile) {
    return myComponent.getVisibleTreeState(profile);
  }
}

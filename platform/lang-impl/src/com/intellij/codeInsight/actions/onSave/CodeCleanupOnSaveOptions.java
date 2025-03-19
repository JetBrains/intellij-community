// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "CodeCleanupOnSaveOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class CodeCleanupOnSaveOptions implements PersistentStateComponent<CodeCleanupOnSaveOptions.State> {
  public static @NotNull CodeCleanupOnSaveOptions getInstance(@NotNull Project project) { return project.getService(CodeCleanupOnSaveOptions.class); }

  private Project myProject;
  private static Logger LOG = Logger.getInstance(CodeCleanupOnSaveOptions.class);
  private State myState = new State();

  public CodeCleanupOnSaveOptions(Project project) {
    myProject = project;
  }

  public InspectionProfileImpl getInspectionProfile() {
    final var projectProfileManager = InspectionProfileManager.getInstance(myProject);
    if (myState.PROFILE == null) return projectProfileManager.getCurrentProfile();

    final var projectProfile = ContainerUtil.find(
      projectProfileManager.getProfiles(),
      p -> Objects.equals(p.getName(), myState.PROFILE)
    );
    if (projectProfile != null) return projectProfile;

    final var appProfile = ContainerUtil.find(
      InspectionProfileManager.getInstance().getProfiles(),
      p -> Objects.equals(p.getName(), myState.PROFILE)
    );
    if (appProfile != null) return projectProfile;

    LOG.warn("Can't find profile " + myState.PROFILE + ". Using project profile instead.");
    return projectProfileManager.getCurrentProfile();
  }

  public @Nullable String getProfile() { return myState.PROFILE; }
  public void setProfile(@Nullable String profileName) { myState.PROFILE = profileName; }

  @Override
  public @NotNull CodeCleanupOnSaveOptions.State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static class State {
    public @Nullable String PROFILE;
  }
}

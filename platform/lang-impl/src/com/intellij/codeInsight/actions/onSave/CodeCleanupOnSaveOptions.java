// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Service(Service.Level.PROJECT)
@State(name = "CodeCleanupOnSaveOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class CodeCleanupOnSaveOptions implements PersistentStateComponent<CodeCleanupOnSaveOptions> {
  public static @NotNull CodeCleanupOnSaveOptions getInstance(@NotNull Project project) { return project.getService(CodeCleanupOnSaveOptions.class); }

  private Project myProject;
  private static Logger LOG = Logger.getInstance(CodeCleanupOnSaveOptions.class);
  public @Nullable String PROFILE = null;

  public CodeCleanupOnSaveOptions(Project project) {
    myProject = project;
  }

  public InspectionProfileImpl getInspectionProfile() {
    final var projectProfileManager = InspectionProfileManager.getInstance(myProject);
    if (PROFILE == null) return projectProfileManager.getCurrentProfile();

    final var projectProfile = ContainerUtil.find(
      projectProfileManager.getProfiles(),
      p -> Objects.equals(p.getName(), PROFILE)
    );
    if (projectProfile != null) return projectProfile;

    final var appProfile = ContainerUtil.find(
      InspectionProfileManager.getInstance().getProfiles(),
      p -> Objects.equals(p.getName(), PROFILE)
    );
    if (appProfile != null) return projectProfile;

    LOG.warn("Can't find profile " + PROFILE + ". Using project profile instead.");
    return projectProfileManager.getCurrentProfile();
  }

  @Override
  public CodeCleanupOnSaveOptions getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull CodeCleanupOnSaveOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}

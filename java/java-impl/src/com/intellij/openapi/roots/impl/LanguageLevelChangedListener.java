// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class LanguageLevelChangedListener implements WorkspaceModelChangeListener {

  private final Project myProject;

  public LanguageLevelChangedListener(Project project) { myProject = project; }

  @Override
  public void changed(@NotNull VersionedStorageChange event) {
    boolean updateLanguageLevel = ContainerUtil.exists(event.getChanges(JavaModuleSettingsEntity.class), change ->
      change instanceof EntityChange.Replaced<?> &&
      !Objects.equals(
        ((EntityChange.Replaced<JavaModuleSettingsEntity>)change).getOldEntity().getLanguageLevelId(),
        ((EntityChange.Replaced<JavaModuleSettingsEntity>)change).getNewEntity().getLanguageLevelId()
      )
    );
    if (updateLanguageLevel) {
      LanguageLevelProjectExtensionImpl.languageLevelsChanged(myProject);
    }
  }
}

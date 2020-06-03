// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public abstract class RecentProjectsManager {
  public static RecentProjectsManager getInstance() {
    return ServiceManager.getService(RecentProjectsManager.class);
  }

  public abstract @Nullable @SystemIndependent String getLastProjectCreationLocation();

  public abstract void setLastProjectCreationLocation(@Nullable @SystemIndependent String lastProjectLocation);

  public void setLastProjectCreationLocation(@Nullable Path value) {
    if (value == null) {
      setLastProjectCreationLocation((String)null);
    }
    else {
      setLastProjectCreationLocation(PathUtil.toSystemIndependentName(value.toString()));
    }
  }

  public abstract void updateLastProjectPath();

  public abstract void removePath(@NotNull @SystemIndependent String path);

  /**
   * @deprecated Use {@link RecentProjectListActionProvider#getActions}
   */
  @Deprecated
  public abstract AnAction @NotNull [] getRecentProjectsActions(boolean addClearListItem);

  /**
   * @deprecated Use {@link RecentProjectListActionProvider#getActions}
   */
  @Deprecated
  public AnAction @NotNull [] getRecentProjectsActions(boolean addClearListItem, boolean useGroups) {
    return getRecentProjectsActions(addClearListItem);
  }

  @NotNull
  public List<ProjectGroup> getGroups() {
    return Collections.emptyList();
  }

  public void addGroup(@NotNull ProjectGroup group) {
  }

  public void removeGroup(@NotNull ProjectGroup group) {
  }

  public boolean hasPath(@SystemIndependent String path) {
    return false;
  }

  public abstract boolean willReopenProjectOnStart();

  public abstract boolean reopenLastProjectsOnStart();
}
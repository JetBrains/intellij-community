// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.util.Collections;
import java.util.List;

public abstract class RecentProjectsManager {
  public static RecentProjectsManager getInstance() {
    return ServiceManager.getService(RecentProjectsManager.class);
  }

  @Nullable
  @SystemIndependent
  public abstract String getLastProjectCreationLocation();

  public abstract void setLastProjectCreationLocation(@Nullable @SystemIndependent String lastProjectLocation);

  public abstract void updateLastProjectPath();

  @SystemIndependent
  public abstract String getLastProjectPath();

  public abstract void removePath(@Nullable @SystemIndependent String path);

  /**
   * @param addClearListItem whether the "Clear List" action should be added to the end of the list.
   */
  @NotNull
  public abstract AnAction[] getRecentProjectsActions(boolean addClearListItem);

  @NotNull
  public AnAction[] getRecentProjectsActions(boolean addClearListItem, boolean useGroups) {
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

  public abstract void reopenLastProjectOnStart();
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class DvcsBranchManager {
  @NotNull private final DvcsBranchSettings myBranchSettings;
  @NotNull private final Map<BranchType, Collection<String>> myPredefinedFavoriteBranches = new HashMap<>();
  @NotNull private final Project myProject;

  @NotNull public static final Topic<DvcsBranchManagerListener> DVCS_BRANCH_SETTINGS_CHANGED =
    Topic.create("Branch settings changed", DvcsBranchManagerListener.class);

  protected DvcsBranchManager(@NotNull Project project, @NotNull DvcsBranchSettings settings,
                              BranchType @NotNull [] branchTypes) {
    myProject = project;
    myBranchSettings = settings;
    for (BranchType type : branchTypes) {
      String defaultBranchName = getDefaultBranchName(type);
      if (!StringUtil.isEmptyOrSpaces(defaultBranchName)) {
        myPredefinedFavoriteBranches.put(type, Collections.singleton(defaultBranchName));
      }
    }
  }

  @Nullable
  protected String getDefaultBranchName(@NotNull BranchType type) {return null;}

  public boolean isFavorite(@Nullable BranchType branchType, @Nullable Repository repository, @NotNull String branchName) {
    if (branchType == null) return false;
    String branchTypeName = branchType.getName();
    if (myBranchSettings.getFavorites().contains(branchTypeName, repository, branchName)) return true;
    if (myBranchSettings.getExcludedFavorites().contains(branchTypeName, repository, branchName)) return false;
    return isPredefinedAsFavorite(branchType, branchName);
  }

  private boolean isPredefinedAsFavorite(@NotNull BranchType type, @NotNull String branchName) {
    Collection<String> predefinedNames = myPredefinedFavoriteBranches.get(type);
    return predefinedNames != null && predefinedNames.contains(branchName);
  }

  public void setFavorite(@Nullable BranchType branchType,
                          @Nullable Repository repository,
                          @NotNull String branchName,
                          boolean shouldBeFavorite) {
    if (branchType == null) return;
    String branchTypeName = branchType.getName();
    if (shouldBeFavorite) {
      myBranchSettings.getExcludedFavorites().remove(branchTypeName, repository, branchName);
      if (!isPredefinedAsFavorite(branchType, branchName)) {
        myBranchSettings.getFavorites().add(branchTypeName, repository, branchName);
      }
    }
    else {
      myBranchSettings.getFavorites().remove(branchTypeName, repository, branchName);
      if (isPredefinedAsFavorite(branchType, branchName)) {
        myBranchSettings.getExcludedFavorites().add(branchTypeName, repository, branchName);
      }
    }
    notifyFavoriteSettingsChanged();
  }

  private void notifyFavoriteSettingsChanged() {
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
      myProject.getMessageBus().syncPublisher(DVCS_BRANCH_SETTINGS_CHANGED).branchFavoriteSettingsChanged();
    });
  }

  public boolean isGroupingEnabled(@NotNull GroupingKey key) {
    return myBranchSettings.getGroupingKeyIds().contains(key.getId());
  }

  public void setGrouping(@NotNull GroupingKey key, boolean state) {
    if (state) {
      myBranchSettings.getGroupingKeyIds().add(key.getId());
    }
    else {
      myBranchSettings.getGroupingKeyIds().remove(key.getId());
    }

    myBranchSettings.intIncrementModificationCount();
    notifyGroupingSettingsChanged(key, state);
  }

  private void notifyGroupingSettingsChanged(@NotNull GroupingKey key, boolean state) {
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
      myProject.getMessageBus().syncPublisher(DVCS_BRANCH_SETTINGS_CHANGED).branchGroupingSettingsChanged(key, state);
    });
  }

  public interface DvcsBranchManagerListener {
    default void branchFavoriteSettingsChanged() { }
    default void branchGroupingSettingsChanged(@NotNull GroupingKey key, boolean state) { }
  }
}

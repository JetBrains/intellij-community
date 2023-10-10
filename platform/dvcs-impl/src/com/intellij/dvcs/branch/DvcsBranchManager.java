// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.Topic;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DvcsBranchManager<T extends Repository> {
  private final AbstractRepositoryManager<T> myRepositoryManager;

  @NotNull private final DvcsBranchSettings myBranchSettings;
  @NotNull private final Map<BranchType, Collection<String>> myPredefinedFavoriteBranches = new HashMap<>();
  @NotNull private final Project myProject;

  @NotNull public static final Topic<DvcsBranchManagerListener> DVCS_BRANCH_SETTINGS_CHANGED =
    Topic.create("Branch settings changed", DvcsBranchManagerListener.class);

  protected DvcsBranchManager(@NotNull Project project,
                              @NotNull DvcsBranchSettings settings,
                              BranchType @NotNull [] branchTypes,
                              @NotNull AbstractRepositoryManager<T> repositoryManager) {
    myProject = project;
    myBranchSettings = settings;
    myRepositoryManager = repositoryManager;
    for (BranchType type : branchTypes) {
      Collection<String> predefinedFavoriteBranches = myPredefinedFavoriteBranches.computeIfAbsent(type, __ -> new HashSet<>());

      for (String branchName : getDefaultBranchNames(type)) {
        if (!StringUtil.isEmptyOrSpaces(branchName)) {
          predefinedFavoriteBranches.add(branchName);
        }
      }

      myPredefinedFavoriteBranches.put(type, predefinedFavoriteBranches);
    }
  }

  protected Collection<String> getDefaultBranchNames(@NotNull BranchType type) { return Collections.EMPTY_LIST; }

  public boolean isFavorite(@Nullable BranchType branchType, @Nullable Repository repository, @NotNull String branchName) {
    if (branchType == null) return false;
    String branchTypeName = branchType.getName();
    if (myBranchSettings.getFavorites().contains(branchTypeName, repository, branchName)) return true;
    if (myBranchSettings.getExcludedFavorites().contains(branchTypeName, repository, branchName)) return false;
    return isPredefinedAsFavorite(branchType, branchName);
  }

  public @NotNull Map<T, Collection<String>> getFavoriteBranches(@NotNull BranchType branchType) {
    String branchTypeName = branchType.getName();
    List<DvcsBranchInfo> favorites = ContainerUtil.notNullize(myBranchSettings.getFavorites().getBranches().get(branchTypeName));
    List<DvcsBranchInfo> excludedFavorites =
      ContainerUtil.notNullize(myBranchSettings.getExcludedFavorites().getBranches().get(branchTypeName));
    Collection<String> predefinedFavorites = myPredefinedFavoriteBranches.get(branchType);

    MultiMap<T, String> result = MultiMap.createSet();

    if (predefinedFavorites != null) {
      for (T repo : myRepositoryManager.getRepositories()) {
        result.putValues(repo, predefinedFavorites);
      }
    }

    for (DvcsBranchInfo info : excludedFavorites) {
      T repo = myRepositoryManager.getRepositoryForRootQuick(VcsUtil.getFilePath(info.repoPath, true));
      if (repo == null) continue;
      result.remove(repo, info.sourceName);
    }

    for (DvcsBranchInfo info : favorites) {
      T repo = myRepositoryManager.getRepositoryForRootQuick(VcsUtil.getFilePath(info.repoPath, true));
      if (repo == null) continue;
      result.putValue(repo, info.sourceName);
    }

    return result.toHashMap();
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

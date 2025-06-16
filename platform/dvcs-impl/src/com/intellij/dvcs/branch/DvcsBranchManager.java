// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DvcsBranchManager<T extends Repository> {
  private static final Logger LOG = Logger.getInstance(DvcsBranchManager.class);

  private final AbstractRepositoryManager<T> myRepositoryManager;

  private final @NotNull DvcsBranchSettings myBranchSettings;
  private final @NotNull Map<BranchType, Collection<String>> myPredefinedFavoriteBranches = new HashMap<>();
  protected final @NotNull Project myProject;

  public static final @NotNull Topic<DvcsBranchManagerListener> DVCS_BRANCH_SETTINGS_CHANGED =
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
    VirtualFile root = repository == null ? null : repository.getRoot();
    if (myBranchSettings.getFavorites().contains(branchTypeName, root, branchName)) return true;
    if (myBranchSettings.getExcludedFavorites().contains(branchTypeName, root, branchName)) return false;
    return isPredefinedAsFavorite(branchType, branchName);
  }

  public @NotNull Set<@NotNull String> getFavoriteRefs(@NotNull BranchType refType, @NotNull Repository repository) {
    Set<String> result = new HashSet<>(myPredefinedFavoriteBranches.getOrDefault(refType, Collections.emptyList()));

    var favorites = myBranchSettings.getFavorites().getBranches();
    var excludedFavorites = myBranchSettings.getExcludedFavorites().getBranches();

    String repoPath = DvcsBranchUtil.getPathFor(repository);
    for (DvcsBranchInfo info : ContainerUtil.notNullize(favorites.get(refType.getName()))) {
      if (info.repoPath.equals(repoPath)) {
        result.add(info.sourceName);
      }
    }

    for (DvcsBranchInfo info : ContainerUtil.notNullize(excludedFavorites.get(refType.getName()))) {
      if (info.repoPath.equals(repoPath)) {
        result.remove(info.sourceName);
      }
    }

    return result;
  }

  public @NotNull Map<@NotNull T, @NotNull Set<@NotNull String>> getFavoriteBranches(@NotNull BranchType branchType) {
    Map<T, List<String>> favorites = collectBranchesByRoot(myBranchSettings.getFavorites(), branchType);
    Map<T, List<String>> excludedFavorites = collectBranchesByRoot(myBranchSettings.getExcludedFavorites(), branchType);
    Collection<String> predefinedFavorites = myPredefinedFavoriteBranches.get(branchType);

    Map<T, Set<String>> result = new HashMap<>();
    for (T repo : myRepositoryManager.getRepositories()) {
      HashSet<String> branches = new HashSet<>();

      if (predefinedFavorites != null) {
        branches.addAll(predefinedFavorites);
      }

      List<String> repoExcludedFavorites = ContainerUtil.notNullize(excludedFavorites.get(repo));
      for (String repoExcludedFavorite : repoExcludedFavorites) {
        branches.remove(repoExcludedFavorite);
      }

      List<String> repoFavorites = ContainerUtil.notNullize(favorites.get(repo));
      branches.addAll(repoFavorites);

      result.put(repo, branches);
    }
    return result;
  }

  private @NotNull Map<T, List<String>> collectBranchesByRoot(@NotNull BranchStorage storage, @NotNull BranchType branchType) {
    List<DvcsBranchInfo> infos = ContainerUtil.notNullize(storage.getBranches().get(branchType.getName()));

    Map<String, List<String>> infoByPath = new HashMap<>();
    for (DvcsBranchInfo info : infos) {
      List<String> list = infoByPath.computeIfAbsent(info.repoPath, key -> new ArrayList<>());
      list.add(info.sourceName);
    }

    Map<T, List<String>> infoByRepo = new HashMap<>();

    List<T> allRepos = myRepositoryManager.getRepositories();
    List<String> allRepoFavorites = ContainerUtil.notNullize(infoByPath.remove(DvcsBranchUtil.getPathFor(null)));
    if (!allRepoFavorites.isEmpty()) {
      for (T repo : allRepos) {
        List<String> repoList = infoByRepo.computeIfAbsent(repo, key -> new ArrayList<>());
        repoList.addAll(allRepoFavorites);
      }
    }

    infoByPath.forEach((repoPath, list) -> {
      T repo = myRepositoryManager.getRepositoryForRootQuick(VcsUtil.getFilePath(repoPath, true));
      if (repo == null) return;
      List<String> repoList = infoByRepo.computeIfAbsent(repo, key -> new ArrayList<>());
      repoList.addAll(list);
    });

    return infoByRepo;
  }

  private boolean isPredefinedAsFavorite(@NotNull BranchType type, @NotNull String branchName) {
    Collection<String> predefinedNames = myPredefinedFavoriteBranches.get(type);
    return predefinedNames != null && predefinedNames.contains(branchName);
  }

  public void setFavorite(@Nullable BranchType branchType,
                          @Nullable T repository,
                          @NotNull String branchName,
                          boolean shouldBeFavorite) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Changing favorite state of %s(%s) to %s in %s"
                  .formatted(branchName, branchType != null ? branchType.getName() : "", shouldBeFavorite, repository));
    }

    if (branchType == null) return;
    String branchTypeName = branchType.getName();
    VirtualFile root = repository == null ? null : repository.getRoot();
    if (shouldBeFavorite) {
      myBranchSettings.getExcludedFavorites().remove(branchTypeName, root, branchName);
      if (!isPredefinedAsFavorite(branchType, branchName)) {
        myBranchSettings.getFavorites().add(branchTypeName, root, branchName);
      }
    }
    else {
      myBranchSettings.getFavorites().remove(branchTypeName, root, branchName);
      if (isPredefinedAsFavorite(branchType, branchName)) {
        myBranchSettings.getExcludedFavorites().add(branchTypeName, root, branchName);
      }
    }
    notifyFavoriteSettingsChanged(repository);
  }

  protected void notifyFavoriteSettingsChanged(@Nullable T repository) {
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
      myProject.getMessageBus().syncPublisher(DVCS_BRANCH_SETTINGS_CHANGED).branchFavoriteSettingsChanged();
    });
  }

  public boolean isGroupingEnabled(@NotNull GroupingKey key) {
    return myBranchSettings.isGroupingEnabled(key);
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

    default void showTagsSettingsChanged(boolean state) { }
  }
}

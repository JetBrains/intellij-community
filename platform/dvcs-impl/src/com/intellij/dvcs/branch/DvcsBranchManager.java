/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map2List;
import static com.intellij.util.containers.ContainerUtil.newArrayList;

public abstract class DvcsBranchManager<T extends Repository> {
  @NotNull private final AbstractRepositoryManager<T> myRepositoryManager;
  @NotNull private final DvcsBranchSettings myBranchSettings;
  @NotNull public final BranchStorage myPredefinedFavoriteBranches = new BranchStorage();

  protected DvcsBranchManager(@NotNull AbstractRepositoryManager<T> repositoryManager,
                              @NotNull DvcsBranchSettings settings,
                              @NotNull BranchType[] branchTypes) {
    myRepositoryManager = repositoryManager;
    myBranchSettings = settings;
    for (BranchType type : branchTypes) {
      String defaultBranchName = getDefaultBranchName(type);
      if (!StringUtil.isEmptyOrSpaces(defaultBranchName)) {
        myPredefinedFavoriteBranches.myBranches.put(type.getName(), constructDefaultBranchPredefinedList(defaultBranchName));
      }
    }
  }

  @NotNull
  private List<DvcsBranchInfo> constructDefaultBranchPredefinedList(@NotNull String defaultBranchName) {
    List<DvcsBranchInfo> branchInfos = newArrayList(new DvcsBranchInfo("", defaultBranchName));
    branchInfos.addAll(map2List(myRepositoryManager.getRepositories(),
                                repository -> new DvcsBranchInfo(repository.getRoot().getPath(), defaultBranchName)));
    return branchInfos;
  }

  @Nullable
  protected String getDefaultBranchName(@NotNull BranchType type) {return null;}

  public boolean isFavorite(@Nullable BranchType branchType, @Nullable Repository repository, @NotNull String branchName) {
    if (branchType == null) return false;
    String branchTypeName = branchType.getName();
    if (myBranchSettings.getFavorites().contains(branchTypeName, repository, branchName)) return true;
    if (myBranchSettings.getExcludedFavorites().contains(branchTypeName, repository, branchName)) return false;
    return myPredefinedFavoriteBranches.contains(branchTypeName, repository, branchName);
  }

  public void setFavorite(@Nullable BranchType branchType,
                          @Nullable Repository repository,
                          @NotNull String branchName,
                          boolean shouldBeFavorite) {
    if (branchType == null) return;
    String branchTypeName = branchType.getName();
    if (shouldBeFavorite) {
      myBranchSettings.getFavorites().add(branchTypeName, repository, branchName);
      myBranchSettings.getExcludedFavorites().remove(branchTypeName, repository, branchName);
    }
    else {
      if (myBranchSettings.getFavorites().contains(branchTypeName, repository, branchName)) {
        myBranchSettings.getFavorites().remove(branchTypeName, repository, branchName);
      }
      else if (myPredefinedFavoriteBranches.contains(branchTypeName, repository, branchName)) {
        myBranchSettings.getExcludedFavorites().add(branchTypeName, repository, branchName);
      }
    }
  }
}

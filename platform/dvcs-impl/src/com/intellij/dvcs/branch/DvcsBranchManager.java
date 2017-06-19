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

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public abstract class DvcsBranchManager {
  @NotNull private final DvcsBranchSettings myBranchSettings;
  @NotNull private final Map<BranchType, Collection<String>> myPredefinedFavoriteBranches = ContainerUtil.newHashMap();

  protected DvcsBranchManager(@NotNull DvcsBranchSettings settings,
                              @NotNull BranchType[] branchTypes) {
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
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class DvcsBranchManager {
  @NotNull private final DvcsBranchSettings myBranchSettings;
  @NotNull private final Map<BranchType, Collection<String>> myPredefinedFavoriteBranches = new HashMap<>();

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

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.DEFAULT_NUM;
import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.MAX_NUM;

public final class BranchActionUtil {
  public static final Comparator<BranchActionGroup> FAVORITE_BRANCH_COMPARATOR =
    Comparator.comparing(branch -> branch.isFavorite() ? -1 : 0);

  public static int getNumOfFavorites(@NotNull List<? extends BranchActionGroup> branchActions) {
    return ContainerUtil.count(branchActions, BranchActionGroup::isFavorite);
  }

  public static int getNumOfTopShownBranches(@NotNull List<? extends BranchActionGroup> branchActions) {
    int numOfFavorites = getNumOfFavorites(branchActions);
    if (branchActions.size() > MAX_NUM) {
      if (numOfFavorites > 0) return numOfFavorites;
      return DEFAULT_NUM;
    }
    return MAX_NUM;
  }
}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.dvcs.ui;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.DEFAULT_NUM;
import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.MAX_NUM;

public class BranchActionUtil {
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

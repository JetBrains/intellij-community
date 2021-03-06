// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A UsageInfo object that can be transferred to a model branch.
 */
@ApiStatus.Experimental
public interface BranchableUsageInfo {
  /**
   * Provides a copy of this UsageInfo that refers to specified model branch.
   * <p>
   * It's encouraged that the implementing classes should be final to ensure that
   * the copy is complete.
   *
   * @return a copy of this UsageInfo that refers to specified model branch.
   */
  @NotNull UsageInfo obtainBranchCopy(@NotNull ModelBranch branch);

  static UsageInfo @NotNull [] convertUsages(UsageInfo @NotNull [] usages, @NotNull ModelBranch branch) {
    Set<String> unsupportedTypes = null;
    List<UsageInfo> result = new ArrayList<>(usages.length);
    for (UsageInfo t : usages) {
      if (t instanceof BranchableUsageInfo) {
        result.add(((BranchableUsageInfo)t).obtainBranchCopy(branch));
      }
      else {
        if (unsupportedTypes == null) {
          unsupportedTypes = new HashSet<>();
        }
        unsupportedTypes.add(t.getClass().getName());
      }
    }
    if (unsupportedTypes != null) {
      Logger.getInstance(BranchableUsageInfo.class)
        .error("Branching is requested for " + String.join(", ", unsupportedTypes) + " class(es). " +
               "Please implement BranchableUsageInfo interface");
    }
    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }
}

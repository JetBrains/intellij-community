// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Simple UsageInfo that supports branching
 */
public final class SimpleBranchableUsageInfo extends UsageInfo implements BranchableUsageInfo {
  public SimpleBranchableUsageInfo(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  public @NotNull UsageInfo obtainBranchCopy(@NotNull ModelBranch branch) {
    return new SimpleBranchableUsageInfo(branch.obtainPsiCopy(Objects.requireNonNull(getElement())));
  }
}

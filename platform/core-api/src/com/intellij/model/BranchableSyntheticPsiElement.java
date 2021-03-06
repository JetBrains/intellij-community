// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A non-physical psi element that must be converted when used in ModelBranch context.
 * Physical elements should be processed automatically.
 */
@ApiStatus.Experimental
public interface BranchableSyntheticPsiElement extends PsiElement {
  /**
   * @param branch branch
   * @return a copy of element inside that branch
   */
  @NotNull BranchableSyntheticPsiElement obtainBranchCopy(@NotNull ModelBranch branch);

  /**
   * @return a branch this element belongs to (null if it doesn't belong to any branch)
   */
  @Nullable ModelBranch getModelBranch();
}

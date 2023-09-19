// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class SkipAutopopupInComments extends CompletionConfidence {

  @Override
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (PsiTreeUtil.getNonStrictParentOfType(contextElement, PsiComment.class) != null) {
      return ThreeState.YES;
    }

    return ThreeState.UNSURE;
  }

}

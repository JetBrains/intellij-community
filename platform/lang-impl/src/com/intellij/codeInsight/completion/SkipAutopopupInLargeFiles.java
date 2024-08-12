// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public final class SkipAutopopupInLargeFiles extends CompletionConfidence {
  @Override
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    if (SingleRootFileViewProvider.isTooLargeForIntelligence(file)) {
      return ThreeState.YES;
    }
    return ThreeState.UNSURE;
  }
}
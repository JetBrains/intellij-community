// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveLeftRight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DefaultMoveElementLeftRightHandler extends MoveElementLeftRightHandler {

  @Override
  public PsiElement @NotNull [] getMovableSubElements(@NotNull PsiElement element) {
    if (element instanceof PsiListLikeElement) {
      return ((PsiListLikeElement)element).getComponents().toArray(PsiElement.EMPTY_ARRAY);
    }
    else {
      return PsiElement.EMPTY_ARRAY;
    }
  }
}

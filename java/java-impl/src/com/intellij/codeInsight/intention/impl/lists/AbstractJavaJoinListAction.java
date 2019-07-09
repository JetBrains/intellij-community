// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractJavaJoinListAction<L extends PsiElement, E extends PsiElement> extends AbstractJoinListAction<L, E> {
  @Override
  @Nullable
  PsiElement prevBreak(@NotNull PsiElement element) {
    return JavaListUtils.prevBreak(element);
  }

  @Override
  @Nullable
  PsiElement nextBreak(@NotNull PsiElement element) {
    return JavaListUtils.nextBreak(element);
  }

  @Override
  protected boolean canJoin(@NotNull List<E> elements) {
    PsiElement parent = elements.get(0).getParent();
    for (PsiElement child : parent.getChildren()) {
      if (child instanceof PsiComment && ((PsiComment)child).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        return false;
      }
    }
    return true;
  }
}

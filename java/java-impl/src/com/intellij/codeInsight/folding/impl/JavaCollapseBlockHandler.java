// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

final class JavaCollapseBlockHandler extends CollapseBlockHandlerImpl {
  @Override
  protected @Nullable PsiElement findParentBlock(@Nullable PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
  }

  @Override
  protected boolean isEndBlockToken(@Nullable PsiElement element) {
    return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE;
  }
}

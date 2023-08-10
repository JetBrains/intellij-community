// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiParenthesizedPatternImpl extends CompositePsiElement implements PsiParenthesizedPattern, Constants {
  public PsiParenthesizedPatternImpl() {
    super(PARENTHESIZED_PATTERN);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParenthesizedPattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiParenthesizedPattern";
  }

  @Override
  public @Nullable PsiPattern getPattern() {
    return PsiTreeUtil.getChildOfType(this, PsiPattern.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PsiPattern pattern = getPattern();
    if (pattern == null) return true;

    return pattern.processDeclarations(processor, state, null, place);
  }
}


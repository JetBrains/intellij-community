// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.psi.impl.source.tree.JavaElementType.PATTERN_GUARD;

public class PsiPatternGuardImpl extends CompositePsiElement implements PsiPatternGuard {
  public PsiPatternGuardImpl() {
    super(PATTERN_GUARD);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPatternGuard(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiPattern getPattern() {
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiPattern.class));
  }

  @Override
  public @Nullable PsiExpression getGuardingExpression() {
    return PsiTreeUtil.getChildOfType(this, PsiExpression.class);
  }

  @Override
  public String toString() {
    return "PsiPatternGuard";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PsiPattern pattern = getPattern();
    if (!pattern.processDeclarations(processor, state, null, place)) return false;

    final PsiExpression expression = getGuardingExpression();
    if (expression == null) return true;
    return expression.processDeclarations(processor, state, lastParent, place);
  }
}
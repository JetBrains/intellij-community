// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class PsiTemplateImpl extends ExpressionPsiElement implements PsiTemplate {

  public PsiTemplateImpl() {
    super(JavaElementType.TEMPLATE);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTemplate(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull List<@NotNull PsiFragment> getFragments() {
    final List<PsiFragment> result = new ArrayList<>();
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof PsiFragment) result.add((PsiFragment)child);
      child = child.getNextSibling();
    }
    return result;
  }

  @Override
  public @NotNull List<@NotNull PsiExpression> getEmbeddedExpressions() {
    final List<PsiExpression> result = new ArrayList<>();
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof PsiExpression) result.add((PsiExpression)child);
      child = child.getNextSibling();
    }
    return result;
  }

  @Override
  public String toString() {
    return "PsiTemplate";
  }
}
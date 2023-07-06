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
  public List<PsiLiteralExpression> getFragments() {
    final List<PsiLiteralExpression> result = new ArrayList<>();
    PsiElement @NotNull [] children = getChildren();
    for (int i = 0, length = children.length; i < length; i += 2) {
      result.add((PsiLiteralExpression)children[i]);
    }
    return result;
  }

  @Override
  public List<PsiExpression> getEmbeddedExpressions() {
    final List<PsiExpression> result = new ArrayList<>();
    PsiElement @NotNull [] children = getChildren();
    for (int i = 1, length = children.length; i < length; i += 2) {
      result.add((PsiExpression)children[i]);
    }
    return result;
  }

  @Override
  public PsiType getType() {
    return PsiType.getTypeByName("java.lang.StringTemplate", getProject(), getResolveScope());
  }

  @Override
  public String toString() {
    return "PsiTemplate";
  }
}
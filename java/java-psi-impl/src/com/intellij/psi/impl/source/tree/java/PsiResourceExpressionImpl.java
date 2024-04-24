// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiResourceExpressionImpl extends CompositePsiElement implements PsiResourceExpression {
  public PsiResourceExpressionImpl() {
    super(JavaElementType.RESOURCE_EXPRESSION);
  }

  @Override
  public @NotNull PsiExpression getExpression() {
    return (PsiExpression)getFirstChild();
  }

  @Override
  public @Nullable PsiType getType() {
    return getExpression().getType();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitResourceExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiResourceExpression";
  }
}

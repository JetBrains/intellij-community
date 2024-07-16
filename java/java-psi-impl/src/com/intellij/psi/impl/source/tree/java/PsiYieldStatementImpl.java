// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiYieldStatementImpl extends CompositePsiElement implements PsiYieldStatement {
  public PsiYieldStatementImpl() {
    super(JavaElementType.YIELD_STATEMENT);
  }

  @Override
  public PsiExpression getExpression() {
    return (PsiExpression)findPsiChildByType(ElementType.EXPRESSION_BIT_SET);
  }

  @Override
  public @Nullable PsiSwitchExpression findEnclosingExpression() {
    return PsiImplUtil.findEnclosingSwitchExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitYieldStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiYieldStatement";
  }
}
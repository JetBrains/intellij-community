// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;

public abstract class JavaBooleanExpressionSurrounder extends JavaExpressionSurrounder {
  @Override
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    return type != null && (PsiTypes.booleanType().equals(type) || PsiTypes.booleanType().equals(PsiPrimitiveType.getUnboxedType(type)));
  }
}

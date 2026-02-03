// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Medvedev
 */
public class JavaExpressionWrapper implements IntroduceParameterData.ExpressionWrapper {
  private final PsiExpression myExpression;

  public JavaExpressionWrapper(@NotNull PsiExpression expression) {
    myExpression = expression;
  }

  @Override
  public @NotNull String getText() {
    return myExpression.getText();
  }

  @Override
  public PsiType getType() {
    return myExpression.getType();
  }

  @Override
  public @NotNull PsiElement getExpression() {
    return myExpression;
  }
}

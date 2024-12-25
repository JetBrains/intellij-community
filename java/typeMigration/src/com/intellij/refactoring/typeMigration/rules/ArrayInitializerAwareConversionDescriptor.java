// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import org.jetbrains.annotations.NotNull;

class ArrayInitializerAwareConversionDescriptor extends TypeConversionDescriptor {
  ArrayInitializerAwareConversionDescriptor(String stringToReplace,
                                                   String replaceByString,
                                                   PsiExpression expression) {
    super(stringToReplace, replaceByString, expression);
  }

  @Override
  protected @NotNull PsiExpression adjustExpressionBeforeReplacement(@NotNull PsiExpression expression) {
    if (expression instanceof PsiArrayInitializerExpression) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
      return (PsiExpression)expression.replace(elementFactory.createExpressionFromText("new " +
                                                                                       TypeConversionUtil
                                                                                         .erasure(expression.getType()).getCanonicalText() +
                                                                                       expression.getText(),
                                                                                       expression));
    }
    return expression;
  }
}

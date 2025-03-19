// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThisReferenceDescriptor implements ItemToReplaceDescriptor {
  private final PsiThisExpression myThisExpression;
  private final PsiClass myPsiClass;

  public ThisReferenceDescriptor(PsiThisExpression expression, PsiClass referredClass) {
    myThisExpression = expression;
    myPsiClass = referredClass;
  }

  public static @Nullable ItemToReplaceDescriptor createIfInaccessible(@NotNull PsiThisExpression thisExpression) {
    PsiType expressionType = thisExpression.getType();
    if (expressionType != null) {
      PsiClass psiClass = PsiTypesUtil.getPsiClass(expressionType);
      if (psiClass != null) {
        return new ThisReferenceDescriptor(thisExpression, psiClass);
      }
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    String newExpression = MemberQualifierUtil.handleThisReference(myThisExpression, myPsiClass, outerClass,
                                                                   callExpression, elementFactory);
    myThisExpression.replace(elementFactory.createExpressionFromText(newExpression, myThisExpression));
  }
}

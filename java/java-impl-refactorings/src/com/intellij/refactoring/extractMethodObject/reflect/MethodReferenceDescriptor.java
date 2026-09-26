// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodReferenceDescriptor implements ItemToReplaceDescriptor {
  private final PsiMethodReferenceExpression myReferenceExpression;

  private MethodReferenceDescriptor(PsiMethodReferenceExpression expression) {
    myReferenceExpression = expression;
  }

  public static @Nullable MethodReferenceDescriptor createIfInaccessible(@NotNull PsiMethodReferenceExpression expression) {
    if (!PsiReflectionAccessUtil.isAccessibleMethodReference(expression)) {
      return new MethodReferenceDescriptor(expression);
    }
    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    LambdaRefactoringUtil.convertMethodReferenceToLambda(myReferenceExpression, false, true);
  }
}

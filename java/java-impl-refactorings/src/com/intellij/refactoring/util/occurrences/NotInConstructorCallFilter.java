// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.occurrences;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;

public class NotInConstructorCallFilter implements OccurrenceFilter {
  public static final NotInConstructorCallFilter INSTANCE = new NotInConstructorCallFilter();

  @Override
  public boolean isOK(@NotNull PsiExpression occurrence) {
    PsiMethod method = PsiTreeUtil.getParentOfType(occurrence, PsiMethod.class, true, PsiMember.class, PsiLambdaExpression.class);
    if (method == null || !method.isConstructor()) return true;
    PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    return constructorCall == null || occurrence.getTextOffset() >= constructorCall.getTextOffset() + constructorCall.getTextLength();
  }
}
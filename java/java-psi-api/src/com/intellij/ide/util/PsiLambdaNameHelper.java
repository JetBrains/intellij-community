// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class PsiLambdaNameHelper {
  private static final Key<ParameterizedCachedValue<Map<PsiLambdaExpression, String>, PsiClass>> LAMBDA_NAME = Key.create("ANONYMOUS_CLASS_NAME");

  public static @Nullable String getVMName(@NotNull PsiLambdaExpression lambdaExpression) {
    final PsiClass upper = PsiTreeUtil.getParentOfType(lambdaExpression, PsiClass.class);
    if (upper == null) {
      return null;
    }
    ParameterizedCachedValue<Map<PsiLambdaExpression, String>, PsiClass> value = upper.getUserData(LAMBDA_NAME);
    if (value == null) {
      value = CachedValuesManager.getManager(upper.getProject()).createParameterizedCachedValue(
        new ParameterizedCachedValueProvider<Map<PsiLambdaExpression, String>, PsiClass>() {
          @Override
          public CachedValueProvider.Result<Map<PsiLambdaExpression, String>> compute(final PsiClass upper) {
            final Map<PsiLambdaExpression, String> map = new HashMap<>();
            upper.accept(new JavaRecursiveElementWalkingVisitor() {
              int index;

              @Override
              public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
                map.put(expression, "$" + index++);
                super.visitLambdaExpression(expression);
              }

              @Override
              public void visitClass(@NotNull PsiClass aClass) {
                if (aClass == upper) {
                  super.visitClass(aClass);
                }
              }
            });
            return CachedValueProvider.Result.create(map, upper);
          }
        }, false);
      upper.putUserData(LAMBDA_NAME, value);
    }
    return "lambda" + getLambdaPrefix(lambdaExpression) + value.getValue(upper).get(lambdaExpression);
  }

  public static String getLambdaPrefix(@NotNull PsiLambdaExpression lambdaExpression) {
    PsiMember member = PsiTreeUtil.getParentOfType(lambdaExpression, PsiMethod.class, PsiClass.class, PsiField.class);
    final String methodPrefix;
    if (member instanceof PsiMethod) {
      methodPrefix = member.getContainingClass() instanceof PsiAnonymousClass ? "" : "$" + member.getName();
    }
    else if (member instanceof PsiField && member.getContainingClass() instanceof PsiAnonymousClass) {
      methodPrefix = "";
    }
    else {
      //inside class initializer everywhere or field in a named class
      methodPrefix = "$new";
    }
    return methodPrefix;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PsiMethodCallFilter implements ElementFilter {
  private final @NonNls String myClassName;
  private final @NonNls Set<String> myMethodNames;


  public PsiMethodCallFilter(final @NonNls String className, final @NotNull @NonNls String @NotNull ... methodNames) {
    myClassName = className;
    myMethodNames = Set.of(methodNames);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    if (element instanceof PsiMethodCallExpression callExpression) {
      String name = callExpression.getMethodExpression().getReferenceName();
      if (name == null || !myMethodNames.contains(name)) {
        return false;
      }

      final PsiMethod psiMethod = callExpression.resolveMethod();
      return psiMethod != null &&
             myMethodNames.contains(psiMethod.getName()) &&
             InheritanceUtil.isInheritor(psiMethod.getContainingClass(), myClassName);
    }
    return false;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return PsiMethodCallExpression.class.isAssignableFrom(hintClass);
  }

  @Override
  public @NonNls String toString() {
    return "methodcall(" + myClassName + "." + myMethodNames + ")";
  }
}

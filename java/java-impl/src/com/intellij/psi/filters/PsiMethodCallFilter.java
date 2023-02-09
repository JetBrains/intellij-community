// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Set;

public class PsiMethodCallFilter implements ElementFilter {
  @NonNls private final String myClassName;
  @NonNls private final Set<String> myMethodNames;


  public PsiMethodCallFilter(@NonNls final String className, @NonNls final String... methodNames) {
    myClassName = className;
    myMethodNames = ContainerUtil.set(methodNames);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    if (element instanceof PsiMethodCallExpression callExpression) {
      if (!myMethodNames.contains(callExpression.getMethodExpression().getReferenceName())) {
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

  @NonNls
  public String toString() {
    return "methodcall(" + myClassName + "." + myMethodNames + ")";
  }
}

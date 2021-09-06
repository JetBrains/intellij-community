// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import java.util.Collection;

public final class AnnotationBasedNonBlockingContextChecker implements NonBlockingContextChecker {

  private final Collection<String> myBlockingAnnotations;
  private final Collection<String> myNonBlockingAnnotations;

  public AnnotationBasedNonBlockingContextChecker(@NotNull Collection<String> blockingAnnotations,
                                                  @NotNull Collection<String> nonBlockingAnnotations) {
    myBlockingAnnotations = blockingAnnotations;
    myNonBlockingAnnotations = nonBlockingAnnotations;
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    JavaPsiFacade javaPsi = JavaPsiFacade.getInstance(file.getProject());
    for (String annotation : myNonBlockingAnnotations) {
      if (javaPsi.findClass(annotation, file.getResolveScope()) != null) return true;
    }
    return false;
  }

  @Override
  public boolean isContextNonBlockingFor(@NotNull PsiElement element) {
    UCallExpression callExpression = UastContextKt.toUElement(element, UCallExpression.class);
    if (callExpression == null) return false;

    UMethod callingMethod = UastUtils.getParentOfType(callExpression, UMethod.class);
    if (callingMethod == null) return false;
    PsiMethod psiCallingMethod = callingMethod.getJavaPsi();

    if (AnnotationUtil.findAnnotation(psiCallingMethod, myNonBlockingAnnotations, false) != null) {
      return true;
    }

    if (AnnotationUtil.findAnnotation(psiCallingMethod, myBlockingAnnotations, false) != null) {
      // @Blocking on method overrides @NonBlocking on class
      return false;
    }

    PsiClass containingClass = psiCallingMethod.getContainingClass();
    return containingClass != null
           && AnnotationUtil.findAnnotation(containingClass, myNonBlockingAnnotations, false) != null;
  }
}

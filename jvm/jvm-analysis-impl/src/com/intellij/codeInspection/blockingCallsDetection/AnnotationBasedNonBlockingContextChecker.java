// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import java.util.List;

import static com.intellij.codeInspection.blockingCallsDetection.AnnotationBasedBlockingMethodChecker.hasAnnotation;

public class AnnotationBasedNonBlockingContextChecker implements NonBlockingContextChecker {

  private final List<String> myNonBlockingAnnotations;

  public AnnotationBasedNonBlockingContextChecker(List<String> nonBlockingAnnotations) {
    myNonBlockingAnnotations = nonBlockingAnnotations;
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    if (myNonBlockingAnnotations.isEmpty()) return false;
    PsiClass annotationClass = JavaPsiFacade.getInstance(file.getProject())
      .findClass(BlockingMethodInNonBlockingContextInspection.DEFAULT_NONBLOCKING_ANNOTATION, file.getResolveScope());
    return annotationClass != null;
  }

  @Override
  public boolean isContextNonBlockingFor(@NotNull PsiElement element) {
    UCallExpression callExpression = UastContextKt.toUElement(element, UCallExpression.class);
    if (callExpression == null) return false;

    UMethod callingMethod = UastUtils.getParentOfType(callExpression, UMethod.class);
    if (callingMethod == null) return false;
    PsiMethod psiCallingMethod = callingMethod.getJavaPsi();

    return hasAnnotation(psiCallingMethod, myNonBlockingAnnotations);
  }
}

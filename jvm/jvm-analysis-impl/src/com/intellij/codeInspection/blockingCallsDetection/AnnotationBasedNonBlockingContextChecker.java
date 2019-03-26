// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import java.util.List;

public class AnnotationBasedNonBlockingContextChecker implements NonBlockingContextChecker {

  private final List<String> myNonBlockingAnnotations;

  public AnnotationBasedNonBlockingContextChecker(List<String> nonBlockingAnnotations) {
    myNonBlockingAnnotations = nonBlockingAnnotations;
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    return myNonBlockingAnnotations != null &&
           StreamEx.of(BlockingMethodInNonBlockingContextInspection.DEFAULT_NONBLOCKING_ANNOTATION)
             .append(myNonBlockingAnnotations)
             .anyMatch(annotation -> JavaPsiFacade.getInstance(file.getProject()).findClass(annotation, file.getResolveScope()) != null);
  }

  @Override
  public boolean isContextNonBlockingFor(@NotNull PsiElement element) {
    UCallExpression callExpression = UastContextKt.toUElement(element, UCallExpression.class);
    if (callExpression == null) return false;

    UMethod callingMethod = UastUtils.getParentOfType(callExpression, UMethod.class);
    if (callingMethod == null) return false;
    PsiMethod psiCallingMethod = callingMethod.getJavaPsi();

    return AnnotationUtil.findAnnotation(psiCallingMethod, myNonBlockingAnnotations, false) != null;
  }
}

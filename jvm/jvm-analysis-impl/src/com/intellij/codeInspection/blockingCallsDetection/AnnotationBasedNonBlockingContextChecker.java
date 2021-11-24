// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.blockingCallsDetection.ContextType.Unsure;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import java.util.Collection;

import static com.intellij.codeInspection.blockingCallsDetection.ContextType.Blocking;
import static com.intellij.codeInspection.blockingCallsDetection.ContextType.NonBlocking;

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
  public ContextType computeContextType(@NotNull ElementContext elementContext) {
    UCallExpression callExpression = UastContextKt.toUElement(elementContext.getElement(), UCallExpression.class);
    if (callExpression == null) return Unsure.INSTANCE;

    UMethod callingMethod = UastUtils.getParentOfType(callExpression, UMethod.class);
    if (callingMethod == null) return Unsure.INSTANCE;
    PsiMethod psiCallingMethod = callingMethod.getJavaPsi();

    if (AnnotationUtil.findAnnotation(psiCallingMethod, myNonBlockingAnnotations, false) != null) {
      return NonBlocking.INSTANCE;
    }

    if (AnnotationUtil.findAnnotation(psiCallingMethod, myBlockingAnnotations, false) != null) {
      // @Blocking on method overrides @NonBlocking on class
      return Blocking.INSTANCE;
    }

    PsiClass containingClass = psiCallingMethod.getContainingClass();
    boolean isClassAnnotated = containingClass != null
                && AnnotationUtil.findAnnotation(containingClass, myNonBlockingAnnotations, false) != null;
    return isClassAnnotated ? NonBlocking.INSTANCE : Unsure.INSTANCE;
  }
}

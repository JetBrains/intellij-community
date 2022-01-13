// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class AnnotationBasedBlockingMethodChecker implements BlockingMethodChecker {
  private final Collection<String> myBlockingAnnotations;
  private final Collection<String> myNonBlockingAnnotations;

  public AnnotationBasedBlockingMethodChecker(@NotNull Collection<String> blockingAnnotations,
                                              @NotNull Collection<String> nonBlockingAnnotations) {
    myBlockingAnnotations = blockingAnnotations;
    myNonBlockingAnnotations = nonBlockingAnnotations;
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    JavaPsiFacade javaPsi = JavaPsiFacade.getInstance(file.getProject());
    GlobalSearchScope fileResolveScope = file.getResolveScope();
    for (String annotation : myBlockingAnnotations) {
      if (javaPsi.findClass(annotation, fileResolveScope) != null) return true;
    }
    for (String annotation : myNonBlockingAnnotations) {
      if (javaPsi.findClass(annotation, fileResolveScope) != null) return true;
    }
    return false;
  }

  @Override
  public boolean isMethodBlocking(@NotNull MethodContext context) {
    return isMethodOrClassAnnotated(context.getElement(), myBlockingAnnotations, myNonBlockingAnnotations);
  }

  @Override
  public boolean isMethodNonBlocking(@NotNull MethodContext context) {
    return isMethodOrClassAnnotated(context.getElement(), myNonBlockingAnnotations, myBlockingAnnotations);
  }

  private static boolean isMethodOrClassAnnotated(@NotNull PsiMethod method,
                                                  @NotNull Collection<String> annotations,
                                                  @NotNull Collection<String> denyAnnotations) {
    if (AnnotationUtil.findAnnotation(method, annotations, false) != null) return true;
    // @NonBlocking on method overrides @Blocking on class
    if (AnnotationUtil.findAnnotation(method, denyAnnotations, false) != null) return false;

    PsiClass containingClass = method.getContainingClass();
    return containingClass != null
           && AnnotationUtil.findAnnotation(containingClass, annotations, false) != null;
  }
}

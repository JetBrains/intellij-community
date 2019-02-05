// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnotationBasedBlockingMethodChecker implements BlockingMethodChecker {

  private final List<String> myBlockingAnnotations;

  public AnnotationBasedBlockingMethodChecker(List<String> blockingAnnotations) {
    myBlockingAnnotations = blockingAnnotations;
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    if (myBlockingAnnotations.isEmpty()) return false;
    PsiClass annotationClass = JavaPsiFacade.getInstance(file.getProject())
      .findClass(BlockingMethodInNonBlockingContextInspection.DEFAULT_BLOCKING_ANNOTATION, file.getResolveScope());
    return annotationClass != null;
  }

  @Override
  public boolean isMethodBlocking(@NotNull PsiMethod method) {
    return AnnotationUtil.findAnnotation(method, myBlockingAnnotations, false) != null;
  }
}

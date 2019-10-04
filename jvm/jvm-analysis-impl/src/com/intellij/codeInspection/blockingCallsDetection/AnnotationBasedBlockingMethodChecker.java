// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnotationBasedBlockingMethodChecker implements BlockingMethodChecker {

  private final List<String> myBlockingAnnotations;

  public AnnotationBasedBlockingMethodChecker(List<String> blockingAnnotations) {
    myBlockingAnnotations = blockingAnnotations;
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    return myBlockingAnnotations != null &&
           StreamEx.of(BlockingMethodInNonBlockingContextInspection.DEFAULT_BLOCKING_ANNOTATION)
             .append(myBlockingAnnotations)
             .anyMatch(annotation -> JavaPsiFacade.getInstance(file.getProject()).findClass(annotation, file.getResolveScope()) != null);
  }

  @Override
  public boolean isMethodBlocking(@NotNull PsiMethod method) {
    return AnnotationUtil.findAnnotation(method, myBlockingAnnotations, false) != null;
  }
}

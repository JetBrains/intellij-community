// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class AnnotationBasedBlockingMethodChecker implements BlockingMethodChecker {

  private final List<String> myBlockingAnnotations;

  public AnnotationBasedBlockingMethodChecker(List<String> blockingAnnotations) {
    myBlockingAnnotations = blockingAnnotations;
  }

  @Override
  public boolean isActive(PsiFile file) {
    if (myBlockingAnnotations.isEmpty()) return false;
    PsiClass annotationClass = JavaPsiFacade.getInstance(file.getProject())
      .findClass(BlockingMethodInNonBlockingContextInspection.DEFAULT_BLOCKING_ANNOTATION, file.getResolveScope());
    return annotationClass != null;
  }

  @Override
  public boolean isMethodBlocking(@NotNull PsiMethod method) {
    HashSet<String> setOfAnnotations = Arrays.stream(AnnotationUtil.getAllAnnotations(method, true, null))
      .map(PsiAnnotation::getQualifiedName).collect(Collectors.toCollection(HashSet::new));

    return myBlockingAnnotations.stream()
      .anyMatch(annotation -> setOfAnnotations.contains(annotation));
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultBlockingMethodChecker implements BlockingMethodChecker {

  private final List<String> myBlockingAnnotations;

  public DefaultBlockingMethodChecker(List<String> blockingAnnotations) {
    myBlockingAnnotations = blockingAnnotations;
  }

  @Override
  public boolean isActive(Project project) {
    return !myBlockingAnnotations.isEmpty();
  }

  @Override
  public boolean isMethodBlocking(@NotNull PsiMethod method) {
    HashSet<String> setOfAnnotations = Arrays.stream(AnnotationUtil.getAllAnnotations(method, true, null))
      .map(PsiAnnotation::getQualifiedName).collect(Collectors.toCollection(HashSet::new));

    return myBlockingAnnotations.stream()
      .anyMatch(annotation -> setOfAnnotations.contains(annotation));
  }
}

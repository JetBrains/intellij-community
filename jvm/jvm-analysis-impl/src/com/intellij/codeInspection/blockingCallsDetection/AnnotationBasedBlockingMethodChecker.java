// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    return hasAnnotation(method, myBlockingAnnotations);
  }

  static boolean hasAnnotation(PsiModifierListOwner owner, List<String> annotationsFQNames) {
    boolean hasAnnotation = annotationsFQNames.stream()
      .anyMatch(annotation -> owner.hasAnnotation(annotation));
    if (hasAnnotation) return true;

    PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(owner.getProject()).findExternalAnnotations(owner);
    if (externalAnnotations == null) return false;
    Set<String> externalAnnotationsFQNames =
      Arrays.stream(externalAnnotations).map(PsiAnnotation::getQualifiedName).collect(Collectors.toSet());
    return ContainerUtil.intersects(annotationsFQNames, externalAnnotationsFQNames);
  }
}

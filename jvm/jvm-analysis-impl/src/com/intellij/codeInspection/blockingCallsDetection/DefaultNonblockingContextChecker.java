// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultNonblockingContextChecker implements NonblockingContextChecker {

  private final List<String> myNonblockingAnnotations;

  public DefaultNonblockingContextChecker(List<String> nonblockingAnnotations) {
    myNonblockingAnnotations = nonblockingAnnotations;
  }

  @Override
  public boolean isActive(Project project) {
    if (myNonblockingAnnotations.isEmpty()) return false;
    PsiClass annotationClass = JavaPsiFacade.getInstance(project)
      .findClass(BlockingMethodInNonBlockingContextInspection.DEFAULT_NONBLOCKING_ANNOTATION, ProjectScope.getProjectScope(project));
    return annotationClass != null;
  }

  @Override
  public boolean isContextNonBlockingFor(@NotNull PsiElement element) {
    UCallExpression callExpression = UastContextKt.toUElement(element, UCallExpression.class);
    if (callExpression == null) return false;

    UMethod callingMethod = UastUtils.getParentOfType(callExpression, UMethod.class);
    if (callingMethod == null) return false;
    PsiMethod psiCallingMethod = callingMethod.getJavaPsi();

    return hasNonblockingAnnotation(psiCallingMethod);
  }

  private boolean hasNonblockingAnnotation(PsiMethod method) {
    HashSet<String> setOfAnnotations = Arrays.stream(AnnotationUtil.getAllAnnotations(method, true, null))
      .map(PsiAnnotation::getQualifiedName).collect(Collectors.toCollection(HashSet::new));

    return myNonblockingAnnotations.stream()
      .anyMatch(annotation -> setOfAnnotations.contains(annotation));
  }
}

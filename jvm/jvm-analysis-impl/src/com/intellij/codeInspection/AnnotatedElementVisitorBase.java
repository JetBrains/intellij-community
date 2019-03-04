// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.apiUsage.ApiUsageVisitorBase;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UastContextKt;

import java.util.List;

/**
 * PSI visitor for any language that detects references to API annotated with one of {@code annotations}.
 */
public abstract class AnnotatedElementVisitorBase extends ApiUsageVisitorBase {
  private final boolean myIgnoreInsideImports;
  private final List<String> myAnnotations;

  public AnnotatedElementVisitorBase(boolean ignoreInsideImports, @NotNull List<String> annotations) {
    myIgnoreInsideImports = ignoreInsideImports;
    myAnnotations = annotations;
  }

  /**
   * Reference {@code reference} is found in source code, which resolves to {@code annotatedTarget} that has the
   * following {@code annotations}, which are subset of sought-for annotations passed to constructor.
   */
  public abstract void processAnnotatedTarget(@NotNull PsiReference reference,
                                              @NotNull PsiModifierListOwner annotatedTarget,
                                              @NotNull List<PsiAnnotation> annotations);

  @Override
  public boolean shouldProcessReferences(@NotNull PsiElement element) {
    return !myIgnoreInsideImports || !isInsideImportStatement(element);
  }

  private static boolean isInsideImportStatement(@NotNull PsiElement element) {
    return UastContextKt.getUastParentOfType(element, UImportStatement.class) != null;
  }

  @Override
  public void processReference(@NotNull PsiReference reference) {
    PsiModifierListOwner annotationsOwner = resolveModifierListOwner(reference);
    if (annotationsOwner != null) {
      List<PsiAnnotation> annotations = AnnotationUtil.findAllAnnotations(annotationsOwner, myAnnotations, false);
      if (!annotations.isEmpty()) {
        processAnnotatedTarget(reference, annotationsOwner, annotations);
      }
    }
  }

  @Override
  public void processConstructorInvocation(@NotNull PsiJavaCodeReferenceElement instantiatedClass, @NotNull PsiMethod constructor) {
    List<PsiAnnotation> annotations = AnnotationUtil.findAllAnnotations(constructor, myAnnotations, false);
    if (!annotations.isEmpty()) {
      processAnnotatedTarget(instantiatedClass, constructor, annotations);
    }
  }

  @Nullable
  private static PsiModifierListOwner resolveModifierListOwner(@NotNull PsiReference reference) {
    if (reference instanceof ResolvingHint && !((ResolvingHint)reference).canResolveTo(PsiModifierListOwner.class)) {
      return null;
    }

    PsiElement resolvedElement = reference.resolve();
    if (resolvedElement instanceof PsiModifierListOwner) {
      return (PsiModifierListOwner)resolvedElement;
    }
    return null;
  }
}

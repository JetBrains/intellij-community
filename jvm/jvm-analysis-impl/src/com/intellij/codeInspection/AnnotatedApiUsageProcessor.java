// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;

import java.util.List;

/**
 * Processor of usages of APIs marked with specified annotations, which are detected by {@link AnnotatedElementInspectionBase}.
 */
public interface AnnotatedApiUsageProcessor {
  void processAnnotatedTarget(
    @NotNull UElement sourceNode,
    @NotNull PsiModifierListOwner annotatedTarget,
    @NotNull List<? extends PsiAnnotation> annotations
  );

  void processAnnotatedMethodOverriding(
    @NotNull UMethod method,
    @NotNull PsiMethod overriddenMethod,
    @NotNull List<? extends PsiAnnotation> annotations
  );
}

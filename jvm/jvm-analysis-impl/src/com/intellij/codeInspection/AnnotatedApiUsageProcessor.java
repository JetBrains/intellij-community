// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;

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
}

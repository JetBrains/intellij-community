// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
abstract class JavaReflectionReferenceProvider extends PsiReferenceProvider {
  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof PsiLiteralExpression literal && literal.getValue() instanceof String) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression call) {
        PsiReferenceExpression methodReference = call.getMethodExpression();
        PsiReference[] references = getReferencesByMethod(literal, methodReference, context);
        if (references != null) {
          return references;
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  protected abstract PsiReference @Nullable [] getReferencesByMethod(@NotNull PsiLiteralExpression literalArgument,
                                                                     @NotNull PsiReferenceExpression methodReference,
                                                                     @NotNull ProcessingContext context);
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaReflectionCompletionConfidence extends CompletionConfidence {

  @Override
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    final PsiElement literal = contextElement.getParent();
    if (literal != null &&
        (JavaReflectionReferenceContributor.Holder.PATTERN.accepts(literal) ||
         JavaReflectionReferenceContributor.Holder.CLASS_PATTERN.accepts(literal))) {
      return ThreeState.NO;
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }
}

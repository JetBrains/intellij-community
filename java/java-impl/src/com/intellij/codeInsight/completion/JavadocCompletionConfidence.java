// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiMarkdownReferenceLink;
import com.intellij.psi.javadoc.PsiSnippetAttributeValue;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public final class JavadocCompletionConfidence extends CompletionConfidence {

  @Override
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (psiElement().inside(PsiDocTag.class).accepts(contextElement)
      || psiElement().inside(PsiMarkdownReferenceLink.class).accepts(contextElement)) {
      if (hasKnownReference(psiFile, offset - 1)) {
        return ThreeState.NO;
      }
      if (PlatformPatterns.psiElement(JavaDocTokenType.DOC_TAG_NAME).accepts(contextElement)) {
        return ThreeState.NO;
      }
      if (contextElement.textMatches("#")) {
        return ThreeState.NO;
      }
      if (contextElement instanceof PsiDocToken token && token.getTokenType().equals(JavaDocTokenType.DOC_TAG_ATTRIBUTE_NAME) ||
          contextElement instanceof PsiSnippetAttributeValue) {
        return ThreeState.NO;
      }
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }

  private static boolean hasKnownReference(final PsiFile file, final int offset) {
    PsiReference reference = file.findReferenceAt(offset);
    return reference instanceof PsiMultiReference
           ? ContainerUtil.exists(((PsiMultiReference)reference).getReferences(), JavadocCompletionConfidence::isKnownReference)
           : isKnownReference(reference);
  }

  private static boolean isKnownReference(@Nullable PsiReference reference) {
    return reference instanceof PsiJavaReference || reference != null && reference.getElement() instanceof PsiDocParamRef;
  }
}

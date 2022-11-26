/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class JavadocCompletionConfidence extends CompletionConfidence {

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (psiElement().inside(PsiDocTag.class).accepts(contextElement)) {
      if (hasKnownReference(psiFile, offset - 1)) {
        return ThreeState.NO;
      }
      if (PlatformPatterns.psiElement(JavaDocTokenType.DOC_TAG_NAME).accepts(contextElement)) {
        return ThreeState.NO;
      }
      if (contextElement.textMatches("#")) {
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

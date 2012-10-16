/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class JavaClassReferenceCompletionContributor extends CompletionContributor {
  @Override
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
    JavaClassReference reference = findJavaClassReference(context.getFile(), context.getStartOffset());
    if (reference != null && reference.getExtendClassNames() != null) {
      final PsiReference[] references = reference.getJavaClassReferenceSet().getReferences();
      final PsiReference last = references[references.length - 1];
      context.setReplacementOffset(last.getRangeInElement().getEndOffset() + last.getElement().getTextRange().getStartOffset());
    }
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.SMART) {
      return;
    }

    PsiElement position = parameters.getPosition();
    JavaClassReference reference = findJavaClassReference(position.getContainingFile(), parameters.getOffset());
    if (reference == null) {
      return;
    }

    if (parameters.getCompletionType() == CompletionType.CLASS_NAME) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, result);
    }
    else {
      LegacyCompletionContributor.completeReference(parameters, result);
    }
    result.stopHere();
  }

  @Nullable
  public static JavaClassReference findJavaClassReference(final PsiFile file, final int offset) {
    PsiReference reference = file.findReferenceAt(offset);
    if (reference instanceof PsiMultiReference) {
      for (final PsiReference psiReference : ((PsiMultiReference)reference).getReferences()) {
        if (psiReference instanceof JavaClassReference) {
          return (JavaClassReference)psiReference;
        }
      }
    }
    return reference instanceof JavaClassReference ? (JavaClassReference)reference : null;
  }
}

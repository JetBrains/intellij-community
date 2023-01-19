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

import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaClassReferenceCompletionContributor extends CompletionContributor implements DumbAware {
  @Override
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
    JavaClassReference reference = findJavaClassReference(context.getFile(), context.getStartOffset());
    if (reference != null && !reference.getSuperClasses().isEmpty()) {
      JavaClassReferenceSet set = reference.getJavaClassReferenceSet();
      context.setReplacementOffset(set.getRangeInElement().getEndOffset() + set.getElement().getTextRange().getStartOffset());
    }
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    JavaClassReference reference = findJavaClassReference(position.getContainingFile(), parameters.getOffset());
    if (reference == null) {
      return;
    }

    List<String> extendClassNames = reference.getSuperClasses();
    PsiElement context = reference.getCompletionContext();
    if (!extendClassNames.isEmpty() && context instanceof PsiPackage) {
      if (parameters.getCompletionType() == CompletionType.SMART) {
        JavaClassReferenceSet set = reference.getJavaClassReferenceSet();
        int setStart = set.getRangeInElement().getStartOffset() + set.getElement().getTextRange().getStartOffset();
        String fullPrefix = parameters.getPosition().getContainingFile().getText().substring(setStart, parameters.getOffset());
        reference.processSubclassVariants((PsiPackage)context, ArrayUtil.toStringArray(extendClassNames), result.withPrefixMatcher(fullPrefix));
        return;
      }
      result.addLookupAdvertisement(JavaBundle.message("press.0.to.see.inheritors.of.1",
                                                             KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION),
                                                             StringUtil.join(extendClassNames, ", ")));
    }

    if (parameters.getCompletionType() == CompletionType.SMART) {
      return;
    }

    if (parameters.isExtendedCompletion() || parameters.getCompletionType() == CompletionType.CLASS_NAME) {
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

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find.findUsages;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class FindUsagesHelper {
  static boolean processUsagesInText(@NotNull final PsiElement element,
                                     @NotNull Collection<String> stringToSearch,
                                     @NotNull GlobalSearchScope searchScope,
                                     @NotNull Processor<? super UsageInfo> processor) {
    final TextRange elementTextRange = ReadAction.compute(() -> !element.isValid() || element instanceof PsiCompiledElement ? null : element.getTextRange());
    UsageInfoFactory factory = (usage, startOffset, endOffset) -> {
      if (!element.isValid()) return new UsageInfo(usage, startOffset, endOffset, true);
      if (elementTextRange != null
          && usage.getContainingFile() == element.getContainingFile()
          && elementTextRange.contains(startOffset)
          && elementTextRange.contains(endOffset)) {
        return null;
      }

      PsiReference someReference = usage.findReferenceAt(startOffset);
      if (someReference != null) {
        PsiElement refElement = someReference.getElement();
        for (PsiReference ref : PsiReferenceService.getService().getReferences(refElement, new PsiReferenceService.Hints(element, null))) {
          if (element.getManager().areElementsEquivalent(ref.resolve(), element)) {
            TextRange range = ref.getRangeInElement().shiftRight(refElement.getTextRange().getStartOffset() - usage.getTextRange().getStartOffset());
            return new UsageInfo(usage, range.getStartOffset(), range.getEndOffset(), true);
          }
        }
      }

      return new UsageInfo(usage, startOffset, endOffset, true);
    };
    for (String s : stringToSearch) {
      if (!PsiSearchHelperImpl.processTextOccurrences(element, s, searchScope, processor, factory)) return false;
    }
    return true;
  }
}

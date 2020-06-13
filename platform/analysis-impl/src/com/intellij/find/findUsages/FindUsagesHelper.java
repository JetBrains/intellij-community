// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class FindUsagesHelper {
  private static final Logger LOG = Logger.getInstance(FindUsagesHelper.class);
  /**
   * @deprecated use {@code processUsagesInText(PsiElement, Collection<String>, GlobalSearchScope, boolean, Processor<? super UsageInfo>} instead.
   */
  @Deprecated
  public static boolean processUsagesInText(@NotNull final PsiElement element,
                                            @NotNull Collection<String> stringToSearch,
                                            @NotNull GlobalSearchScope searchScope,
                                            @NotNull Processor<? super UsageInfo> processor) {
    return processUsagesInText(element, stringToSearch, false, searchScope, processor);
  }

  public static boolean processUsagesInText(@NotNull final PsiElement element,
                                            @NotNull Collection<String> stringToSearch,
                                            boolean equivalentReferencesOnly,
                                            @NotNull GlobalSearchScope searchScope,
                                            @NotNull Processor<? super UsageInfo> processor) {
    final TextRange elementTextRange = ReadAction.compute(
      () -> !element.isValid() || element instanceof PsiCompiledElement ? null
                                                                        : element.getTextRange());
    UsageInfoFactory factory = (usage, startOffset, endOffset) -> {
      if (!element.isValid()) return equivalentReferencesOnly ? null : new UsageInfo(usage, startOffset, endOffset, true);
      if (elementTextRange != null
          && usage.getContainingFile() == element.getContainingFile()
          && elementTextRange.contains(startOffset)
          && elementTextRange.contains(endOffset)) {
        return null;
      }

      PsiReference someReference = usage.findReferenceAt(startOffset);
      if (someReference != null) {
        PsiElement refElement = someReference.getElement();
        for (PsiReference ref : PsiReferenceService.getService()
          .getReferences(refElement, new PsiReferenceService.Hints(element, null))) {
          if (element.getManager().areElementsEquivalent(ref.resolve(), element)) {
            TextRange range = ref.getRangeInElement()
              .shiftRight(refElement.getTextRange().getStartOffset() - usage.getTextRange().getStartOffset());
            return new UsageInfo(usage, range.getStartOffset(), range.getEndOffset(), true);
          }
        }
      }

      return equivalentReferencesOnly ? null : new UsageInfo(usage, startOffset, endOffset, true);
    };
    for (String s : stringToSearch) {
      if (!processTextOccurrences(element, s, searchScope, factory, processor)) return false;
    }
    return true;
  }

  public static boolean processTextOccurrences(@NotNull final PsiElement element,
                                                @NotNull String stringToSearch,
                                                @NotNull GlobalSearchScope searchScope,
                                                @NotNull final UsageInfoFactory factory,
                                                @NotNull final Processor<? super UsageInfo> processor) {
    PsiSearchHelper helper = ReadAction.compute(() -> PsiSearchHelper.getInstance(element.getProject()));

    return helper.processUsagesInNonJavaFiles(element, stringToSearch, (psiFile, startOffset, endOffset) -> {
      try {
        UsageInfo usageInfo = ReadAction.compute(() -> factory.createUsageInfo(psiFile, startOffset, endOffset));
        return usageInfo == null || processor.process(usageInfo);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
        return true;
      }
    }, searchScope);
  }
}

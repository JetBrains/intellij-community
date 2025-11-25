// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.util;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesUtil;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

public final class TextOccurrencesUtil {
  private TextOccurrencesUtil() {
  }

  /**
   * @param results must be thread-safe
   */
  public static void addTextOccurrences(@NotNull PsiElement element,
                                        @NotNull String stringToSearch,
                                        @NotNull GlobalSearchScope searchScope,
                                        @NotNull Collection<? super UsageInfo> results,
                                        @NotNull UsageInfoFactory factory) {
    TextOccurrencesUtilBase.addTextOccurrences(element, stringToSearch, searchScope, results, factory);
  }

  /**
   * @param includeReferences usage with a reference at offset would be skipped iff {@code includeReferences == false}
   * @param processor must be thread-safe
   */
  public static boolean processUsagesInStringsAndComments(@NotNull PsiElement element,
                                                          @NotNull SearchScope searchScope,
                                                          @NotNull String stringToSearch,
                                                          boolean includeReferences,
                                                          @NotNull PairProcessor<? super PsiElement, ? super TextRange> processor) {
    return TextOccurrencesUtilBase.processUsagesInStringsAndComments(element, searchScope, stringToSearch, includeReferences, processor);
  }

  /**
   * @param results must be thread-safe
   */
  public static void addUsagesInStringsAndComments(@NotNull PsiElement element,
                                                   @NotNull SearchScope searchScope,
                                                   @NotNull String stringToSearch,
                                                   @NotNull Collection<? super UsageInfo> results,
                                                   @NotNull UsageInfoFactory factory) {
    TextOccurrencesUtilBase.processUsagesInStringsAndComments(element, searchScope, stringToSearch, false, (commentOrLiteral, textRange) -> {
      UsageInfo usageInfo = factory.createUsageInfo(commentOrLiteral, textRange.getStartOffset(), textRange.getEndOffset());
      if (usageInfo != null) {
        results.add(usageInfo);
      }
      return true;
    });
  }

  public static boolean isSearchTextOccurrencesEnabled(@NotNull PsiElement element) {
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(element.getProject())).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, true);
    return FindUsagesUtil.isSearchForTextOccurrencesAvailable(element, false, handler);
  }

  /**
   * @param results must be thread-safe
   */
  public static void findNonCodeUsages(@NotNull PsiElement element,
                                       @NotNull SearchScope searchScope,
                                       @NotNull String stringToSearch,
                                       boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles,
                                       String newQName,
                                       @NotNull Collection<? super UsageInfo> results) {
    if (searchInStringsAndComments || searchInNonJavaFiles) {
      UsageInfoFactory factory = createUsageInfoFactory(element, newQName);

      if (searchInStringsAndComments) {
        addUsagesInStringsAndComments(element, searchScope, stringToSearch, results, factory);
      }

      if (searchInNonJavaFiles && searchScope instanceof GlobalSearchScope gss) {
        addTextOccurrences(element, stringToSearch, gss, results, factory);
      }
    }
  }

  @Contract(pure = true)
  private static @NonNull UsageInfoFactory createUsageInfoFactory(final PsiElement element, final String newQName) {
    return (usage, startOffset, endOffset) -> {
      int start = usage.getTextRange().getStartOffset();
      return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element,
                                     newQName);
    };
  }
}

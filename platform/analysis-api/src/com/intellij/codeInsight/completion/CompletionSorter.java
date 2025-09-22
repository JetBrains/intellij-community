// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementWeigher;
import org.jetbrains.annotations.NotNull;

/**
 * @see CompletionResultSet#withRelevanceSorter(CompletionSorter)
 */
public abstract class CompletionSorter {
  /**
   * @param beforeId id of the weigher which must be run after {@code weighers}
   * @param weighers weighers to add
   * @return a sorter combining the current one and added weighers
   */
  public abstract @NotNull CompletionSorter weighBefore(@NotNull String beforeId, LookupElementWeigher... weighers);

  /**
   * @param afterId  id of the weigher which must be run before {@code weighers}
   * @param weighers weighers to add.
   * @return a sorter combining the current one and added weighers
   */
  public abstract @NotNull CompletionSorter weighAfter(@NotNull String afterId, LookupElementWeigher... weighers);

  /**
   * @param weigher a new weigher to append
   * @return a sorter combining the current sorter and the added weigher
   */
  public abstract @NotNull CompletionSorter weigh(@NotNull LookupElementWeigher weigher);

  public static @NotNull CompletionSorter emptySorter() {
    return CompletionService.getCompletionService().emptySorter();
  }

  public static @NotNull CompletionSorter defaultSorter(@NotNull CompletionParameters parameters, @NotNull PrefixMatcher matcher) {
    return CompletionService.getCompletionService().defaultSorter(parameters, matcher);
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementWeigher;
import org.jetbrains.annotations.NotNull;

/**
 * @see CompletionResultSet#withRelevanceSorter(CompletionSorter)
 */
public abstract class CompletionSorter {
  public abstract CompletionSorter weighBefore(@NotNull String beforeId, LookupElementWeigher... weighers);

  public abstract CompletionSorter weighAfter(@NotNull String afterId, LookupElementWeigher... weighers);

  public abstract CompletionSorter weigh(LookupElementWeigher weigher);

  public static CompletionSorter emptySorter() {
    return CompletionService.getCompletionService().emptySorter();
  }

  public static CompletionSorter defaultSorter(CompletionParameters parameters, PrefixMatcher matcher) {
    return CompletionService.getCompletionService().defaultSorter(parameters, matcher);
  }

}

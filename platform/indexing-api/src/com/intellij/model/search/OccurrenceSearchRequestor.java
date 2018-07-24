// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Essentially the function: {@code SearchRequestCollector -> TextOccurenceProcessor}.
 * The difference is that it returns {@code void} instead of {@code boolean} preventing accidental search cancelling.
 */
public interface OccurrenceSearchRequestor {

  /**
   * Queues other search requests by the word occurence,
   * which was found during the processing of the previous {@link SearchRequestCollector#searchWord word} request.
   *
   * @param element         element which contains the requested word
   * @param offsetInElement offset of the requested word in the {@code element}
   */
  void collectRequests(@NotNull SearchRequestCollector collector, @NotNull PsiElement element, int offsetInElement);
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * IMPORTANT: DO NOT USE IT
 * Supposed to be used ONLY by plugin allowing to sort completion using ml-ranking algorithm.
 * Needed to sort items from different sorters together.
 */
@ApiStatus.Internal
public abstract class CompletionFinalSorter {


  public abstract @NotNull Iterable<? extends LookupElement> sort(@NotNull Iterable<? extends LookupElement> initial,
                                                                  @NotNull CompletionParameters parameters);

  /**
   * For debugging purposes, provide weights by which completion will be sorted.
   */
  public abstract @NotNull Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<? extends LookupElement> elements);


  @ApiStatus.Internal
  public interface Factory {
    @NotNull
    CompletionFinalSorter newSorter();
  }

  public static @NotNull CompletionFinalSorter newSorter() {
    Factory factory = ApplicationManager.getApplication().getService(Factory.class);
    return factory != null ? factory.newSorter() : EMPTY_SORTER;
  }


  private static final CompletionFinalSorter EMPTY_SORTER = new CompletionFinalSorter() {
    @Override
    public @NotNull Iterable<? extends LookupElement> sort(@NotNull Iterable<? extends LookupElement> initial,
                                                           @NotNull CompletionParameters parameters) {
      return initial;
    }

    @Override
    public @NotNull Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<? extends LookupElement> elements) {
      return Collections.emptyMap();
    }
  };
}



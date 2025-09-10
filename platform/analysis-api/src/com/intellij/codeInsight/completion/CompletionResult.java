// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompletionResult {
  private final LookupElement myLookupElement;
  private final PrefixMatcher myMatcher;
  private final CompletionSorter mySorter;

  private CompletionResult(LookupElement lookupElement, PrefixMatcher matcher, CompletionSorter sorter) {
    myLookupElement = lookupElement;
    myMatcher = matcher;
    mySorter = sorter;
  }

  public static @Nullable CompletionResult wrap(@NotNull LookupElement lookupElement, @NotNull PrefixMatcher matcher, @NotNull CompletionSorter sorter) {
    if (matcher.prefixMatches(lookupElement)) {
      return new CompletionResult(lookupElement, matcher, sorter);
    }
    return null;
  }

  public @NotNull PrefixMatcher getPrefixMatcher() {
    return myMatcher;
  }

  public @NotNull CompletionSorter getSorter() {
    return mySorter;
  }

  public @NotNull LookupElement getLookupElement() {
    return myLookupElement;
  }

  public @NotNull CompletionResult withLookupElement(@NotNull LookupElement element) {
    if (!myMatcher.prefixMatches(element)) {
      throw new AssertionError("The new element doesn't match the prefix");
    }
    return new CompletionResult(element, myMatcher, mySorter);
  }

  public boolean isStartMatch() {
    return myMatcher.isStartMatch(myLookupElement);
  }

  @Override
  public String toString() {
    return myLookupElement.toString();
  }
}

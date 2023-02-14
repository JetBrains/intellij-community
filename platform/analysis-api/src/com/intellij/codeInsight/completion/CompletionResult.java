// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompletionResult {
  private final LookupElement myLookupElement;
  private final PrefixMatcher myMatcher;
  private final CompletionSorter mySorter;

  protected CompletionResult(LookupElement lookupElement, PrefixMatcher matcher, CompletionSorter sorter) {
    myLookupElement = lookupElement;
    myMatcher = matcher;
    mySorter = sorter;
  }

  @Nullable
  public static CompletionResult wrap(@NotNull LookupElement lookupElement, @NotNull PrefixMatcher matcher, @NotNull CompletionSorter sorter) {
    if (matcher.prefixMatches(lookupElement)) {
      return new CompletionResult(lookupElement, matcher, sorter);
    }
    return null;
  }

  public PrefixMatcher getPrefixMatcher() {
    return myMatcher;
  }

  public CompletionSorter getSorter() {
    return mySorter;
  }

  public LookupElement getLookupElement() {
    return myLookupElement;
  }

  @NotNull
  public CompletionResult withLookupElement(@NotNull LookupElement element) {
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

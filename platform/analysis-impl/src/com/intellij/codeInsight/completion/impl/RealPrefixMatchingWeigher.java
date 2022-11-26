package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.lookup.WeighingContext;
import org.jetbrains.annotations.NotNull;

public class RealPrefixMatchingWeigher extends LookupElementWeigher {

  public RealPrefixMatchingWeigher() {
    super("prefix", false, true);
  }

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull WeighingContext context) {
    return getBestMatchingDegree(element, CompletionService.getItemMatcher(element, context));
  }

  public static int getBestMatchingDegree(LookupElement element, PrefixMatcher matcher) {
    int max = Integer.MIN_VALUE;
    for (String lookupString : element.getAllLookupStrings()) {
      max = Math.max(max, matcher.matchingDegree(lookupString));
    }
    return max == Integer.MIN_VALUE ? Integer.MAX_VALUE : -max;
  }
}

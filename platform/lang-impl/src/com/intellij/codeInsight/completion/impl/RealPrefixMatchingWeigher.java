package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import org.jetbrains.annotations.NotNull;

/**
* @author Peter
*/
public class RealPrefixMatchingWeigher extends LookupElementWeigher {
  private final CompletionLocation myLocation;

  public RealPrefixMatchingWeigher(CompletionLocation location) {
    super("prefix", false, true);
    myLocation = location;
  }

  @Override
  public Comparable weigh(@NotNull LookupElement element) {
    return getBestMatchingDegree(element, CompletionServiceImpl.getItemMatcher(element, myLocation.getCompletionParameters().getLookup()));
  }

  public static int getBestMatchingDegree(LookupElement element, PrefixMatcher matcher) {
    int max = Integer.MIN_VALUE;
    for (String lookupString : element.getAllLookupStrings()) {
      max = Math.max(max, matcher.matchingDegree(lookupString));
    }
    return -max;
  }
}

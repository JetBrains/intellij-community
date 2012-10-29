package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import org.jetbrains.annotations.NotNull;

/**
* @author Peter
*/
public class PreferStartMatching extends LookupElementWeigher {
  private final CompletionLocation myLocation;

  public PreferStartMatching(CompletionLocation location) {
    super("middleMatching", false, true);
    myLocation = location;
  }

  @Override
  public Comparable weigh(@NotNull LookupElement element) {
    return !CompletionServiceImpl.isStartMatch(element, myLocation.getCompletionParameters().getLookup());
  }
}

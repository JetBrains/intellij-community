package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.lookup.WeighingContext;
import org.jetbrains.annotations.NotNull;

public class PreferStartMatching extends LookupElementWeigher {

  public PreferStartMatching() {
    super("middleMatching", false, true);
  }

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull WeighingContext context) {
    return !CompletionService.isStartMatch(element, context);
  }
}

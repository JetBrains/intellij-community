package com.intellij.codeInsight.completion.methodChains.completion.lookup;

import com.intellij.codeInsight.completion.methodChains.search.ChainRelevance;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public final class WeightableChainLookupElement extends LookupElementDecorator<LookupElement> {
  private final ChainRelevance myChainRelevance;

  public WeightableChainLookupElement(final @NotNull LookupElement delegate, final ChainRelevance relevance) {
    super(delegate);
    myChainRelevance = relevance;
  }

  public ChainRelevance getChainRelevance() {
    return myChainRelevance;
  }
}

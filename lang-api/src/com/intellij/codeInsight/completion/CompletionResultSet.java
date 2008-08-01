/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * {@link com.intellij.codeInsight.completion.CompletionResultSet}s feed on {@link com.intellij.codeInsight.lookup.LookupElement}s,
 * match them against specified
 * {@link com.intellij.codeInsight.completion.PrefixMatcher} and give them to special {@link com.intellij.util.Consumer}
 * (see {@link com.intellij.codeInsight.completion.CompletionService#createResultSet(CompletionParameters, com.intellij.util.Consumer)})
 * for further processing, which usually means
 * they will sooner or later appear in completion list. If they don't, there must be some {@link CompletionContributor}
 * up the invocation stack that filters them out.
 *
 * If you want to change the matching prefix, use {@link #withPrefixMatcher(PrefixMatcher)} or {@link #withPrefixMatcher(String)}
 * to obtain another {@link com.intellij.codeInsight.completion.CompletionResultSet} and give your lookup elements to that one.
 *
 * @author peter
 */
public abstract class CompletionResultSet {
  private final PrefixMatcher myPrefixMatcher;
  private final Consumer<LookupElement> myConsumer;

  protected CompletionResultSet(final PrefixMatcher prefixMatcher, Consumer<LookupElement> consumer) {
    myPrefixMatcher = prefixMatcher;
    myConsumer = consumer;
  }

  protected Consumer<LookupElement> getConsumer() {
    return myConsumer;
  }

  public void addElement(@NotNull final LookupElement result) {
    if (result.isPrefixMatched() || result.setPrefixMatcher(getPrefixMatcher())) {
      myConsumer.consume(result);
    }
  }

  @NotNull public abstract CompletionResultSet withPrefixMatcher(@NotNull PrefixMatcher matcher);

  /**
   * Creates a default camel-hump prefix matcher based on given prefix
   * @param prefix
   */
  @NotNull public abstract CompletionResultSet withPrefixMatcher(@NotNull String prefix);

  @NotNull
  public PrefixMatcher getPrefixMatcher() {
    return myPrefixMatcher;
  }

}

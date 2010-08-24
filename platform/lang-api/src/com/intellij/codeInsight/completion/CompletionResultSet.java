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
 * (see {@link CompletionService#createResultSet(CompletionParameters, com.intellij.util.Consumer, CompletionContributor)})
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
  private final CompletionService myCompletionService = CompletionService.getCompletionService();
  protected final CompletionContributor myContributor;
  private boolean myStopped;

  protected CompletionResultSet(final PrefixMatcher prefixMatcher, Consumer<LookupElement> consumer, CompletionContributor contributor) {
    myPrefixMatcher = prefixMatcher;
    myConsumer = consumer;
    myContributor = contributor;
  }

  protected Consumer<LookupElement> getConsumer() {
    return myConsumer;
  }

  /**
   * If a given element matches the prefix, give it for further processing (which may eventually result in its appearing in the completion list)
   * @param element
   */
  public void addElement(@NotNull final LookupElement element) {
    if (myCompletionService.prefixMatches(element, getPrefixMatcher())) {
      myConsumer.consume(element);
    }
  }

  public void addAll(@NotNull final Iterable<LookupElement> elements) {
    for (LookupElement element : elements) {
      addElement(element);
    }
  }

  @NotNull public abstract CompletionResultSet withPrefixMatcher(@NotNull PrefixMatcher matcher);

  /**
   * Creates a default camel-hump prefix matcher based on given prefix
   * @param prefix
   */
  @NotNull public abstract CompletionResultSet withPrefixMatcher(@NotNull String prefix);

  /**
   * @return A result set with the same prefix, but the lookup strings will be matched case-insensitively. Their lookup strings will
   * remain as they are though, so upon insertion the prefix case will be changed.
   */
  @NotNull public abstract CompletionResultSet caseInsensitive();

  @NotNull
  public PrefixMatcher getPrefixMatcher() {
    return myPrefixMatcher;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public void stopHere() {
    myStopped = true;
  }

  public void runRemainingContributors(CompletionParameters parameters, Consumer<LookupElement> consumer) {
    runRemainingContributors(parameters, consumer, true);
  }

  public void runRemainingContributors(CompletionParameters parameters, Consumer<LookupElement> consumer, final boolean stop) {
    if (stop) {
      stopHere();
    }
    myCompletionService.getVariantsFromContributors(parameters, myContributor, consumer);
  }
}

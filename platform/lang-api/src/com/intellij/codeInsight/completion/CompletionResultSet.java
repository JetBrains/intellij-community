// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

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
public abstract class CompletionResultSet implements Consumer<LookupElement> {
  private final PrefixMatcher myPrefixMatcher;
  private final Consumer<CompletionResult> myConsumer;
  protected final CompletionService myCompletionService = CompletionService.getCompletionService();
  protected final CompletionContributor myContributor;
  private boolean myStopped;

  protected CompletionResultSet(final PrefixMatcher prefixMatcher, Consumer<CompletionResult> consumer, CompletionContributor contributor) {
    myPrefixMatcher = prefixMatcher;
    myConsumer = consumer;
    myContributor = contributor;
  }

  protected Consumer<CompletionResult> getConsumer() {
    return myConsumer;
  }

  @Override
  public void consume(LookupElement element) {
    addElement(element);
  }

  /**
   * If a given element matches the prefix, give it for further processing (which may eventually result in its appearing in the completion list).
   * @see #addAllElements(Iterable) 
   */
  public abstract void addElement(@NotNull final LookupElement element);

  public void passResult(@NotNull CompletionResult result) {
    myConsumer.consume(result);
  }

  public void startBatch() {
    if (myConsumer instanceof BatchConsumer) {
      ((BatchConsumer)myConsumer).startBatch();
    }
  }

  public void endBatch() {
    if (myConsumer instanceof BatchConsumer) {
      ((BatchConsumer)myConsumer).endBatch();
    }
  }

  /**
   * Adds all elements from the given collection that match the prefix for further processing. The elements are processed in batch,
   * so that they'll appear in lookup all together.<p/>
   * This can be useful to ensure predictable order of top suggested elements.
   * Otherwise, when the lookup is shown, most relevant elements processed to that moment are put to the top 
   * and remain there even if more relevant elements appear later. 
   * These "first" elements may differ from completion invocation to completion invocation due to performance fluctuations,
   * resulting in varying preselected item in completion and worse user experience. Using {@code addAllElements} 
   * instead of {@link #addElement(LookupElement)} helps to avoid that.
   */
  public void addAllElements(@NotNull final Iterable<? extends LookupElement> elements) {
    startBatch();
    int seldomCounter = 0;
    for (LookupElement element : elements) {
      seldomCounter++;
      addElement(element);
      if (seldomCounter % 1000 == 0) {
        ProgressManager.checkCanceled();
      }
    }
    endBatch();
  }

  @Contract(value="", pure=true)
  @NotNull public abstract CompletionResultSet withPrefixMatcher(@NotNull PrefixMatcher matcher);

  /**
   * Creates a default camel-hump prefix matcher based on given prefix
   */
  @Contract(value="", pure=true)
  @NotNull public abstract CompletionResultSet withPrefixMatcher(@NotNull String prefix);

  @NotNull
  @Contract(value="", pure=true)
  public abstract CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter);

  public abstract void addLookupAdvertisement(@NotNull String text);

  /**
   * @return A result set with the same prefix, but the lookup strings will be matched case-insensitively. Their lookup strings will
   * remain as they are though, so upon insertion the prefix case will be changed.
   */
  @Contract(value="", pure=true)
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

  public LinkedHashSet<CompletionResult> runRemainingContributors(CompletionParameters parameters, final boolean passResult) {
    final LinkedHashSet<CompletionResult> elements = new LinkedHashSet<>();
    runRemainingContributors(parameters, result -> {
      if (passResult) {
        passResult(result);
      }
      elements.add(result);
    });
    return elements;
  }

  public void runRemainingContributors(CompletionParameters parameters, Consumer<CompletionResult> consumer) {
    runRemainingContributors(parameters, consumer, true);
  }

  public void runRemainingContributors(CompletionParameters parameters, Consumer<CompletionResult> consumer, final boolean stop) {
    if (stop) {
      stopHere();
    }
    myCompletionService.getVariantsFromContributors(parameters, myContributor, new BatchConsumer<CompletionResult>() {
      @Override
      public void startBatch() {
        CompletionResultSet.this.startBatch();
      }

      @Override
      public void endBatch() {
        CompletionResultSet.this.endBatch();
      }

      @Override
      public void consume(CompletionResult result) {
        consumer.consume(result);
      }
    });
  }

  /**
   * Request that the completion contributors be run again when the user changes the prefix so that it becomes equal to the one given.
   */
  public void restartCompletionOnPrefixChange(String prefix) {
    restartCompletionOnPrefixChange(StandardPatterns.string().equalTo(prefix));
  }

  /**
   * Request that the completion contributors be run again when the user changes the prefix in a way satisfied by the given condition.
   */
  public abstract void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition);

  /**
   * Request that the completion contributors be run again when the user changes the prefix in any way.
   */
  public void restartCompletionOnAnyPrefixChange() {
    restartCompletionOnPrefixChange(StandardPatterns.string());
  }

  /**
   * Request that the completion contributors be run again when the user types something into the editor so that no existing lookup elements match that prefix anymore.
   */
  public abstract void restartCompletionWhenNothingMatches();
}

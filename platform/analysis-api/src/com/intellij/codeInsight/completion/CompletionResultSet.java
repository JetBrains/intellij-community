// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

/**
 * {@link CompletionResultSet}s feed on {@link LookupElement}s,
 * match them against specified
 * {@link PrefixMatcher} and give them to special {@link Consumer}
 * for further processing, which usually means
 * they will sooner or later appear in a completion list.
 * If they don't, there must be some {@link CompletionContributor} up the invocation stack that filters them out.
 * <p>
 * If you want to change the matching prefix, use {@link #withPrefixMatcher(PrefixMatcher)} or {@link #withPrefixMatcher(String)}
 * to obtain another {@link CompletionResultSet} and give your lookup elements to that one.
 */
public abstract class CompletionResultSet implements Consumer<LookupElement> {
  private final PrefixMatcher prefixMatcher;
  private final java.util.function.Consumer<? super CompletionResult> consumer;
  protected final CompletionService myCompletionService = CompletionService.getCompletionService();
  @ApiStatus.Internal
  public final CompletionContributor contributor;
  private boolean myStopped;

  protected CompletionResultSet(@NotNull PrefixMatcher prefixMatcher,
                                java.util.function.Consumer<? super CompletionResult> consumer,
                                CompletionContributor contributor) {
    this.prefixMatcher = prefixMatcher;
    this.consumer = consumer;
    this.contributor = contributor;
  }

  @ApiStatus.Internal
  public java.util.function.Consumer<? super CompletionResult> getConsumer() {
    return consumer;
  }

  @Override
  public void consume(LookupElement element) {
    addElement(element);
  }

  /**
   * If a given element matches the prefix, give it for further processing (which may eventually result in its appearing in the completion list).
   * @see #addAllElements(Iterable) 
   */
  public abstract void addElement(final @NotNull LookupElement element);

  public void passResult(@NotNull CompletionResult result) {
    consumer.accept(result);
  }

  public void startBatch() {
    if (consumer instanceof BatchConsumer<?> batch) {
      batch.startBatch();
    }
  }

  public void endBatch() {
    if (consumer instanceof BatchConsumer<?> batch) {
      batch.endBatch();
    }
  }

  /**
   * Adds all elements from the given collection that match the prefix for further processing. The elements are processed in batch,
   * so that they'll appear in lookup all together.<p/>
   * This can be useful to ensure a predictable order of top suggested elements.
   * Otherwise, when the lookup is shown, most relevant elements processed to that moment are put to the top 
   * and remain there even if more relevant elements appear later. 
   * These "first" elements may differ from completion invocation to completion invocation due to performance fluctuations,
   * resulting in varying preselected items in completion and worse user experience. Using {@code addAllElements}
   * instead of {@link #addElement(LookupElement)} helps to avoid that.
   */
  public void addAllElements(final @NotNull Iterable<? extends LookupElement> elements) {
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

  @Contract(pure = true)
  public abstract @NotNull CompletionResultSet withPrefixMatcher(@NotNull PrefixMatcher matcher);

  /**
   * Creates a default camel-hump prefix matcher based on given prefix
   */
  @Contract(pure = true)
  public abstract @NotNull CompletionResultSet withPrefixMatcher(@NotNull String prefix);

  @Contract(pure = true)
  public abstract @NotNull CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter);

  public abstract void addLookupAdvertisement(@NotNull @NlsContexts.PopupAdvertisement String text);

  /**
   * @return A result set with the same prefix, but the lookup strings will be matched case-insensitively. Their lookup strings will
   * remain as they are though, so upon insertion, the prefix case will be changed.
   */
  @Contract(pure = true)
  public abstract @NotNull CompletionResultSet caseInsensitive();

  public @NotNull PrefixMatcher getPrefixMatcher() {
    return prefixMatcher;
  }

  public boolean isStopped() {
    return myStopped;
  }

  /**
   * Stops processing of completion candidates.
   */
  public void stopHere() {
    myStopped = true;
  }

  public @NotNull LinkedHashSet<CompletionResult> runRemainingContributors(CompletionParameters parameters, final boolean passResult) {
    final LinkedHashSet<CompletionResult> elements = new LinkedHashSet<>();
    runRemainingContributors(parameters, result -> {
      if (passResult) {
        passResult(result);
      }
      elements.add(result);
    });
    return elements;
  }

  public void runRemainingContributors(CompletionParameters parameters, Consumer<? super CompletionResult> consumer) {
    runRemainingContributors(parameters, consumer, true);
  }

  public void runRemainingContributors(CompletionParameters parameters, Consumer<? super CompletionResult> consumer, final boolean stop) {
    runRemainingContributors(parameters, consumer, stop, null);
  }

  public void runRemainingContributors(CompletionParameters parameters, Consumer<? super CompletionResult> consumer, final boolean stop,
                                       CompletionSorter customSorter) {
    if (stop) {
      stopHere();
    }
    myCompletionService.getVariantsFromContributors(parameters, contributor, getPrefixMatcher(), new BatchConsumer<>() {
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
    }, customSorter);
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

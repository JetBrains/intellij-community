// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.group.GroupedCompletionContributor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public final @Nullable CompletionContributor contributor;
  private boolean myStopped;

  protected CompletionResultSet(@NotNull PrefixMatcher prefixMatcher,
                                @NotNull java.util.function.Consumer<? super CompletionResult> consumer,
                                @Nullable CompletionContributor contributor) {
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

  /** @deprecated this method does not do anything meaningful anymore. */
  @Deprecated
  public void startBatch() {
    if (consumer instanceof BatchConsumer<?> batch) {
      batch.startBatch();
    }
  }

  /** @deprecated this method does not do anything meaningful anymore. */
  @Deprecated
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

  /**
   * Creates a new CompletionResultSet with the given relevance sorter. Previously contributed results are not affected.
   *
   * @param sorter a new relevance sorter
   * @return a new result set with the given sorter installed
   */
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

  /**
   * Runs all instances of {@link CompletionContributor} applicable to {@code parameters} starting from the current one (excluding it).
   * These contributors won't be run again after this method returns.
   *
   * @param parameters the parameters for which the contributors are run.
   * @param passResult whether the results should be passed to the current CompletionResultSet.
   * @return the set of results from all run contributors.
   */
  public @NotNull LinkedHashSet<CompletionResult> runRemainingContributors(@NotNull CompletionParameters parameters,
                                                                           boolean passResult) {
    LinkedHashSet<CompletionResult> elements = new LinkedHashSet<>();
    runRemainingContributors(parameters, result -> {
      if (passResult) {
        passResult(result);
      }
      elements.add(result);
    });
    return elements;
  }

  /**
   * Runs all instances of {@link CompletionContributor} applicable to {@code parameters} starting from the current one (excluding it).
   * These contributors won't be run again after this method returns.
   *
   * @param parameters the parameters for which the contributors are run.
   * @param consumer   the consumer for the results.
   */
  public void runRemainingContributors(@NotNull CompletionParameters parameters,
                                       @NotNull Consumer<? super CompletionResult> consumer) {
    runRemainingContributors(parameters, consumer, true);
  }

  /**
   * Runs all instances of {@link CompletionContributor} applicable to {@code parameters} starting from the current one (excluding it).
   *
   * @param parameters the parameters for which the contributors are run.
   * @param consumer   the consumer for the results.
   * @param stop       if {@code false} is passed, no contributors will be run after the current contributor finishes.
   * @deprecated use {@link #runRemainingContributors(CompletionParameters, Consumer)} instead.
   * It never should be allowed to pass {@code false} as stop parameter.
   */
  @Deprecated
  public void runRemainingContributors(@NotNull CompletionParameters parameters,
                                       @NotNull Consumer<? super CompletionResult> consumer,
                                       boolean stop) {
    //grouped contributors are not allowed to be used in runRemainingContributors from other contributors
    if (GroupedCompletionContributor.isGroupEnabledInApp() &&
        contributor instanceof GroupedCompletionContributor groupedCompletionContributor &&
        groupedCompletionContributor.groupIsEnabled(parameters)) {
      return;
    }
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
    });
  }

  /**
   * Request that the completion contributors be run again when the user changes the prefix so that it becomes equal to the one given.
   */
  public void restartCompletionOnPrefixChange(@NotNull String prefix) {
    restartCompletionOnPrefixChange(StandardPatterns.string().equalTo(prefix));
  }

  /**
   * Request that the completion contributors be run again when the user changes the prefix in a way satisfied by the given condition.
   */
  public abstract void restartCompletionOnPrefixChange(@NotNull ElementPattern<String> prefixCondition);

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

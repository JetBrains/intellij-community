/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
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
   * If a given element matches the prefix, give it for further processing (which may eventually result in its appearing in the completion list)
   */
  public abstract void addElement(@NotNull final LookupElement element);

  public void passResult(@NotNull CompletionResult result) {
    myConsumer.consume(result);
  }

  public void addAllElements(@NotNull final Iterable<? extends LookupElement> elements) {
    for (LookupElement element : elements) {
      addElement(element);
    }
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
    myCompletionService.getVariantsFromContributors(parameters, myContributor, consumer);
  }

  public void restartCompletionOnPrefixChange(String prefix) {
    restartCompletionOnPrefixChange(StandardPatterns.string().equalTo(prefix));
  }

  public abstract void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition);

  public abstract void restartCompletionWhenNothingMatches();
}

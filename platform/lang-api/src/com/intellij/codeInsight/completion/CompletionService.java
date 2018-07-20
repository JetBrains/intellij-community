// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.Weigher;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * For completion FAQ, see {@link CompletionContributor}.
 *
 * @author peter
 */
public abstract class CompletionService {
  public static final Key<CompletionStatistician> STATISTICS_KEY = Key.create("completion");
  /**
   * A "weigher" extension key (see {@link Weigher}) to sort completion items by priority and move the heaviest to the top of the Lookup.
   */
  public static final Key<CompletionWeigher> RELEVANCE_KEY = Key.create("completion");
  /**
   * A "weigher" extension key (see {@link Weigher}) to sort the whole lookup descending.
   * @deprecated use "completion" relevance key instead
   */
  @Deprecated public static final Key<CompletionWeigher> SORTING_KEY = Key.create("completionSorting");

  public static CompletionService getCompletionService() {
    return ServiceManager.getService(CompletionService.class);
  }

  /**
   * @return Current lookup advertisement text (at the bottom).
   */
  @Nullable
  public abstract String getAdvertisementText();

  /**
   * Set lookup advertisement text (at the bottom) at any time. Will do nothing if no completion process is in progress.
   * @param text
   * @deprecated use {@link CompletionResultSet#addLookupAdvertisement(String)}
   */
  @Deprecated
  public abstract void setAdvertisementText(@Nullable String text);

  /**
   * Creates the completion parameters for the given context.
   *
   * @param caret the selected caret in the given editor
   * @param invocationCount the number of times the user has pressed the code completion shortcut (0 if autopopup)
   * @param parentDisposable The disposable you need to dispose when the completion procedure is over.
   * @return the completion parameters instance
   */
  @SuppressWarnings("unused")
  public abstract CompletionParameters createCompletionParameters(@NotNull Project project,
                                                                  @NotNull Editor editor,
                                                                  @NotNull Caret caret,
                                                                  int invocationCount,
                                                                  CompletionType completionType,
                                                                  @NotNull Disposable parentDisposable);

  /**
   * Run all contributors until any of them returns false or the list is exhausted. If from parameter is not null, contributors
   * will be run starting from the next one after that.
   * @param parameters
   * @param from
   * @param consumer
   * @return
   */
  public void getVariantsFromContributors(final CompletionParameters parameters,
                                          @Nullable final CompletionContributor from,
                                          final Consumer<CompletionResult> consumer) {
    final List<CompletionContributor> contributors = CompletionContributor.forParameters(parameters);

    for (int i = contributors.indexOf(from) + 1; i < contributors.size(); i++) {
      ProgressManager.checkCanceled();
      CompletionContributor contributor = contributors.get(i);

      CompletionResultSet result = createResultSet(parameters, consumer, contributor);
      contributor.fillCompletionVariants(parameters, result);
      if (result.isStopped()) {
        return;
      }
    }
  }

  /**
   * Create a {@link com.intellij.codeInsight.completion.CompletionResultSet} that will filter variants based on default camel-hump
   * {@link com.intellij.codeInsight.completion.PrefixMatcher} and give the filtered variants to consumer.
   * @param parameters
   * @param consumer
   * @param contributor
   * @return
   */
  public abstract CompletionResultSet createResultSet(CompletionParameters parameters, Consumer<CompletionResult> consumer,
                                                      @NotNull CompletionContributor contributor);

  @Nullable
  public abstract CompletionProcess getCurrentCompletion();

  /**
   * The main method that is invoked to collect all the completion variants
   * @param parameters Parameters specifying current completion environment
   * @param consumer This consumer will directly add lookup elements to the lookup
   */
  public void performCompletion(final CompletionParameters parameters, final Consumer<CompletionResult> consumer) {
    final Set<LookupElement> lookupSet = ContainerUtil.newConcurrentSet();

    getVariantsFromContributors(parameters, null, result -> {
      if (lookupSet.add(result.getLookupElement())) {
        consumer.consume(result);
      }
    });
  }

  public abstract CompletionSorter defaultSorter(CompletionParameters parameters, PrefixMatcher matcher);

  public abstract CompletionSorter emptySorter();
}

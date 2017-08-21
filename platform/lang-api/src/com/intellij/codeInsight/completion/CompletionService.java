/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressManager;
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
  public static final Key<CompletionWeigher> SORTING_KEY = Key.create("completionSorting");

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
  public abstract void setAdvertisementText(@Nullable String text);

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
      final CompletionContributor contributor = contributors.get(i);

      final CompletionResultSet result = createResultSet(parameters, consumer, contributor);
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

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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Key;
import com.intellij.psi.Weigher;
import com.intellij.reference.SoftReference;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * For completion FAQ, see {@link CompletionContributor}.
 *
 * @author peter
 */
public abstract class CompletionService {
  private static final Key<SoftReference<CompletionProcess>> INVOLVED_IN_COMPLETION_KEY = Key.create("INVOLVED_IN_COMPLETION_KEY");
  public static final Key<CompletionStatistician> STATISTICS_KEY = Key.create("completion");
  /**
   * A "weigher" extension key (see {@link Weigher}) to sort completion items by priority and move the heaviest to the top of the Lookup.
   */
  public static final Key<CompletionWeigher> RELEVANCE_KEY = Key.create("completion");
  /**
   * A "weigher" extension key (see {@link Weigher}) to sort the whole lookup descending.
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
                                          final Consumer<LookupElement> consumer) {
    final List<CompletionContributor> contributors = CompletionContributor.forParameters(parameters);
    final boolean dumb = DumbService.getInstance(parameters.getPosition().getProject()).isDumb();

    for (int i = contributors.indexOf(from) + 1; i < contributors.size(); i++) {
      final CompletionContributor contributor = contributors.get(i);
      if (dumb && !DumbService.isDumbAware(contributor)) continue;

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
  public abstract CompletionResultSet createResultSet(CompletionParameters parameters, Consumer<LookupElement> consumer,
                                                      @NotNull CompletionContributor contributor);

  @Nullable
  public abstract CompletionProcess getCurrentCompletion();

  /**
   * Checks if a lookup element matches a given prefix matcher. If this element has already been matched successfully during this completion,
   * returns true. If it was matched successfully during another completion, forget about this and re-match with (possibly) new prefix.
   * @param element
   * @param matcher
   * @return should a lookup element be presented to user based on entered prefix?
   */
  public boolean prefixMatches(@NotNull LookupElement element, @NotNull PrefixMatcher matcher) {
    final SoftReference<CompletionProcess> data = element.getUserData(INVOLVED_IN_COMPLETION_KEY);
    final CompletionProcess currentCompletion = getCurrentCompletion();
    if (currentCompletion != null) {
      element.putUserData(INVOLVED_IN_COMPLETION_KEY, new SoftReference<CompletionProcess>(currentCompletion));
      if (data != null) {
        final CompletionProcess oldCompletion = data.get();
        if (oldCompletion != null && oldCompletion != currentCompletion) {
          return element.setPrefixMatcher(matcher);
        }
      }
    } else {
      element.putUserData(INVOLVED_IN_COMPLETION_KEY, null);
    }
    return element.isPrefixMatched() || element.setPrefixMatcher(matcher);
  }

  /**
   * The main method that is invoked to collect all the completion variants
   * @param parameters Parameters specifying current completion environment
   * @param consumer This consumer will directly add lookup elements to the lookup
   * @return all suitable lookup elements
   */
  @NotNull
  public LookupElement[] performCompletion(final CompletionParameters parameters, final Consumer<LookupElement> consumer) {
    final Collection<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();

    getVariantsFromContributors(parameters, null, new Consumer<LookupElement>() {
      public void consume(final LookupElement lookupElement) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (lookupSet.add(lookupElement)) {
              consumer.consume(lookupElement);
            }
          }
        });
      }
    });
    return lookupSet.toArray(new LookupElement[lookupSet.size()]);
  }

  public abstract void correctCaseInsensitiveString(@NotNull final LookupElement element, InsertionContext context);
}

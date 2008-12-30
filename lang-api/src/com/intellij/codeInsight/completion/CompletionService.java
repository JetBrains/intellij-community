/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.psi.Weigher;
import com.intellij.util.Consumer;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * For completion FAQ, see {@link CompletionContributor}.
 *
 * @author peter
 */
public abstract class CompletionService {
  private static final Key<SoftReference<CompletionProcess>> INVOLVED_IN_COMPLETION_KEY = Key.create("INVOLVED_IN_COMPLETION_KEY");
  public static final Key<CompletionStatistician> STATISTICS_KEY = Key.create("completion");
  /**
   * A "weigher" extension key (see {@link Weigher}) to sort completion items.
   */
  public static final Key<CompletionWeigher> WEIGHER_KEY = Key.create("completion");
  /**
   * A "weigher" extension key (see {@link Weigher}) to skip some of the top lookup items that shouldn't be selected, but should still be at the top
   * (so {@link #WEIGHER_KEY} is not applicable).
   */
  public static final Key<CompletionWeigher> PRESELECT_KEY = Key.create("preferredCompletionItem");

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
   * @param contributorsEP
   * @param parameters
   * @param from
   * @param consumer
   * @param <Params>
   * @param <T>
   * @return
   */
  public <Params extends CompletionParameters, T extends AbstractCompletionContributor<Params>> boolean getVariantsFromContributors(
      ExtensionPointName<T> contributorsEP,
      Params parameters, @Nullable T from, Consumer<LookupElement> consumer) {
    return getVariantsFromContributors(Extensions.getExtensions(contributorsEP), parameters, from, consumer);
  }

  public <Params extends CompletionParameters, T extends AbstractCompletionContributor<Params>> boolean getVariantsFromContributors(final T[] contributors,
                                                                                                                                    final Params parameters,
                                                                                                                                    final T from,
                                                                                                                                    final Consumer<LookupElement> consumer) {
    final CompletionResultSet result = createResultSet(parameters, consumer);
    for (int i = Arrays.asList(contributors).indexOf(from) + 1; i < contributors.length; i++) {
      if (!contributors[i].fillCompletionVariants(parameters, result)) {
        return false;
      }
    }
    return from == null;
  }

  /**
   * Create a {@link com.intellij.codeInsight.completion.CompletionResultSet} that will filter variants based on default camel-hump
   * {@link com.intellij.codeInsight.completion.PrefixMatcher} and give the filtered variants to consumer.  
   * @param parameters
   * @param consumer
   * @return
   */
  public abstract CompletionResultSet createResultSet(CompletionParameters parameters, Consumer<LookupElement> consumer);

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

    getVariantsFromContributors(CompletionContributor.EP_NAME, parameters, null, new Consumer<LookupElement>() {
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
}

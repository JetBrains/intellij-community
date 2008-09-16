/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author peter
 */
public abstract class CompletionService {
  public static final Key<CompletionStatistician> STATISTICS_KEY = Key.create("completion");
  public static final Key<CompletionWeigher> WEIGHER_KEY = Key.create("completion");
  public static final Key<CompletionWeigher> PRESELECT_KEY = Key.create("preferredCompletionItem");

  public static CompletionService getCompletionService() {
    return ServiceManager.getService(CompletionService.class);
  }

  @Nullable
  public abstract String getAdvertisementText();

  public abstract void setAdvertisementText(@Nullable String text);

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
}

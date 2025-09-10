// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.group.CompletionGroup;
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.WeighingContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.Weigher;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.completion.group.CompletionGroup.COMPLETION_GROUP_KEY;

/**
 * For completion FAQ, see {@link CompletionContributor}.
 */
public abstract class CompletionService {
  public static final Key<CompletionStatistician> STATISTICS_KEY = Key.create("completion");
  /**
   * A "weigher" extension key (see {@link Weigher}) to sort completion items by priority and move the heaviest to the top of the Lookup.
   */
  public static final Key<CompletionWeigher> RELEVANCE_KEY = Key.create("completion");

  public static CompletionService getCompletionService() {
    return ApplicationManager.getApplication().getService(CompletionService.class);
  }

  /**
   * Set lookup advertisement text (at the bottom) at any time. Will do nothing if no completion process is in progress.
   * @deprecated use {@link CompletionResultSet#addLookupAdvertisement(String)}
   */
  @Deprecated(forRemoval = true)
  public abstract void setAdvertisementText(@Nullable @NlsContexts.PopupAdvertisement String text);

  /**
   * Run all contributors until any of them returns false or the list is exhausted. If {@code from} parameter is not null, contributors
   * will be run starting from the next one after that.
   */
  public void getVariantsFromContributors(@NotNull CompletionParameters parameters,
                                          @Nullable CompletionContributor from,
                                          @NotNull Consumer<? super CompletionResult> consumer) {
    getVariantsFromContributors(parameters, from, createMatcher(suggestPrefix(parameters), false), consumer);
  }

  protected void getVariantsFromContributors(@NotNull CompletionParameters parameters,
                                             @Nullable CompletionContributor from,
                                             @NotNull PrefixMatcher matcher,
                                             @NotNull Consumer<? super CompletionResult> consumer) {
    getVariantsFromContributors(parameters, from, matcher, consumer, null);
  }


  /**
   * Invokes completion contributors that belong to a specific group, collects their completion variants,
   * and passes the results to a specified consumer. Only contributors with their group enabled are processed.
   *
   * @param parameters Specifies the current completion parameters, containing context information
   *                   about the target element and environment where completion is requested.
   * @param matcher    A prefix matcher that filters suggestions based on the provided input prefix.
   * @param consumer   A consumer to handle the completion results produced by active contributors in the group.
   */
  @ApiStatus.Experimental
  protected void getVariantsFromGroupContributors(@NotNull CompletionParameters parameters,
                                                  @NotNull PrefixMatcher matcher,
                                                  @NotNull Consumer<? super CompletionResult> consumer) {
    if (!GroupedCompletionContributor.isGroupEnabledInApp()) {
      return;
    }
    final List<CompletionContributor> contributors = CompletionContributor.forParameters(parameters);
    for (int i = 0; i < contributors.size(); i++) {
      CompletionContributor contributor = contributors.get(i);
      if (!(contributor instanceof GroupedCompletionContributor groupedCompletionContributor &&
            groupedCompletionContributor.groupIsEnabled(parameters))) {
        continue;
      }
      CompletionGroup completionGroup = new CompletionGroup(i, groupedCompletionContributor.getGroupDisplayName());
      CompletionResultSet result = createResultSet(parameters,
                                                   r -> {
                                                     r.getLookupElement().putUserData(COMPLETION_GROUP_KEY, completionGroup);
                                                     consumer.consume(r);
                                                   }, contributor,
                                                   matcher);
      try {
        getVariantsFromContributor(parameters, contributor, result);
      }
      catch (IndexNotReadyException ignore) {
      }
      if (result.isStopped()) {
        return;
      }
    }
  }

  protected void getVariantsFromContributors(@NotNull CompletionParameters parameters,
                                             @Nullable CompletionContributor from,
                                             @NotNull PrefixMatcher matcher,
                                             @NotNull Consumer<? super CompletionResult> consumer,
                                             @Nullable CompletionSorter customSorter) {
    List<CompletionContributor> contributors = CompletionContributor.forParameters(parameters);
    boolean groupEnabledInApp = GroupedCompletionContributor.isGroupEnabledInApp();

    int startingIndex = from != null ? contributors.indexOf(from) + 1 : 0;
    for (int i = startingIndex; i < contributors.size(); i++) {
      ProgressManager.checkCanceled();
      CompletionContributor contributor = contributors.get(i);
      if (groupEnabledInApp &&
          contributor instanceof GroupedCompletionContributor groupedCompletionContributor &&
          groupedCompletionContributor.groupIsEnabled(parameters)) {
        continue;
      }
      CompletionResultSet result = createResultSet(parameters, consumer, contributor, matcher);
      if (customSorter != null) {
        result = result.withRelevanceSorter(customSorter);
      }
      try {
        getVariantsFromContributor(parameters, contributor, result);
      }
      catch (IndexNotReadyException ignore) {
      }
      if (result.isStopped()) {
        return;
      }
    }
  }

  @ApiStatus.Internal
  public void getVariantsFromContributor(@NotNull CompletionParameters params,
                                         @NotNull CompletionContributor contributor,
                                         @NotNull CompletionResultSet result) {
    contributor.fillCompletionVariants(params, result);
  }

  @ApiStatus.Internal
  public abstract @NotNull CompletionResultSet createResultSet(@NotNull CompletionParameters parameters,
                                                               @NotNull Consumer<? super CompletionResult> consumer,
                                                               @NotNull CompletionContributor contributor,
                                                               @NotNull PrefixMatcher matcher);

  protected abstract @NotNull String suggestPrefix(@NotNull CompletionParameters parameters);

  protected abstract @NotNull PrefixMatcher createMatcher(String prefix, boolean typoTolerant);


  public abstract @Nullable CompletionProcess getCurrentCompletion();

  /**
   * The main method that is invoked to collect all the completion variants
   * @param parameters Parameters specifying current completion environment
   * @param consumer The consumer of the completion variants. Pass an instance of {@link BatchConsumer} if you need to receive information
   *                 about item batches generated by each completion contributor.
   */
  public void performCompletion(@NotNull CompletionParameters parameters, @NotNull Consumer<? super CompletionResult> consumer) {
    Set<LookupElement> lookupSet = ConcurrentHashMap.newKeySet();
    AtomicBoolean typoTolerant = new AtomicBoolean();

    BatchConsumer<CompletionResult> batchConsumer = new BatchConsumer<>() {
      @Override
      public void startBatch() {
        if (consumer instanceof BatchConsumer<?> c) {
          c.startBatch();
        }
      }

      @Override
      public void endBatch() {
        if (consumer instanceof BatchConsumer<?> c) {
          c.endBatch();
        }
      }

      @Override
      public void consume(CompletionResult result) {
        if (typoTolerant.get() && result.getLookupElement().getAutoCompletionPolicy() != AutoCompletionPolicy.NEVER_AUTOCOMPLETE) {
          result = result.withLookupElement(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(result.getLookupElement()));
        }
        if (lookupSet.add(result.getLookupElement())) {
          consumer.consume(result);
        }
      }
    };
    String prefix = suggestPrefix(parameters);
    getVariantsFromContributors(parameters, null, createMatcher(prefix, false), batchConsumer);
    getVariantsFromGroupContributors(parameters, createMatcher(prefix, false), batchConsumer);
    if (lookupSet.isEmpty() && prefix.length() > 2) {
      typoTolerant.set(true);
      getVariantsFromContributors(parameters, null, createMatcher(prefix, true), batchConsumer);
      getVariantsFromGroupContributors(parameters, createMatcher(prefix, true), batchConsumer);
    }
  }

  public abstract @NotNull CompletionSorter defaultSorter(@NotNull CompletionParameters parameters, @NotNull PrefixMatcher matcher);

  public abstract @NotNull CompletionSorter emptySorter();

  @ApiStatus.Internal
  public static boolean isStartMatch(@NotNull LookupElement element, @NotNull WeighingContext context) {
    return getItemMatcher(element, context).isStartMatch(element);
  }

  @ApiStatus.Internal
  public static PrefixMatcher getItemMatcher(@NotNull LookupElement element, @NotNull WeighingContext context) {
    PrefixMatcher itemMatcher = context.itemMatcher(element);
    String pattern = context.itemPattern(element);
    if (!pattern.equals(itemMatcher.getPrefix())) {
      return itemMatcher.cloneWithPrefix(pattern);
    }
    return itemMatcher;
  }
}

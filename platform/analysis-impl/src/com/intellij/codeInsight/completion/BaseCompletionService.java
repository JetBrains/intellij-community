// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.*;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.ClassifierFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.Weigher;
import com.intellij.psi.WeighingService;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class BaseCompletionService extends CompletionService {
  private static final Logger LOG = Logger.getInstance(BaseCompletionService.class);

  protected @Nullable CompletionProcess apiCompletionProcess;

  @ApiStatus.Internal
  public static final Key<CompletionContributor> LOOKUP_ELEMENT_CONTRIBUTOR = Key.create("lookup element contributor");
  /**
   * Timestamp when a lookup item was added to the {@link CompletionResultSet}
   */
  public static final Key<Long> LOOKUP_ELEMENT_RESULT_ADD_TIMESTAMP_MILLIS = Key.create("lookup element add time");
  /**
   * The order in which the element was added to the {@link CompletionResultSet}
   */
  public static final Key<Integer> LOOKUP_ELEMENT_RESULT_SET_ORDER = Key.create("lookup element result set order");

  public static final Key<Boolean> FORBID_WORD_COMPLETION = new Key<>("ForbidWordCompletion");

  @Override
  public void performCompletion(@NotNull CompletionParameters parameters, @NotNull Consumer<? super CompletionResult> consumer) {
    apiCompletionProcess = parameters.getProcess();
    try {
      super.performCompletion(parameters, consumer);
    }
    finally {
      apiCompletionProcess = null;
    }
  }

  @Override
  public void setAdvertisementText(@Nullable @NlsContexts.PopupAdvertisement String text) {
    if (text == null) return;

    if (apiCompletionProcess instanceof CompletionProcessEx processEx) {
      processEx.addAdvertisement(text, null);
    }
  }

  @Override
  protected String suggestPrefix(@NotNull CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    final int offset = parameters.getOffset();
    TextRange range = position.getTextRange();
    assert range.containsOffset(offset) : position + "; " + offset + " not in " + range;
    //noinspection deprecation
    return CompletionData.findPrefixStatic(position, offset);
  }

  @Override
  protected @NotNull PrefixMatcher createMatcher(String prefix, boolean typoTolerant) {
    return createMatcher(prefix, true, typoTolerant);
  }

  private static @NotNull CamelHumpMatcher createMatcher(String prefix, boolean caseSensitive, boolean typoTolerant) {
    return new CamelHumpMatcher(prefix, caseSensitive, typoTolerant);
  }

  @Override
  @ApiStatus.Internal
  public @NotNull CompletionResultSet createResultSet(CompletionParameters parameters,
                                                      Consumer<? super CompletionResult> consumer,
                                                      @NotNull CompletionContributor contributor,
                                                      PrefixMatcher matcher) {
    return new BaseCompletionResultSet(consumer, matcher, contributor, parameters, null, null);
  }

  @Override
  public @Nullable CompletionProcess getCurrentCompletion() {
    return apiCompletionProcess;
  }

  protected static class BaseCompletionResultSet extends CompletionResultSet {
    protected final CompletionParameters parameters;
    protected CompletionSorter sorter;
    protected final @Nullable BaseCompletionService.BaseCompletionResultSet myOriginal;
    private int itemCounter = 0;

    protected BaseCompletionResultSet(java.util.function.Consumer<? super CompletionResult> consumer,
                                      PrefixMatcher prefixMatcher,
                                      CompletionContributor contributor,
                                      CompletionParameters parameters,
                                      @Nullable CompletionSorter sorter,
                                      @Nullable BaseCompletionService.BaseCompletionResultSet original) {
      super(prefixMatcher, consumer, contributor);
      this.parameters = parameters;
      this.sorter = sorter;
      myOriginal = original;
    }

    @Override
    public void addElement(@NotNull LookupElement element) {
      ProgressManager.checkCanceled();
      if (!element.isValid()) {
        LOG.error("Invalid lookup element: " + element + " of " + element.getClass() +
                  " in " + parameters.getOriginalFile() + " of " + parameters.getOriginalFile().getClass());
        return;
      }

      sorter = sorter == null ? getCompletionService().defaultSorter(parameters, getPrefixMatcher()) : sorter;

      CompletionResult matched = CompletionResult.wrap(element, getPrefixMatcher(), sorter);
      if (matched != null) {
        passResult(matched);
      }
    }

    @Override
    public void passResult(@NotNull CompletionResult result) {
      LookupElement element = result.getLookupElement();
      element.putUserDataIfAbsent(LOOKUP_ELEMENT_CONTRIBUTOR, contributor);
      element.putUserData(LOOKUP_ELEMENT_RESULT_ADD_TIMESTAMP_MILLIS, System.currentTimeMillis());
      element.putUserData(LOOKUP_ELEMENT_RESULT_SET_ORDER, itemCounter);
      itemCounter += 1;
      super.passResult(result);
    }

    @Override
    public @NotNull CompletionResultSet withPrefixMatcher(@NotNull PrefixMatcher matcher) {
      if (matcher.equals(getPrefixMatcher())) {
        return this;
      }
      return new BaseCompletionResultSet(getConsumer(), matcher, contributor, parameters, sorter, this);
    }

    @Override
    public @NotNull CompletionResultSet withPrefixMatcher(@NotNull String prefix) {
      return withPrefixMatcher(getPrefixMatcher().cloneWithPrefix(prefix));
    }

    @Override
    public void stopHere() {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Completion stopped\n" + DebugUtil.currentStackTrace());
      }
      super.stopHere();
      if (myOriginal != null) {
        myOriginal.stopHere();
      }
    }

    @Override
    public @NotNull CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
      return new BaseCompletionResultSet(getConsumer(), getPrefixMatcher(), contributor, parameters, sorter, this);
    }

    @Override
    public void addLookupAdvertisement(@NotNull @NlsContexts.PopupAdvertisement String text) {
      getCompletionService().setAdvertisementText(text);
    }

    @Override
    public @NotNull CompletionResultSet caseInsensitive() {
      PrefixMatcher matcher = getPrefixMatcher();
      boolean typoTolerant = matcher instanceof CamelHumpMatcher camelHumpMatcher && camelHumpMatcher.isTypoTolerant();
      return withPrefixMatcher(createMatcher(matcher.getPrefix(), false, typoTolerant));
    }

    @Override
    public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {
    }

    @Override
    public void restartCompletionWhenNothingMatches() {
    }
  }

  protected @NotNull CompletionSorterImpl addWeighersBefore(@NotNull CompletionSorterImpl sorter) {
    return sorter;
  }

  protected @NotNull CompletionSorterImpl processStatsWeigher(@NotNull CompletionSorterImpl sorter,
                                                              @NotNull Weigher weigher,
                                                              @NotNull CompletionLocation location) {
    return sorter;
  }

  @Override
  public CompletionSorter defaultSorter(CompletionParameters parameters, PrefixMatcher matcher) {

    CompletionLocation location = new CompletionLocation(parameters);
    CompletionSorterImpl sorter = emptySorter();
    sorter = addWeighersBefore(sorter);
    //sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(LiveTemplateWeigher()))
    sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new PreferStartMatching()));

    for (final Weigher weigher : WeighingService.getWeighers(RELEVANCE_KEY)) {
      final String id = weigher.toString();
      if ("prefix".equals(id)) {
        sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new RealPrefixMatchingWeigher()));
      }
      else if ("stats".equals(id)) {
        sorter = processStatsWeigher(sorter, weigher, location);
      }
      else {
        sorter = sorter.weigh(new LookupElementWeigher(id, true, false) {
          @Override
          public @Nullable Comparable weigh(@NotNull LookupElement element) {
            //noinspection unchecked
            return weigher.weigh(element, location);
          }
        });
      }
    }
    return sorter.withClassifier("priority", true, new ClassifierFactory<>("liftShorter") {
      @Override
      public Classifier<LookupElement> createClassifier(Classifier<LookupElement> next) {
        return new LiftShorterItemsClassifier("liftShorter", next, new LiftShorterItemsClassifier.LiftingCondition(), false);
      }
    });
  }

  @Override
  public CompletionSorterImpl emptySorter() {
    return new CompletionSorterImpl(new ArrayList<>());
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author peter
 */
public class BaseCompletionService extends CompletionService {
  private static final Logger LOG = Logger.getInstance(BaseCompletionService.class);

  @Nullable protected CompletionProcess myApiCompletionProcess;

  public static final Key<CompletionContributor> LOOKUP_ELEMENT_CONTRIBUTOR = Key.create("lookup element contributor");

  @Override
  public void performCompletion(CompletionParameters parameters, Consumer<? super CompletionResult> consumer) {
    myApiCompletionProcess = parameters.getProcess();
    try {
      super.performCompletion(parameters, consumer);
    }
    finally {
      myApiCompletionProcess = null;
    }
  }

  @Override
  public void setAdvertisementText(@Nullable @NlsContexts.PopupAdvertisement String text) {
    if (text == null) return;

    if (myApiCompletionProcess instanceof CompletionProcessEx) {
      ((CompletionProcessEx)myApiCompletionProcess).addAdvertisement(text, null);
    }
  }

  @Override
  protected String suggestPrefix(CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    final int offset = parameters.getOffset();
    TextRange range = position.getTextRange();
    assert range.containsOffset(offset) : position + "; " + offset + " not in " + range;
    //noinspection deprecation
    return CompletionData.findPrefixStatic(position, offset);
  }

  @Override
  @NotNull
  protected PrefixMatcher createMatcher(String prefix, boolean typoTolerant) {
    return createMatcher(prefix, true, typoTolerant);
  }

  @NotNull
  private static CamelHumpMatcher createMatcher(String prefix, boolean caseSensitive, boolean typoTolerant) {
    return new CamelHumpMatcher(prefix, caseSensitive, typoTolerant);
  }

  @Override
  protected CompletionResultSet createResultSet(CompletionParameters parameters, Consumer<? super CompletionResult> consumer,
                                                @NotNull CompletionContributor contributor, PrefixMatcher matcher) {
    return new BaseCompletionResultSet(consumer, matcher, contributor, parameters, null, null);
  }

  @Override
  @Nullable
  public CompletionProcess getCurrentCompletion() {
    return myApiCompletionProcess;
  }

  protected static class BaseCompletionResultSet extends CompletionResultSet {
    protected final CompletionParameters myParameters;
    protected CompletionSorter mySorter;
    @Nullable
    protected final BaseCompletionService.BaseCompletionResultSet myOriginal;

    protected BaseCompletionResultSet(Consumer<? super CompletionResult> consumer, PrefixMatcher prefixMatcher,
                                      CompletionContributor contributor, CompletionParameters parameters,
                                      @Nullable CompletionSorter sorter, @Nullable BaseCompletionService.BaseCompletionResultSet original) {
      super(prefixMatcher, consumer, contributor);
      myParameters = parameters;
      mySorter = sorter;
      myOriginal = original;
    }

    @Override
    public void addElement(@NotNull LookupElement element) {
      ProgressManager.checkCanceled();
      if (!element.isValid()) {
        LOG.error("Invalid lookup element: " + element + " of " + element.getClass() +
                  " in " + myParameters.getOriginalFile() + " of " + myParameters.getOriginalFile().getClass());
        return;
      }

      mySorter = mySorter == null ? getCompletionService().defaultSorter(myParameters, getPrefixMatcher()) : mySorter;

      CompletionResult matched = CompletionResult.wrap(element, getPrefixMatcher(), mySorter);
      if (matched != null) {
        element.putUserData(LOOKUP_ELEMENT_CONTRIBUTOR, myContributor);
        passResult(matched);
      }
    }

    @Override
    public @NotNull CompletionResultSet withPrefixMatcher(@NotNull PrefixMatcher matcher) {
      if (matcher.equals(getPrefixMatcher())) {
        return this;
      }
      return new BaseCompletionResultSet(getConsumer(), matcher, myContributor, myParameters, mySorter, this);
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
      return new BaseCompletionResultSet(getConsumer(), getPrefixMatcher(), myContributor, myParameters, sorter, this);
    }

    @Override
    public void addLookupAdvertisement(@NotNull @NlsContexts.PopupAdvertisement String text) {
      getCompletionService().setAdvertisementText(text);
    }

    @Override
    public @NotNull CompletionResultSet caseInsensitive() {
      PrefixMatcher matcher = getPrefixMatcher();
      boolean typoTolerant = matcher instanceof CamelHumpMatcher && ((CamelHumpMatcher)matcher).isTypoTolerant();
      return withPrefixMatcher(createMatcher(matcher.getPrefix(), false, typoTolerant));
    }

    @Override
    public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {
    }

    @Override
    public void restartCompletionWhenNothingMatches() {
    }
  }

  @NotNull
  protected CompletionSorterImpl addWeighersBefore(@NotNull CompletionSorterImpl sorter) {
    return sorter;
  }

  @NotNull
  protected CompletionSorterImpl processStatsWeigher(@NotNull CompletionSorterImpl sorter,
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
    return sorter.withClassifier("priority", true, new ClassifierFactory<LookupElement>("liftShorter") {
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

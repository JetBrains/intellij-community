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
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Disposer;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.Weigher;
import com.intellij.psi.WeighingService;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author peter
 */
public final class CompletionServiceImpl extends CompletionService{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.impl.CompletionServiceImpl");
  private static volatile CompletionPhase ourPhase = CompletionPhase.NoCompletion;
  private static String ourPhaseTrace;

  public CompletionServiceImpl() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectClosing(Project project) {
        CompletionProgressIndicator indicator = getCurrentCompletion();
        if (indicator != null && indicator.getProject() == project) {
          indicator.closeAndFinish(true);
          setCompletionPhase(CompletionPhase.NoCompletion);
        }
        else if (indicator == null) {
          setCompletionPhase(CompletionPhase.NoCompletion);
        }
      }
    });
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static CompletionServiceImpl getCompletionService() {
    return (CompletionServiceImpl)CompletionService.getCompletionService();
  }

  @Override
  public String getAdvertisementText() {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    return completion == null ? null : ContainerUtil.getFirstItem(completion.getLookup().getAdvertisements());
  }

  @Override
  public void setAdvertisementText(@Nullable final String text) {
    if (text == null) return;
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    if (completion != null) {
      completion.addAdvertisement(text, null);
    }
  }

  @Override
  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<CompletionResult> consumer,
                                             @NotNull final CompletionContributor contributor) {
    final PsiElement position = parameters.getPosition();
    final int offset = parameters.getOffset();
    assert position.getTextRange().containsOffset(offset) : position;
    //noinspection deprecation
    final String prefix = CompletionData.findPrefixStatic(position, offset);
    CamelHumpMatcher matcher = new CamelHumpMatcher(prefix);
    CompletionSorterImpl sorter = defaultSorter(parameters, matcher);
    return new CompletionResultSetImpl(consumer, offset, matcher, contributor,parameters, sorter, null);
  }

  @Override
  public CompletionProgressIndicator getCurrentCompletion() {
    if (isPhase(CompletionPhase.BgCalculation.class, CompletionPhase.ItemsCalculated.class, CompletionPhase.CommittingDocuments.class,
                CompletionPhase.Synchronous.class)) {
      return ourPhase.indicator;
    }
    return null;
  }

  private static class CompletionResultSetImpl extends CompletionResultSet {
    private final int myLengthOfTextBeforePosition;
    private final CompletionParameters myParameters;
    private final CompletionSorterImpl mySorter;
    @Nullable private final CompletionResultSetImpl myOriginal;

    public CompletionResultSetImpl(final Consumer<CompletionResult> consumer, final int lengthOfTextBeforePosition,
                                   final PrefixMatcher prefixMatcher,
                                   CompletionContributor contributor,
                                   CompletionParameters parameters,
                                   @NotNull CompletionSorterImpl sorter,
                                   @Nullable CompletionResultSetImpl original) {
      super(prefixMatcher, consumer, contributor);
      myLengthOfTextBeforePosition = lengthOfTextBeforePosition;
      myParameters = parameters;
      mySorter = sorter;
      myOriginal = original;
    }

    @Override
    public void addElement(@NotNull final LookupElement element) {
      if (!element.isValid()) {
        LOG.error("Invalid lookup element: " + element + " of " + element.getClass() +
                  " in " + myParameters.getOriginalFile() + " of " + myParameters.getOriginalFile().getClass());
        return;
      }

      CompletionResult matched = CompletionResult.wrap(element, getPrefixMatcher(), mySorter);
      if (matched != null) {
        passResult(matched);
      }
    }

    @Override
    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final PrefixMatcher matcher) {
      return new CompletionResultSetImpl(getConsumer(), myLengthOfTextBeforePosition, matcher, myContributor, myParameters, mySorter, this);
    }

    @Override
    public void stopHere() {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Completion stopped\n" + DebugUtil.currentStackTrace());
      }
      super.stopHere();
      if (myOriginal != null) {
        myOriginal.stopHere();
      }
    }

    @Override
    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final String prefix) {
      return withPrefixMatcher(new CamelHumpMatcher(prefix));
    }

    @NotNull
    @Override
    public CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
      return new CompletionResultSetImpl(getConsumer(), myLengthOfTextBeforePosition, getPrefixMatcher(), myContributor, myParameters, (CompletionSorterImpl)sorter,
                                         this);
    }

    @Override
    public void addLookupAdvertisement(@NotNull String text) {
      getCompletionService().setAdvertisementText(text);
    }

    @NotNull
    @Override
    public CompletionResultSet caseInsensitive() {
      return withPrefixMatcher(new CamelHumpMatcher(getPrefixMatcher().getPrefix(), false));
    }

    @Override
    public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {
      final CompletionProgressIndicator indicator = getCompletionService().getCurrentCompletion();
      if (indicator != null) {
        indicator.addWatchedPrefix(myLengthOfTextBeforePosition - getPrefixMatcher().getPrefix().length(), prefixCondition);
      }
    }

    @Override
    public void restartCompletionWhenNothingMatches() {
      final CompletionProgressIndicator indicator = getCompletionService().getCurrentCompletion();
      if (indicator != null) {
        indicator.getLookup().setStartCompletionWhenNothingMatches(true);
      }
    }
  }

  public static boolean assertPhase(Class<? extends CompletionPhase>... possibilities) {
    if (!isPhase(possibilities)) {
      LOG.error(ourPhase + "; set at " + ourPhaseTrace);
      return false;
    }
    return true;
  }

  public static boolean isPhase(Class<? extends CompletionPhase>... possibilities) {
    CompletionPhase phase = getCompletionPhase();
    for (Class<? extends CompletionPhase> possibility : possibilities) {
      if (possibility.isInstance(phase)) {
        return true;
      }
    }
    return false;
  }

  public static void setCompletionPhase(@NotNull CompletionPhase phase) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CompletionPhase oldPhase = getCompletionPhase();
    CompletionProgressIndicator oldIndicator = oldPhase.indicator;
    if (oldIndicator != null && !(phase instanceof CompletionPhase.BgCalculation)) {
      LOG.assertTrue(!oldIndicator.isRunning() || oldIndicator.isCanceled(), "don't change phase during running completion: oldPhase=" + oldPhase);
    }

    Disposer.dispose(oldPhase);
    ourPhase = phase;
    ourPhaseTrace = DebugUtil.currentStackTrace();
  }

  public static CompletionPhase getCompletionPhase() {
//    ApplicationManager.getApplication().assertIsDispatchThread();
    CompletionPhase phase = getPhaseRaw();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
    }
    return phase;
  }

  public static CompletionPhase getPhaseRaw() {
    return ourPhase;
  }

  @Override
  public CompletionSorterImpl defaultSorter(CompletionParameters parameters, final PrefixMatcher matcher) {
    final CompletionLocation location = new CompletionLocation(parameters);

    CompletionSorterImpl sorter = emptySorter();
    sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new DispreferLiveTemplates()));
    sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new PreferStartMatching()));

    for (final Weigher weigher : WeighingService.getWeighers(CompletionService.RELEVANCE_KEY)) {
      final String id = weigher.toString();
      if ("prefix".equals(id)) {
        sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new RealPrefixMatchingWeigher()));
      }
      else if ("stats".equals(id)) {
        sorter = sorter.withClassifier(new ClassifierFactory<LookupElement>("stats") {
          @Override
          public Classifier<LookupElement> createClassifier(Classifier<LookupElement> next) {
            return new StatisticsWeigher.LookupStatisticsWeigher(location, next);
          }
        });
      }
      else {
        sorter = sorter.weigh(new LookupElementWeigher(id, true, false) {
          @Override
          public Comparable weigh(@NotNull LookupElement element) {
            //noinspection unchecked
            return weigher.weigh(element, location);
          }
        });
      }
    }

    return sorter.withClassifier("priority", true, new ClassifierFactory<LookupElement>("liftShorter") {
      @Override
      public Classifier<LookupElement> createClassifier(final Classifier<LookupElement> next) {
        return new LiftShorterItemsClassifier("liftShorter", next, new LiftShorterItemsClassifier.LiftingCondition(), false);
      }
    });
  }

  @Override
  public CompletionSorterImpl emptySorter() {
    return new CompletionSorterImpl(new ArrayList<>());
  }

  public static boolean isStartMatch(LookupElement element, WeighingContext context) {
    return getItemMatcher(element, context).isStartMatch(element);
  }

  static PrefixMatcher getItemMatcher(LookupElement element, WeighingContext context) {
    PrefixMatcher itemMatcher = context.itemMatcher(element);
    String pattern = context.itemPattern(element);
    if (!pattern.equals(itemMatcher.getPrefix())) {
      return itemMatcher.cloneWithPrefix(pattern);
    }
    return itemMatcher;
  }
}

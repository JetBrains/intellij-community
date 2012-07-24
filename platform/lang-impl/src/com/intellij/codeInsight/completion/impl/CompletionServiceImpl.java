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
import com.intellij.psi.PsiFile;
import com.intellij.psi.Weigher;
import com.intellij.psi.WeighingService;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author peter
 */
public class CompletionServiceImpl extends CompletionService{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.impl.CompletionServiceImpl");
  private static volatile CompletionPhase ourPhase = CompletionPhase.NoCompletion;
  private static String ourPhaseTrace;

  public CompletionServiceImpl() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectClosing(Project project) {
        CompletionProgressIndicator indicator = getCurrentCompletion();
        if (indicator != null && indicator.getProject() == project) {
          LookupManager.getInstance(indicator.getProject()).hideActiveLookup();
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
    return completion == null ? null : completion.getLookup().getAdvertisementText();
  }

  public void setAdvertisementText(@Nullable final String text) {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    if (completion != null) {
      completion.getLookup().setAdvertisementText(text);
    }
  }

  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<CompletionResult> consumer,
                                             @NotNull final CompletionContributor contributor) {
    final PsiElement position = parameters.getPosition();
    final String prefix = CompletionData.findPrefixStatic(position, parameters.getOffset());
    final String textBeforePosition = parameters.getPosition().getContainingFile().getText().substring(0, parameters.getOffset());
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (!(indicator instanceof CompletionProgressIndicator)) {
      throw new AssertionError("createResultSet may be invoked only from completion thread: " + indicator + "!=" + getCurrentCompletion() + "; phase set at " + ourPhaseTrace);
    }
    CompletionProgressIndicator process = (CompletionProgressIndicator)indicator;
    CamelHumpMatcher matcher = new CamelHumpMatcher(prefix);
    CompletionSorterImpl sorter = defaultSorter(parameters, matcher);
    return new CompletionResultSetImpl(consumer, textBeforePosition, matcher, contributor,parameters, sorter, process, null);
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
    private final String myTextBeforePosition;
    private final CompletionParameters myParameters;
    private final CompletionSorterImpl mySorter;
    private final CompletionProgressIndicator myProcess;
    @Nullable private final CompletionResultSetImpl myOriginal;

    public CompletionResultSetImpl(final Consumer<CompletionResult> consumer, final String textBeforePosition,
                                   final PrefixMatcher prefixMatcher,
                                   CompletionContributor contributor,
                                   CompletionParameters parameters,
                                   @NotNull CompletionSorterImpl sorter,
                                   @NotNull CompletionProgressIndicator process,
                                   @Nullable CompletionResultSetImpl original) {
      super(prefixMatcher, consumer, contributor);
      myTextBeforePosition = textBeforePosition;
      myParameters = parameters;
      mySorter = sorter;
      myProcess = process;
      myOriginal = original;
    }

    public void addElement(@NotNull final LookupElement element) {
      CompletionResult matched = CompletionResult.wrap(element, getPrefixMatcher(), mySorter);
      if (matched != null) {
        passResult(matched);
      }
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final PrefixMatcher matcher) {
      if (!myTextBeforePosition.endsWith(matcher.getPrefix())) {
        final int len = myTextBeforePosition.length();
        final String fragment = len > 100 ? myTextBeforePosition.substring(len - 100) : myTextBeforePosition;
        PsiFile positionFile = myParameters.getPosition().getContainingFile();
        LOG.error("prefix should be some actual file string just before caret: " + matcher.getPrefix() +
                  "\n text=" + fragment +
                  "\ninjected=" + (InjectedLanguageUtil.getTopLevelFile(positionFile) != positionFile) +
                  "\nlang=" + positionFile.getLanguage());
      }
      return new CompletionResultSetImpl(getConsumer(), myTextBeforePosition, matcher, myContributor, myParameters, mySorter, myProcess, this);
    }

    @Override
    public void stopHere() {
      super.stopHere();
      if (myOriginal != null) {
        myOriginal.stopHere();
      }
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final String prefix) {
      return withPrefixMatcher(new CamelHumpMatcher(prefix));
    }

    @NotNull
    @Override
    public CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
      return new CompletionResultSetImpl(getConsumer(), myTextBeforePosition, getPrefixMatcher(), myContributor, myParameters, (CompletionSorterImpl)sorter, myProcess, this);
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
        indicator.addWatchedPrefix(myTextBeforePosition.length() - getPrefixMatcher().getPrefix().length(), prefixCondition);
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

  public CompletionSorterImpl defaultSorter(CompletionParameters parameters, final PrefixMatcher matcher) {
    final CompletionLocation location = new CompletionLocation(parameters);

    CompletionSorterImpl sorter = emptySorter();
    sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new PreferStartMatching(location)));

    for (final Weigher weigher : WeighingService.getWeighers(CompletionService.RELEVANCE_KEY)) {
      final String id = weigher.toString();
      if ("prefix".equals(id)) {
        sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new PrefixMatchingClassifier(location)));
      }
      else if ("stats".equals(id)) {
        sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(new StatisticsWeigher.LookupStatisticsWeigher(location)));
      }
      else {
        sorter = sorter.weigh(new LookupElementWeigher(id, true, false) {
          @Override
          public Comparable weigh(@NotNull LookupElement element) {
            return weigher.weigh(element, location);
          }
        });
      }

    }

    if (parameters.getCompletionType() == CompletionType.SMART) {
      return sorter;
    }
    
    return sorter.withClassifier("priority", true, new ClassifierFactory<LookupElement>("liftShorter") {
      @Override
      public Classifier<LookupElement> createClassifier(final Classifier<LookupElement> next) {
        return new LiftShorterItemsClassifier(next, new LiftShorterItemsClassifier.LiftingCondition());
      }
    });
  }

  public CompletionSorterImpl emptySorter() {
    return new CompletionSorterImpl(new ArrayList<ClassifierFactory<LookupElement>>());
  }

  private static class PreferStartMatching extends LookupElementWeigher {
    private final CompletionLocation myLocation;

    public PreferStartMatching(CompletionLocation location) {
      super("middleMatching", false, true);
      myLocation = location;
    }

    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      return !isStartMatch(element, myLocation.getCompletionParameters().getLookup());
    }
  }

  public static boolean isStartMatch(LookupElement element, Lookup lookup) {
    PrefixMatcher itemMatcher = getItemMatcher(element, lookup);
    for (String ls : element.getAllLookupStrings()) {
      if (itemMatcher.isStartMatch(ls)) {
        return true;
      }
    }
    return false;
  }

  private static PrefixMatcher getItemMatcher(LookupElement element, Lookup lookup) {
    PrefixMatcher itemMatcher = lookup.itemMatcher(element);
    String pattern = lookup.itemPattern(element);
    if (!pattern.equals(itemMatcher.getPrefix())) {
      return itemMatcher.cloneWithPrefix(pattern);
    }
    return itemMatcher;
  }

  private static class PrefixMatchingClassifier extends LookupElementWeigher {
    private final CompletionLocation myLocation;

    public PrefixMatchingClassifier(CompletionLocation location) {
      super("prefix", false, true);
      myLocation = location;
    }

    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final PrefixMatcher matcher = getItemMatcher(element, myLocation.getCompletionParameters().getLookup());

      int max = Integer.MIN_VALUE;
      for (String lookupString : element.getAllLookupStrings()) {
        max = Math.max(max, matcher.matchingDegree(lookupString));
      }
      return -max;
    }

  }
}

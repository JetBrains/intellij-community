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
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.ClassifierFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
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
  private Throwable myTrace = null;
  private volatile CompletionProgressIndicator myCurrentCompletion;
  private static volatile CompletionPhase ourPhase = CompletionPhase.NoCompletion;
  private static String ourPhaseTrace;

  public CompletionServiceImpl() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectClosing(Project project) {
        setCompletionPhase(CompletionPhase.NoCompletion);
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

  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<LookupElement> consumer,
                                             @NotNull final CompletionContributor contributor) {
    final PsiElement position = parameters.getPosition();
    final String prefix = CompletionData.findPrefixStatic(position, parameters.getOffset());
    final String textBeforePosition = parameters.getPosition().getContainingFile().getText().substring(0, parameters.getOffset());
    CompletionProgressIndicator process = myCurrentCompletion;
    LOG.assertTrue(process != null, "createResultSet may be invoked only during completion");
    CamelHumpMatcher matcher = new CamelHumpMatcher(prefix, true, parameters.relaxMatching());
    CompletionSorterImpl sorter = defaultSorter(parameters, matcher);
    return new CompletionResultSetImpl(consumer, textBeforePosition, matcher, contributor,parameters, sorter, process, null);
  }

  @Override
  public CompletionProgressIndicator getCurrentCompletion() {
    return myCurrentCompletion;
  }

  public void setCurrentCompletion(@Nullable final CompletionProgressIndicator indicator) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (indicator != null) {
      final CompletionProgressIndicator oldCompletion = myCurrentCompletion;
      final Throwable oldTrace = myTrace;
      myCurrentCompletion = indicator;
      myTrace = new Throwable();
      if (oldCompletion != null) {
        throw new RuntimeException(
          "SHe's not dead yet!\nthis=" + indicator + "\ncurrent=" + oldCompletion + "\ntrace=" + StringUtil.getThrowableText(oldTrace));
      }
    } else {
      myCurrentCompletion = null;
    }
  }

  private static class CompletionResultSetImpl extends CompletionResultSet {
    private final String myTextBeforePosition;
    private final CompletionParameters myParameters;
    private final CompletionSorterImpl mySorter;
    private final CompletionProgressIndicator myProcess;
    @Nullable private final CompletionResultSetImpl myOriginal;

    public CompletionResultSetImpl(final Consumer<LookupElement> consumer, final String textBeforePosition,
                                   final PrefixMatcher prefixMatcher,
                                   CompletionContributor contributor,
                                   CompletionParameters parameters,
                                   @NotNull CompletionSorterImpl sorter,
                                   @NotNull CompletionProgressIndicator process,
                                   CompletionResultSetImpl original) {
      super(prefixMatcher, consumer, contributor);
      myTextBeforePosition = textBeforePosition;
      myParameters = parameters;
      mySorter = sorter;
      myProcess = process;
      myOriginal = original;
    }

    public void addElement(@NotNull final LookupElement element) {
      if (element instanceof MatchedLookupElement) {
        getConsumer().consume(element);
        return;
      }

      MatchedLookupElement matched = element.as(MatchedLookupElement.CLASS_CONDITION_KEY);
      if (matched != null) {
        getConsumer().consume(new MatchedLookupElement(element, matched.getMatcher(), matched.getSorter()));
        return;
      }

      PrefixMatcher matcher = getPrefixMatcher();
      if (matcher.prefixMatches(element)) {
        element.setPrefixMatcher(matcher);
        getConsumer().consume(new MatchedLookupElement(element, matcher, mySorter));
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
      boolean relaxed = getPrefixMatcher() instanceof CamelHumpMatcher && ((CamelHumpMatcher)getPrefixMatcher()).isRelaxedMatching();
      return withPrefixMatcher(new CamelHumpMatcher(prefix, true, relaxed));
    }

    @NotNull
    @Override
    public CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
      return new CompletionResultSetImpl(getConsumer(), myTextBeforePosition, getPrefixMatcher(), myContributor, myParameters, (CompletionSorterImpl)sorter, myProcess, this);
    }

    @NotNull
    @Override
    public CompletionResultSet caseInsensitive() {
      boolean relaxed = getPrefixMatcher() instanceof CamelHumpMatcher && ((CamelHumpMatcher)getPrefixMatcher()).isRelaxedMatching();
      return withPrefixMatcher(new CamelHumpMatcher(getPrefixMatcher().getPrefix(), false, relaxed));
    }

    @Override
    public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {
      final CompletionProgressIndicator indicator = getCompletionService().getCurrentCompletion();
      if (indicator != null) {
        indicator.addWatchedPrefix(myTextBeforePosition.length() - getPrefixMatcher().getPrefix().length(), prefixCondition);
      }
    }
  }

  public static void assertPhase(Class<? extends CompletionPhase>... possibilities) {
    if (!isPhase(possibilities)) {
      LOG.error(ourPhase + "; set at " + ourPhaseTrace);
    }
  }

  public static boolean isPhase(Class<? extends CompletionPhase>... possibilities) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
      LOG.assertTrue(!oldIndicator.isRunning() || oldIndicator.isCanceled(), "don't change phase during running completion");
    }

    Disposer.dispose(oldPhase);
    ourPhase = phase;
    ourPhaseTrace = DebugUtil.currentStackTrace();
  }

  public static CompletionPhase getCompletionPhase() {
//    ApplicationManager.getApplication().assertIsDispatchThread();
    CompletionPhase phase = ourPhase;
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
    }
    return phase;
  }

  public CompletionSorterImpl defaultSorter(CompletionParameters parameters, final PrefixMatcher matcher) {
    final CompletionLocation location = new CompletionLocation(parameters);

    CompletionSorterImpl sorter = emptySorter();
    final String prefix = matcher.getPrefix();
    if (!prefix.isEmpty()) {
      final String prefixHumps = StringUtil.capitalsOnly(prefix);
      if (prefixHumps.length() > 0) {
        sorter = sorter.weigh(new LookupElementWeigher("prefixHumps") {
          @NotNull
          @Override
          public Comparable weigh(@NotNull LookupElement element) {
            for (String itemString : element.getAllLookupStrings()) {
              if (StringUtil.capitalsOnly(itemString).startsWith(prefixHumps)) {
                return false;
              }
            }
            return true;
          }
        });
      }
    }

    for (final Weigher weigher : WeighingService.getWeighers(CompletionService.RELEVANCE_KEY)) {
      sorter = sorter.weigh(new LookupElementWeigher(weigher.toString()) {
        @NotNull
        @Override
        public Comparable weigh(@NotNull LookupElement element) {
          return new NegatingComparable(weigher.weigh(element, location));
        }
      });
    }

    return sorter.withClassifier("priority", true, new ClassifierFactory<LookupElement>("liftShorter") {
      @Override
      public Classifier<LookupElement> createClassifier(final Classifier<LookupElement> next) {
        return new LiftShorterItemsClassifier(next);
      }
    });
  }

  public CompletionSorterImpl emptySorter() {
    return new CompletionSorterImpl(new ArrayList<ClassifierFactory<LookupElement>>());
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.Weigher;
import com.intellij.psi.WeighingService;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author peter
 */
public final class CompletionServiceImpl extends CompletionService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.impl.CompletionServiceImpl");
  private static volatile CompletionPhase ourPhase = CompletionPhase.NoCompletion;
  private static Throwable ourPhaseTrace;

  public CompletionServiceImpl() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(Project project) {
        CompletionProgressIndicator indicator = getCurrentCompletion();
        if (indicator != null && indicator.getProject() == project) {
          indicator.closeAndFinish(true);
          setCompletionPhase(CompletionPhase.NoCompletion);
        } else if (indicator == null) {
          setCompletionPhase(CompletionPhase.NoCompletion);
        }
      }
    });
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static CompletionServiceImpl getCompletionService() {
    return (CompletionServiceImpl) CompletionService.getCompletionService();
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
  public CompletionParameters createCompletionParameters(@NotNull Project project,
                                                         @NotNull Editor editor,
                                                         @NotNull Caret caret,
                                                         int invocationCount,
                                                         CompletionType completionType,
                                                         @NotNull Disposable parentDisposable) {
    CompletionInitializationContext context = CompletionInitializationUtil.createCompletionInitializationContext(project, editor, caret,
                                                                                                                     invocationCount, completionType);
    CompletionProcessBase progress = new CompletionProcessBase(context);
    Disposer.register(parentDisposable, progress);
    return CompletionInitializationUtil.prepareCompletionParameters(context, progress);
  }

  @Override
  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<CompletionResult> consumer,
                                             @NotNull final CompletionContributor contributor) {
    final PsiElement position = parameters.getPosition();
    final int offset = parameters.getOffset();
    TextRange range = position.getTextRange();
    assert range.containsOffset(offset) : position + "; " + offset + " not in " + range;
    //noinspection deprecation
    final String prefix = CompletionData.findPrefixStatic(position, offset);
    CamelHumpMatcher matcher = new CamelHumpMatcher(prefix);
    CompletionSorterImpl sorter = defaultSorter(parameters, matcher);
    return new CompletionResultSetImpl(consumer, offset, matcher, contributor, parameters, sorter, null);
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
    @Nullable
    private final CompletionResultSetImpl myOriginal;

    CompletionResultSetImpl(Consumer<CompletionResult> consumer, int lengthOfTextBeforePosition, PrefixMatcher prefixMatcher,
                            CompletionContributor contributor, CompletionParameters parameters,
                            @NotNull CompletionSorterImpl sorter, @Nullable CompletionResultSetImpl original) {
      super(prefixMatcher, consumer, contributor);
      myLengthOfTextBeforePosition = lengthOfTextBeforePosition;
      myParameters = parameters;
      mySorter = sorter;
      myOriginal = original;
    }

    @Override
    public void addAllElements(@NotNull Iterable<? extends LookupElement> elements) {
      CompletionThreadingBase.withBatchUpdate(() -> super.addAllElements(elements), myParameters.getProcess());
    }

    @Override
    public void addElement(@NotNull final LookupElement element) {
      ProgressManager.checkCanceled();
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
      if (matcher.equals(getPrefixMatcher())) {
        return this;
      }
      
      return new CompletionResultSetImpl(getConsumer(), myLengthOfTextBeforePosition, matcher, myContributor, myParameters, mySorter, this);
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
    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final String prefix) {
      return withPrefixMatcher(getPrefixMatcher().cloneWithPrefix(prefix));
    }

    @NotNull
    @Override
    public CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
      return new CompletionResultSetImpl(getConsumer(), myLengthOfTextBeforePosition, getPrefixMatcher(), myContributor, myParameters, (CompletionSorterImpl) sorter,
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
      CompletionProcess process = myParameters.getProcess();
      if (process instanceof CompletionProgressIndicator) {
        ((CompletionProgressIndicator)process).addWatchedPrefix(myLengthOfTextBeforePosition - getPrefixMatcher().getPrefix().length(), prefixCondition);
      }
    }

    @Override
    public void restartCompletionWhenNothingMatches() {
      CompletionProcess process = myParameters.getProcess();
      if (process instanceof CompletionProgressIndicator) {
        ((CompletionProgressIndicator)process).getLookup().setStartCompletionWhenNothingMatches(true);
      }
    }
  }

  @SafeVarargs
  public static void assertPhase(@NotNull Class<? extends CompletionPhase>... possibilities) {
    if (!isPhase(possibilities)) {
      LOG.error(ourPhase + "; set at " + ExceptionUtil.getThrowableText(ourPhaseTrace));
    }
  }

  @SafeVarargs
  public static boolean isPhase(@NotNull Class<? extends CompletionPhase>... possibilities) {
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
    ourPhaseTrace = new Throwable();
  }

  public static CompletionPhase getCompletionPhase() {
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
      } else if ("stats".equals(id)) {
        sorter = sorter.withClassifier(new ClassifierFactory<LookupElement>("stats") {
          @Override
          public Classifier<LookupElement> createClassifier(Classifier<LookupElement> next) {
            return new StatisticsWeigher.LookupStatisticsWeigher(location, next);
          }
        });
      } else {
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

  @SuppressWarnings("unused")
  public CompletionLookupArranger createLookupArranger(CompletionParameters parameters) {
    return new CompletionLookupArrangerImpl(parameters).withAllItemsVisible();
  }

  @SuppressWarnings("unused")
  public void handleCompletionItemSelected(CompletionParameters parameters,
                                           LookupElement lookupElement,
                                           PrefixMatcher prefixMatcher,
                                           char completionChar) {

    LookupImpl.insertLookupString(parameters.getPosition().getProject(),
                                  parameters.getEditor(),
                                  lookupElement,
                                  prefixMatcher, prefixMatcher.getPrefix(), prefixMatcher.getPrefix().length());
    CodeCompletionHandlerBase handler =
      CodeCompletionHandlerBase.createHandler(parameters.getCompletionType(), true, parameters.isAutoPopup(), true);
    handler.handleCompletionElementSelected(parameters, lookupElement, completionChar);
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

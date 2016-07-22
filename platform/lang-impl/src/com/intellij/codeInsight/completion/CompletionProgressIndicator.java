/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class CompletionProgressIndicator extends ProgressIndicatorBase implements CompletionProcess, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionProgressIndicator");
  private final Editor myEditor;
  @NotNull
  private final Caret myCaret;
  private final CompletionParameters myParameters;
  private final CodeCompletionHandlerBase myHandler;
  private final LookupImpl myLookup;
  private final MergingUpdateQueue myQueue;
  private final Update myUpdate = new Update("update") {
    @Override
    public void run() {
      updateLookup();
      myQueue.setMergingTimeSpan(300);
    }
  };
  private final Semaphore myFreezeSemaphore;
  private final OffsetMap myOffsetMap;
  private final List<Pair<Integer, ElementPattern<String>>> myRestartingPrefixConditions = ContainerUtil.createLockFreeCopyOnWriteList();
  private final LookupAdapter myLookupListener = new LookupAdapter() {
    @Override
    public void lookupCanceled(final LookupEvent event) {
      finishCompletionProcess(true);
    }
  };
  private volatile int myCount;
  private volatile boolean myHasPsiElements;
  private boolean myLookupUpdated;
  private final ConcurrentMap<LookupElement, CompletionSorterImpl> myItemSorters =
    ContainerUtil.createConcurrentWeakMap(ContainerUtil.identityStrategy());
  private final PropertyChangeListener myLookupManagerListener;
  private final Queue<Runnable> myAdvertiserChanges = new ConcurrentLinkedQueue<>();
  private final List<CompletionResult> myDelayedMiddleMatches = ContainerUtil.newArrayList();
  private final int myStartCaret;

  public CompletionProgressIndicator(final Editor editor,
                                     @NotNull Caret caret,
                                     CompletionParameters parameters,
                                     CodeCompletionHandlerBase handler,
                                     Semaphore freezeSemaphore,
                                     final OffsetMap offsetMap,
                                     boolean hasModifiers,
                                     LookupImpl lookup) {
    myEditor = editor;
    myCaret = caret;
    myParameters = parameters;
    myHandler = handler;
    myFreezeSemaphore = freezeSemaphore;
    myOffsetMap = offsetMap;
    myLookup = lookup;
    myStartCaret = myEditor.getCaretModel().getOffset();

    myAdvertiserChanges.offer(() -> myLookup.getAdvertiser().clearAdvertisements());

    myLookup.setArranger(new CompletionLookupArranger(parameters, this));

    myLookup.addLookupListener(myLookupListener);
    myLookup.setCalculating(true);

    myLookupManagerListener = evt -> {
      if (evt.getNewValue() != null) {
        LOG.error("An attempt to change the lookup during completion, phase = " + CompletionServiceImpl.getCompletionPhase());
      }
    };
    LookupManager.getInstance(getProject()).addPropertyChangeListener(myLookupManagerListener);

    myQueue = new MergingUpdateQueue("completion lookup progress", 100, true, myEditor.getContentComponent());
    myQueue.setPassThrough(false);

    ApplicationManager.getApplication().assertIsDispatchThread();
    Disposer.register(this, offsetMap);

    if (hasModifiers && !ApplicationManager.getApplication().isUnitTestMode()) {
      trackModifiers();
    }
  }

  public void itemSelected(@Nullable LookupElement lookupItem, char completionChar) {
    boolean dispose = lookupItem == null;
    finishCompletionProcess(dispose);
    if (dispose) return;

    setMergeCommand();

    myHandler.lookupItemSelected(this, lookupItem, completionChar, myLookup.getItems());
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public int getSelectionEndOffset() {
    return getOffsetMap().getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  void duringCompletion(CompletionInitializationContext initContext) {
    if (isAutopopupCompletion()) {
      if (shouldPreselectFirstSuggestion(myParameters)) {
        if (!CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS) {
          myLookup.setFocusDegree(LookupImpl.FocusDegree.SEMI_FOCUSED);
          if (FeatureUsageTracker.getInstance().isToBeAdvertisedInLookup(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_CONTROL_DOT, getProject())) {
            String dotShortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_DOT);
            if (StringUtil.isNotEmpty(dotShortcut)) {
              addAdvertisement("Press " + dotShortcut + " to choose the selected (or first) suggestion and insert a dot afterwards", null);
            }
          }
        } else {
          myLookup.setFocusDegree(LookupImpl.FocusDegree.FOCUSED);
        }
      }
      if (!myEditor.isOneLineMode() &&
          FeatureUsageTracker.getInstance()
            .isToBeAdvertisedInLookup(CodeCompletionFeatures.EDITING_COMPLETION_CONTROL_ARROWS, getProject())) {
        String downShortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_LOOKUP_DOWN);
        String upShortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_LOOKUP_UP);
        if (StringUtil.isNotEmpty(downShortcut) && StringUtil.isNotEmpty(upShortcut)) {
          addAdvertisement(downShortcut + " and " + upShortcut + " will move caret down and up in the editor", null);
        }
      }
    } else if (DumbService.isDumb(getProject())) {
      addAdvertisement("The results might be incomplete while indexing is in progress", MessageType.WARNING.getPopupBackground());
    }

    ProgressManager.checkCanceled();

    if (!initContext.getOffsetMap().wasModified(CompletionInitializationContext.IDENTIFIER_END_OFFSET)) {
      try {
        final int selectionEndOffset = initContext.getSelectionEndOffset();
        final PsiReference reference = TargetElementUtil.findReference(myEditor, selectionEndOffset);
        if (reference != null) {
          final int replacementOffset = findReplacementOffset(selectionEndOffset, reference);
          final Document document = initContext.getEditor().getDocument();
          if (replacementOffset > document.getTextLength()) {
            LOG.error("Invalid replacementOffset: " + replacementOffset + " returned by reference " + reference + " of " + reference.getClass() + 
                      "; doc=" + document + 
                      "; doc actual=" + (document == initContext.getFile().getViewProvider().getDocument()) + 
                      "; doc committed=" + PsiDocumentManager.getInstance(getProject()).isCommitted(document));
          } else {
            initContext.setReplacementOffset(replacementOffset);
          }
        }
      }
      catch (IndexNotReadyException ignored) {
      }
    }

    for (CompletionContributor contributor : CompletionContributor.forLanguage(initContext.getPositionLanguage())) {
      ProgressManager.checkCanceled();
      if (DumbService.getInstance(initContext.getProject()).isDumb() && !DumbService.isDumbAware(contributor)) {
        continue;
      }

      contributor.duringCompletion(initContext);
    }
  }

  @NotNull
  CompletionSorterImpl getSorter(LookupElement element) {
    return myItemSorters.get(element);
  }

  @Override
  public void dispose() {
  }

  private static int findReplacementOffset(int selectionEndOffset, PsiReference reference) {
    final List<TextRange> ranges = ReferenceRange.getAbsoluteRanges(reference);
    for (TextRange range : ranges) {
      if (range.contains(selectionEndOffset)) {
        return range.getEndOffset();
      }
    }

    return selectionEndOffset;
  }


  void scheduleAdvertising() {
    if (myLookup.isAvailableToUser()) {
      return;
    }
    for (final CompletionContributor contributor : CompletionContributor.forParameters(myParameters)) {
      if (!myLookup.isCalculating() && !myLookup.isVisible()) return;

      @SuppressWarnings("deprecation") String s = contributor.advertise(myParameters);
      if (s != null) {
        addAdvertisement(s, null);
      }
    }
  }

  private boolean isOutdated() {
    return CompletionServiceImpl.getCompletionPhase().indicator != this;
  }

  private void trackModifiers() {
    assert !isAutopopupCompletion();

    final JComponent contentComponent = myEditor.getContentComponent();
    contentComponent.addKeyListener(new ModifierTracker(contentComponent));
  }

  public void setMergeCommand() {
    CommandProcessor.getInstance().setCurrentCommandGroupId(getCompletionCommandName());
  }

  private String getCompletionCommandName() {
    return "Completion" + hashCode();
  }

  public boolean showLookup() {
    return updateLookup();
  }

  public CompletionParameters getParameters() {
    return myParameters;
  }

  public CodeCompletionHandlerBase getHandler() {
    return myHandler;
  }

  public LookupImpl getLookup() {
    return myLookup;
  }

  private boolean updateLookup() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isOutdated() || !shouldShowLookup()) return false;

    while (true) {
      Runnable action = myAdvertiserChanges.poll();
      if (action == null) break;
      action.run();
    }

    if (!myLookupUpdated) {
      if (myLookup.getAdvertisements().isEmpty() && !isAutopopupCompletion() && !DumbService.isDumb(getProject())) {
        DefaultCompletionContributor.addDefaultAdvertisements(myParameters, myLookup, myHasPsiElements);
      }
      myLookup.getAdvertiser().showRandomText();
    }

    boolean justShown = false;
    if (!myLookup.isShown()) {
      if (hideAutopopupIfMeaningless()) {
        return false;
      }

      if (Registry.is("dump.threads.on.empty.lookup") && myLookup.isCalculating() && myLookup.getItems().isEmpty()) {
        PerformanceWatcher.getInstance().dumpThreads("emptyLookup/", true);
      }

      if (!myLookup.showLookup()) {
        return false;
      }
      justShown = true;
    }
    myLookupUpdated = true;
    myLookup.refreshUi(true, justShown);
    hideAutopopupIfMeaningless();
    if (justShown) {
      myLookup.ensureSelectionVisible(true);
    }
    return true;
  }

  private boolean shouldShowLookup() {
    if (isAutopopupCompletion()) {
      if (myCount == 0) {
        return false;
      }
      if (myLookup.isCalculating() && Registry.is("ide.completion.delay.autopopup.until.completed")) {
        return false;
      }
    }
    return true;
  }

  final boolean isInsideIdentifier() {
    return getIdentifierEndOffset() != getSelectionEndOffset();
  }

  public int getIdentifierEndOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
  }

  void addItem(final CompletionResult item) {
    if (!isRunning()) return;
    ProgressManager.checkCanceled();

    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (!unitTestMode) {
      LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
    }

    LookupElement lookupElement = item.getLookupElement();
    if (!myHasPsiElements && lookupElement.getPsiElement() != null) {
      myHasPsiElements = true;
    }

    boolean allowMiddleMatches = myCount > CompletionLookupArranger.MAX_PREFERRED_COUNT * 2;
    if (allowMiddleMatches) {
      addDelayedMiddleMatches();
    }

    myItemSorters.put(lookupElement, (CompletionSorterImpl)item.getSorter());
    if (item.isStartMatch() || allowMiddleMatches) {
      addItemToLookup(item);
    } else {
      synchronized (myDelayedMiddleMatches) {
        myDelayedMiddleMatches.add(item);
      }
    }
  }

  private void addItemToLookup(CompletionResult item) {
    if (!myLookup.addItem(item.getLookupElement(), item.getPrefixMatcher())) {
      return;
    }
    myCount++;

    if (myCount == 1) {
      JobScheduler.getScheduler().schedule(myFreezeSemaphore::up, 300, TimeUnit.MILLISECONDS);
    }
    myQueue.queue(myUpdate);
  }

  void addDelayedMiddleMatches() {
    ArrayList<CompletionResult> delayed;
    synchronized (myDelayedMiddleMatches) {
      if (myDelayedMiddleMatches.isEmpty()) return;
      delayed = ContainerUtil.newArrayList(myDelayedMiddleMatches);
      myDelayedMiddleMatches.clear();
    }
    for (CompletionResult item : delayed) {
      ProgressManager.checkCanceled();
      addItemToLookup(item);
    }
  }

  public void closeAndFinish(boolean hideLookup) {
    if (!myLookup.isLookupDisposed()) {
      Lookup lookup = LookupManager.getActiveLookup(myEditor);
      LOG.assertTrue(lookup == myLookup, "lookup changed: " + lookup + "; " + this);
    }
    myLookup.removeLookupListener(myLookupListener);
    finishCompletionProcess(true);
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());

    if (hideLookup) {
      myLookup.hideLookup(true);
    }
  }

  private void finishCompletionProcess(boolean disposeOffsetMap) {
    cancel();

    ApplicationManager.getApplication().assertIsDispatchThread();
    Disposer.dispose(myQueue);
    LookupManager.getInstance(getProject()).removePropertyChangeListener(myLookupManagerListener);

    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    LOG.assertTrue(currentCompletion == this, currentCompletion + "!=" + this);

    CompletionServiceImpl
      .assertPhase(CompletionPhase.BgCalculation.class, CompletionPhase.ItemsCalculated.class, CompletionPhase.Synchronous.class,
                   CompletionPhase.CommittingDocuments.class);
    CompletionPhase oldPhase = CompletionServiceImpl.getCompletionPhase();
    if (oldPhase instanceof CompletionPhase.CommittingDocuments) {
      LOG.assertTrue(((CompletionPhase.CommittingDocuments)oldPhase).isRestartingCompletion(), oldPhase);
      ((CompletionPhase.CommittingDocuments)oldPhase).replaced = true;
    }
    CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    if (disposeOffsetMap) {
      disposeIndicator();
    }
  }

  void disposeIndicator() {
    Disposer.dispose(this);
  }

  @TestOnly
  public static void cleanupForNextTest() {
    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.finishCompletionProcess(true);
      CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());
    }
    else {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }
    CompletionLookupArranger.cancelLastCompletionStatisticsUpdate();
  }

  @Override
  public void stop() {
    super.stop();

    myQueue.cancelAllUpdates();
    myFreezeSemaphore.up();

    GuiUtils.invokeLaterIfNeeded(() -> {
      final CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
      if (!(phase instanceof CompletionPhase.BgCalculation) || phase.indicator != this) return;

      LOG.assertTrue(!getProject().isDisposed(), "project disposed");

      if (myEditor.isDisposed()) {
        myLookup.hideLookup(false);
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
        return;
      }

      if (myEditor instanceof EditorWindow) {
        LOG.assertTrue(((EditorWindow)myEditor).getInjectedFile().isValid(), "injected file !valid");
        LOG.assertTrue(((DocumentWindow)myEditor.getDocument()).isValid(), "docWindow !valid");
      }
      PsiFile file = myLookup.getPsiFile();
      LOG.assertTrue(file == null || file.isValid(), "file !valid");

      myLookup.setCalculating(false);

      if (myCount == 0) {
        myLookup.hideLookup(false);
        if (!isAutopopupCompletion()) {
          final CompletionProgressIndicator current = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
          LOG.assertTrue(current == null, current + "!=" + this);

          handleEmptyLookup(!((CompletionPhase.BgCalculation)phase).modifiersChanged);
        }
      }
      else {
        CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(this));
        updateLookup();
      }
    }, myQueue.getModalityState());
  }

  private boolean hideAutopopupIfMeaningless() {
    if (!myLookup.isLookupDisposed() && isAutopopupCompletion() && !myLookup.isSelectionTouched() && !myLookup.isCalculating()) {
      myLookup.refreshUi(true, false);
      final List<LookupElement> items = myLookup.getItems();

      for (LookupElement item : items) {
        if (!myLookup.itemPattern(item).equals(item.getLookupString())) {
          return false;
        }

        if (item.isValid() && item.isWorthShowingInAutoPopup()) {
          return false;
        }
      }

      myLookup.hideLookup(false);
      LOG.assertTrue(CompletionServiceImpl.getCompletionService().getCurrentCompletion() == null);
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      return true;
    }
    return false;
  }

  public boolean fillInCommonPrefix(final boolean explicit) {
    if (isInsideIdentifier()) {
      return false;
    }

    final Boolean aBoolean = new WriteCommandAction<Boolean>(getProject()) {
      @Override
      protected void run(@NotNull Result<Boolean> result) throws Throwable {
        if (!explicit) {
          setMergeCommand();
        }
        try {
          result.setResult(myLookup.fillInCommonPrefix(explicit));
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }.execute().getResultObject();
    return aBoolean.booleanValue();
  }

  public void restorePrefix(@NotNull final Runnable customRestore) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        setMergeCommand();

        customRestore.run();
      }
    }.execute();
  }

  public int nextInvocationCount(int invocation, boolean reused) {
    return reused ? Math.max(getParameters().getInvocationCount() + 1, 2) : invocation;
  }

  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public Caret getCaret() {
    return myCaret;
  }

  public boolean isRepeatedInvocation(CompletionType completionType, Editor editor) {
    if (completionType != myParameters.getCompletionType() || editor != myEditor) {
      return false;
    }

    if (isAutopopupCompletion() && !myLookup.mayBeNoticed()) {
      return false;
    }

    return true;
  }

  @Override
  public boolean isAutopopupCompletion() {
    return myParameters.getInvocationCount() == 0;
  }

  @NotNull
  public Project getProject() {
    return ObjectUtils.assertNotNull(myEditor.getProject());
  }

  public void addWatchedPrefix(int startOffset, ElementPattern<String> restartCondition) {
    myRestartingPrefixConditions.add(Pair.create(startOffset, restartCondition));
  }

  public void prefixUpdated() {
    final int caretOffset = myEditor.getCaretModel().getOffset();
    if (caretOffset < myStartCaret) {
      scheduleRestart();
      myRestartingPrefixConditions.clear();
      return;
    }

    final CharSequence text = myEditor.getDocument().getCharsSequence();
    for (Pair<Integer, ElementPattern<String>> pair : myRestartingPrefixConditions) {
      int start = pair.first;
      if (caretOffset >= start && start >= 0) {
        final String newPrefix = text.subSequence(start, caretOffset).toString();
        if (pair.second.accepts(newPrefix)) {
          scheduleRestart();
          myRestartingPrefixConditions.clear();
          return;
        }
      }
    }

    hideAutopopupIfMeaningless();
  }

  public void scheduleRestart() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (ApplicationManager.getApplication().isUnitTestMode() && !CompletionAutoPopupHandler.ourTestingAutopopup) {
      closeAndFinish(true);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      new CodeCompletionHandlerBase(myParameters.getCompletionType()).invokeCompletion(getProject(), myEditor, myParameters.getInvocationCount());
      return;
    }

    cancel();

    final CompletionProgressIndicator current = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (this != current) {
      LOG.error(current + "!=" + this);
    }

    hideAutopopupIfMeaningless();

    CompletionPhase oldPhase = CompletionServiceImpl.getCompletionPhase();
    if (oldPhase instanceof CompletionPhase.CommittingDocuments) {
      ((CompletionPhase.CommittingDocuments)oldPhase).replaced = true;
    }

    final CompletionPhase.CommittingDocuments phase = new CompletionPhase.CommittingDocuments(this, myEditor);
    CompletionServiceImpl.setCompletionPhase(phase);
    phase.ignoreCurrentDocumentChange();

    final Project project = getProject();
    AutoPopupController.runTransactionWithEverythingCommitted(project, () -> {
      if (phase.checkExpired()) return;

      CompletionAutoPopupHandler.invokeCompletion(myParameters.getCompletionType(),
                                                  isAutopopupCompletion(), project, myEditor, myParameters.getInvocationCount(),
                                                  true);
    });
  }

  @Override
  public String toString() {
    return "CompletionProgressIndicator[count=" +
           myCount +
           ",phase=" +
           CompletionServiceImpl.getCompletionPhase() +
           "]@" +
           System.identityHashCode(this);
  }

  protected void handleEmptyLookup(final boolean awaitSecondInvocation) {
    if (isAutopopupCompletion() && ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    LOG.assertTrue(!isAutopopupCompletion());

    if (ApplicationManager.getApplication().isUnitTestMode() || !myHandler.invokedExplicitly) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      return;
    }

    for (final CompletionContributor contributor : CompletionContributor.forParameters(getParameters())) {
      final String text = contributor.handleEmptyLookup(getParameters(), getEditor());
      if (StringUtil.isNotEmpty(text)) {
        LightweightHint hint = showErrorHint(getProject(), getEditor(), text);
        CompletionServiceImpl.setCompletionPhase(
          awaitSecondInvocation ? new CompletionPhase.NoSuggestionsHint(hint, this) : CompletionPhase.NoCompletion);
        return;
      }
    }
    CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
  }

  private static LightweightHint showErrorHint(Project project, Editor editor, String text) {
    final LightweightHint[] result = {null};
    final EditorHintListener listener = (project1, hint, flags) -> result[0] = hint;
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorHintListener.TOPIC, listener);
    assert text != null;
    HintManager.getInstance().showErrorHint(editor, StringUtil.escapeXml(text), HintManager.UNDER);
    connection.disconnect();
    return result[0];
  }

  private static boolean shouldPreselectFirstSuggestion(CompletionParameters parameters) {
    if (!Registry.is("ide.completion.autopopup.choose.by.enter")) {
      return false;
    }

    if (Registry.is("ide.completion.lookup.element.preselect.depends.on.context")) {
      for (CompletionPreselectionBehaviourProvider provider : Extensions.getExtensions(CompletionPreselectionBehaviourProvider.EP_NAME)) {
        if (!provider.shouldPreselectFirstSuggestion(parameters)) {
          return false;
        }
      }
    }

    return true;
  }

  void startCompletion(final CompletionInitializationContext initContext) {
    boolean sync = ApplicationManager.getApplication().isUnitTestMode() && !CompletionAutoPopupHandler.ourTestingAutopopup;
    final CompletionThreading strategy = sync ? new SyncCompletion() : new AsyncCompletion();

    strategy.startThread(ProgressWrapper.wrap(this), this::scheduleAdvertising);
    final WeighingDelegate weigher = strategy.delegateWeighing(this);

    class CalculateItems implements Runnable {
      @Override
      public void run() {
        try {
          calculateItems(initContext, weigher);
        }
        catch (ProcessCanceledException ignore) {
          cancel(); // some contributor may just throw PCE; if indicator is not canceled everything will hang
        }
        catch (Throwable t) {
          cancel();
          LOG.error(t);
        }
      }
    }
    strategy.startThread(this, new CalculateItems());
  }

  private void calculateItems(CompletionInitializationContext initContext, WeighingDelegate weigher) {
    duringCompletion(initContext);
    ProgressManager.checkCanceled();

    CompletionService.getCompletionService().performCompletion(myParameters, weigher);
    ProgressManager.checkCanceled();

    weigher.waitFor();
    ProgressManager.checkCanceled();
  }

  public void addAdvertisement(@NotNull final String text, @Nullable final Color bgColor) {
    myAdvertiserChanges.offer(() -> myLookup.addAdvertisement(text, bgColor));

    myQueue.queue(myUpdate);
  }

  private static class ModifierTracker extends KeyAdapter {
    private final JComponent myContentComponent;

    public ModifierTracker(JComponent contentComponent) {
      myContentComponent = contentComponent;
    }

    @Override
    public void keyPressed(KeyEvent e) {
      processModifier(e);
    }

    @Override
    public void keyReleased(KeyEvent e) {
      processModifier(e);
    }

    private void processModifier(KeyEvent e) {
      final int code = e.getKeyCode();
      if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT || code == KeyEvent.VK_SHIFT) {
        myContentComponent.removeKeyListener(this);
        final CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
        if (phase instanceof CompletionPhase.BgCalculation) {
          ((CompletionPhase.BgCalculation)phase).modifiersChanged = true;
        }
        else if (phase instanceof CompletionPhase.InsertedSingleItem) {
          CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
        }
      }
    }
  }
}

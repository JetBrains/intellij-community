// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeWithMe.ClientId;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class CompletionProgressIndicator extends ProgressIndicatorBase implements CompletionProcessEx, Disposable {
  private static final Logger LOG = Logger.getInstance(CompletionProgressIndicator.class);
  private final Editor myEditor;
  private final @NotNull Caret myCaret;
  private @Nullable CompletionParameters myParameters;
  private final CodeCompletionHandlerBase handler;
  private final CompletionLookupArrangerImpl myArranger;
  private final CompletionType myCompletionType;
  private final int myInvocationCount;
  private OffsetsInFile myHostOffsets;
  private final LookupImpl lookup;
  private final MergingUpdateQueue queue;
  private final Update myUpdate = new Update("update") {
    @Override
    public void run() {
      WriteIntentReadAction.run((Runnable)() -> updateLookup());
      queue.setMergingTimeSpan(ourShowPopupGroupingTime);
    }
  };
  private final Semaphore freezeSemaphore = new Semaphore(1);
  private final Semaphore finishSemaphore = new Semaphore(1);
  private final @NotNull OffsetMap myOffsetMap;
  private final Set<Pair<Integer, ElementPattern<String>>> myRestartingPrefixConditions = ConcurrentHashMap.newKeySet();
  private final LookupListener myLookupListener = new LookupListener() {
    @Override
    public void lookupCanceled(final @NotNull LookupEvent event) {
      finishCompletionProcess(true);
    }
  };

  private static int ourInsertSingleItemTimeSpan = 300;

  //temp external setters to make Rider autopopup more reactive
  private static int ourShowPopupGroupingTime = 300;
  private static int ourShowPopupAfterFirstItemGroupingTime = 100;

  private volatile int count;
  private enum LookupAppearancePolicy {
    /**
     * The default strategy for lookup appearance.
     * After elements are added to the lookup, it will appear after a certain period of time,
     * the duration of which depends on the platform's internal logic.
     */
    DEFAULT,
    /**
     * In this strategy, elements can be added to the lookup, but the lookup itself will not appear.
     * To eventually open the lookup, the state should transition either to DEFAULT or ON_FIRST_POSSIBILITY.
     * Transitioning to DEFAULT, the lookup will appear after some time, based on platform's logic.
     * Transitioning to ON_FIRST_POSSIBILITY, the lookup will attempt to appear as soon as possible.
     */
    POSTPONED,
    /**
     * This strategy forces the lookup to appear as soon as possible.
     * If there's at least one element in the lookup already, it will open nearly immediately.
     * Otherwise, the lookup will open almost immediately following the addition of a new element.
     */
    ON_FIRST_POSSIBILITY,
  }
  private volatile LookupAppearancePolicy myLookupAppearancePolicy = LookupAppearancePolicy.DEFAULT;
  private volatile boolean myHasPsiElements;
  private boolean myLookupUpdated;
  private final PropertyChangeListener myLookupManagerListener;
  private final Queue<Runnable> myAdvertiserChanges = new ConcurrentLinkedQueue<>();
  private final List<CompletionResult> delayedMiddleMatches = new ArrayList<>();
  private final int myStartCaret;
  private final CompletionThreadingBase threading;
  private final Object myLock = ObjectUtils.sentinel("CompletionProgressIndicator");

  private final EmptyCompletionNotifier myEmptyCompletionNotifier;

  /**
   * Unfreeze immediately after N-th item is added to the lookup.
   * -1 means this functionality is disabled.
   */
  private volatile int myUnfreezeAfterNItems = -1;

  CompletionProgressIndicator(Editor editor, @NotNull Caret caret, int invocationCount,
                              CodeCompletionHandlerBase handler, @NotNull OffsetMap offsetMap, @NotNull OffsetsInFile hostOffsets,
                              boolean hasModifiers, @NotNull LookupImpl lookup) {
    myEditor = editor;
    myCaret = caret;
    this.handler = handler;
    myCompletionType = handler.completionType;
    myInvocationCount = invocationCount;
    myOffsetMap = offsetMap;
    myHostOffsets = hostOffsets;
    this.lookup = lookup;
    myStartCaret = myEditor.getCaretModel().getOffset();
    threading = ApplicationManager.getApplication().isWriteAccessAllowed() || this.handler.isTestingCompletionQualityMode()
                  ? new SyncCompletion()
                  : new AsyncCompletion(editor.getProject());

    myAdvertiserChanges.offer(() -> this.lookup.getAdvertiser().clearAdvertisements());

    myArranger = new CompletionLookupArrangerImpl(this);
    this.lookup.setArranger(myArranger);

    this.lookup.addLookupListener(myLookupListener);
    this.lookup.setCalculating(true);

    myEmptyCompletionNotifier = LightEdit.owns(editor.getProject()) ? LightEditUtil.createEmptyCompletionNotifier() :
                                new ProjectEmptyCompletionNotifier();

    myLookupManagerListener = evt -> {
      @Nullable Lookup newLookup = (Lookup)evt.getNewValue();
      if (newLookup != null && newLookup.getEditor() == myEditor) {
        LOG.error("An attempt to change the lookup during completion, phase = " + CompletionServiceImpl.getCompletionPhase());
      }
    };
    LookupManager.getInstance(getProject()).addPropertyChangeListener(myLookupManagerListener);

    queue = new MergingUpdateQueue("completion lookup progress", ourShowPopupAfterFirstItemGroupingTime, true, myEditor.getContentComponent());

    ThreadingAssertions.assertEventDispatchThread();

    if (hasModifiers && !ApplicationManager.getApplication().isUnitTestMode()) {
      trackModifiers();
    }
  }

  @Override
  public void itemSelected(@Nullable LookupElement lookupItem, char completionChar) {
    boolean dispose = lookupItem == null;
    finishCompletionProcess(dispose);
    if (dispose) return;

    setMergeCommand();

    handler.lookupItemSelected(this, lookupItem, completionChar, lookup.getItems());
  }

  @Override
  @SuppressWarnings("WeakerAccess")
  public @NotNull OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  @Override
  public @NotNull OffsetsInFile getHostOffsets() {
    return myHostOffsets;
  }

  private void duringCompletion(CompletionInitializationContext initContext, CompletionParameters parameters) {
    PsiUtilCore.ensureValid(parameters.getPosition());
    if (isAutopopupCompletion() && shouldPreselectFirstSuggestion(parameters)) {
      LookupFocusDegree degree = CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars()
                                 ? LookupFocusDegree.FOCUSED
                                 : LookupFocusDegree.SEMI_FOCUSED;
      lookup.setLookupFocusDegree(degree);
    }
    addDefaultAdvertisements(parameters);

    ProgressManager.checkCanceled();

    Document document = initContext.getEditor().getDocument();
    if (!initContext.getOffsetMap().wasModified(CompletionInitializationContext.IDENTIFIER_END_OFFSET)) {
      try {
        final int selectionEndOffset = initContext.getSelectionEndOffset();
        final PsiReference reference = TargetElementUtil.findReference(myEditor, selectionEndOffset);
        if (reference != null) {
          final int replacementOffset = findReplacementOffset(selectionEndOffset, reference);
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

    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      for (CompletionContributor contributor :
        CompletionContributor.forLanguageHonorDumbness(initContext.getPositionLanguage(), initContext.getProject())) {
        ProgressManager.checkCanceled();
        contributor.duringCompletion(initContext);
      }
    });
    if (document instanceof DocumentWindow) {
      myHostOffsets = new OffsetsInFile(initContext.getFile(), initContext.getOffsetMap()).toTopLevelFile();
    }
  }


  private void addDefaultAdvertisements(CompletionParameters parameters) {
    if (DumbService.isDumb(getProject())) {
      addAdvertisement(IdeBundle.message("dumb.mode.results.might.be.incomplete"), AllIcons.General.Warning);
      return;
    }

    String enterShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
    String tabShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
    addAdvertisement(CodeInsightBundle.message("completion.ad.press.0.to.insert.1.to.replace", enterShortcut, tabShortcut), null);

    advertiseTabReplacement(parameters);
    if (isAutopopupCompletion()) {
      if (shouldPreselectFirstSuggestion(parameters) && !CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars()) {
        advertiseCtrlDot();
      }
      advertiseCtrlArrows();
    }
  }

  private void advertiseTabReplacement(CompletionParameters parameters) {
    if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_REPLACE) &&
      myOffsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != myOffsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET)) {
      String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
      if (StringUtil.isNotEmpty(shortcut)) {
        addAdvertisement(CodeInsightBundle.message("completion.ad.use.0.to.overwrite", shortcut), null);
      }
    }
  }

  private void advertiseCtrlDot() {
    if (FeatureUsageTracker
      .getInstance().isToBeAdvertisedInLookup(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_CONTROL_DOT, getProject())) {
      String dotShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_DOT);
      if (StringUtil.isNotEmpty(dotShortcut)) {
        addAdvertisement(CodeInsightBundle.message("completion.ad.press.0.to.choose.with.dot", dotShortcut), null);
      }
    }
  }

  private void advertiseCtrlArrows() {
    if (!myEditor.isOneLineMode() &&
        FeatureUsageTracker.getInstance()
          .isToBeAdvertisedInLookup(CodeCompletionFeatures.EDITING_COMPLETION_CONTROL_ARROWS, getProject())) {
      String downShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_LOOKUP_DOWN);
      String upShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_LOOKUP_UP);
      if (StringUtil.isNotEmpty(downShortcut) && StringUtil.isNotEmpty(upShortcut)) {
        addAdvertisement(CodeInsightBundle.message("completion.ad.moving.caret.down.and.up.in.the.editor", downShortcut, upShortcut), null);
      }
    }
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


  void scheduleAdvertising(CompletionParameters parameters) {
    if (lookup.isAvailableToUser()) {
      return;
    }

    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      for (CompletionContributor contributor : CompletionContributor.forParameters(parameters)) {
        if (!lookup.isCalculating() && !lookup.isVisible()) {
          return;
        }

        @SuppressWarnings("removal")
        String s = contributor.advertise(parameters);
        if (s != null) {
          addAdvertisement(s, null);
        }
      }
    });
  }

  private boolean isOutdated() {
    return CompletionServiceImpl.getCompletionPhase().indicator != this;
  }

  private void trackModifiers() {
    assert !isAutopopupCompletion();

    final JComponent contentComponent = myEditor.getContentComponent();
    contentComponent.addKeyListener(new ModifierTracker(contentComponent));
  }

  void setMergeCommand() {
    CommandProcessor.getInstance().setCurrentCommandGroupId(getCompletionCommandName());
  }

  private @NonNls String getCompletionCommandName() {
    return "Completion" + hashCode();
  }

  void showLookup() {
    updateLookup();
  }

  // non-null when running generators and adding elements to lookup
  @Override
  public @Nullable CompletionParameters getParameters() {
    return myParameters;
  }

  @Override
  public void setParameters(@NotNull CompletionParameters parameters) {
    myParameters = parameters;
  }

  @Override
  public @NotNull LookupImpl getLookup() {
    return lookup;
  }

  /**
   * Set lookup appearance policy to default.
   * @see LookupAppearancePolicy
   */
  public void defaultLookupAppearance() {
    myLookupAppearancePolicy = LookupAppearancePolicy.DEFAULT;
  }

  /**
   * Postpone lookup appearance.
   * @see LookupAppearancePolicy
   */
  public void postponeLookupAppearance() {
    myLookupAppearancePolicy = LookupAppearancePolicy.POSTPONED;
  }

  /**
   * Use this function to show the lookup window as soon as possible.
   * Right now or after, when at least one element will be added to the lookup.
   * @see LookupAppearancePolicy
   */
  public void showLookupAsSoonAsPossible() {
    myLookupAppearancePolicy = LookupAppearancePolicy.ON_FIRST_POSSIBILITY;
    openLookupLater();
  }

  private void openLookupLater() {
    ApplicationManager.getApplication()
      .invokeLater(this::showLookup, obj -> lookup.getShownTimestampMillis() != 0L || lookup.isLookupDisposed());
  }

  void withSingleUpdate(Runnable action) {
    myArranger.batchUpdate(action);
  }

  private void updateLookup() {
    ThreadingAssertions.assertEventDispatchThread();
    if (isOutdated() || !shouldShowLookup()) return;

    while (true) {
      Runnable action = myAdvertiserChanges.poll();
      if (action == null) break;
      action.run();
    }

    if (!myLookupUpdated) {
      if (lookup.getAdvertisements().isEmpty() && !isAutopopupCompletion() && !DumbService.isDumb(getProject())) {
        DefaultCompletionContributor.addDefaultAdvertisements(lookup, myHasPsiElements);
      }
      lookup.getAdvertiser().showRandomText();
    }

    boolean justShown = false;
    if (!lookup.isShown()) {
      if (hideAutopopupIfMeaningless()) {
        return;
      }

      if (!lookup.showLookup()) {
        return;
      }
      justShown = true;
    }
    myLookupUpdated = true;
    lookup.refreshUi(true, justShown);
    hideAutopopupIfMeaningless();
    if (justShown) {
      lookup.ensureSelectionVisible(true);
    }
  }

  private boolean shouldShowLookup() {
    if (myLookupAppearancePolicy == LookupAppearancePolicy.POSTPONED) {
      return false;
    }
    if (isAutopopupCompletion()) {
      if (count == 0) {
        return false;
      }
      if (lookup.isCalculating() && Registry.is("ide.completion.delay.autopopup.until.completed")) {
        return false;
      }
    }
    return true;
  }

  void addItem(CompletionResult item) {
    if (!isRunning()) return;
    ProgressManager.checkCanceled();

    if (!handler.isTestingMode()) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    }

    LookupElement lookupElement = item.getLookupElement();
    if (!myHasPsiElements && lookupElement.getPsiElement() != null) {
      myHasPsiElements = true;
    }

    boolean forceMiddleMatch = lookupElement.getUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH) != null;
    if (forceMiddleMatch) {
      myArranger.associateSorter(lookupElement, (CompletionSorterImpl)item.getSorter());
      addItemToLookup(item);
      return;
    }

    boolean allowMiddleMatches = count > BaseCompletionLookupArranger.MAX_PREFERRED_COUNT * 2;
    if (allowMiddleMatches) {
      addDelayedMiddleMatches();
    }

    myArranger.associateSorter(lookupElement, (CompletionSorterImpl)item.getSorter());
    if (item.isStartMatch() || allowMiddleMatches) {
      addItemToLookup(item);
    } else {
      synchronized (delayedMiddleMatches) {
        delayedMiddleMatches.add(item);
      }
    }
  }

  private void addItemToLookup(CompletionResult item) {
    Ref<Boolean> stopRef = new Ref<>(Boolean.FALSE);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      stopRef.set(lookup.isLookupDisposed() || !lookup.addItem(item.getLookupElement(), item.getPrefixMatcher()));
    });

    if (stopRef.get()) {
      return;
    }

    myArranger.setLastLookupPrefix(lookup.getAdditionalPrefix());

    //noinspection NonAtomicOperationOnVolatileField
    count++; // invoked from a single thread

    if (count == myUnfreezeAfterNItems) {
      showLookupAsSoonAsPossible();
    }

    if (myLookupAppearancePolicy == LookupAppearancePolicy.ON_FIRST_POSSIBILITY && lookup.getShownTimestampMillis() == 0L) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(freezeSemaphore::up, 0, TimeUnit.MILLISECONDS);
      openLookupLater();
    }
    else {
      if (count == 1) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(freezeSemaphore::up, ourInsertSingleItemTimeSpan, TimeUnit.MILLISECONDS);
      }
      queue.queue(myUpdate);
    }
  }

  /**
   * In certain cases, we add the first batch of items almost at once and want to show lookup directly after they are added.
   * It makes sense to set this number when you get all your items from one contributor, and you have full control over the completion
   * process. So you can, for example, set this number in the beginning of `addCompletions()` in your completion provider and reset it
   * when the completion is over.
   * Example: Completion provider receives first 100 items at once after significant delay (200+ ms) and adds them all together. If you
   * don't set this value, and you continue adding results in your completion provider (for example, you get your next batch of results in
   * 500ms), the popup might be shown with significant delay.
   *
   * @param number The Number of items in lookup which trigger the popup. -1 means this functionality is disabled. Also specifying 0 makes
   *               no sense since we can't add 0 items.
   */
  public void unfreezeImmediatelyAfterFirstNItems(int number) {
    myUnfreezeAfterNItems = number;
  }

  void addDelayedMiddleMatches() {
    ArrayList<CompletionResult> delayed;
    synchronized (delayedMiddleMatches) {
      if (delayedMiddleMatches.isEmpty()) {
        return;
      }
      delayed = new ArrayList<>(delayedMiddleMatches);
      delayedMiddleMatches.clear();
    }
    for (CompletionResult item : delayed) {
      ProgressManager.checkCanceled();
      addItemToLookup(item);
    }
  }

  public void closeAndFinish(boolean hideLookup) {
    if (!lookup.isLookupDisposed()) {
      Lookup lookup = LookupManager.getActiveLookup(myEditor);
      if (lookup != null && lookup != this.lookup && ClientId.isCurrentlyUnderLocalId()) {
        LOG.error("lookup changed: " + lookup + "; " + this);
      }
    }
    lookup.removeLookupListener(myLookupListener);
    finishCompletionProcess(true);
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());

    if (hideLookup) {
      lookup.hideLookup(true);
    }
  }

  private void finishCompletionProcess(boolean disposeOffsetMap) {
    cancel();

    ThreadingAssertions.assertEventDispatchThread();
    Disposer.dispose(queue);
    LookupManager.getInstance(getProject()).removePropertyChangeListener(myLookupManagerListener);

    CompletionServiceImpl.assertPhase(CompletionPhase.BgCalculation.class,
                                      CompletionPhase.ItemsCalculated.class,
                                      CompletionPhase.Synchronous.class,
                                      CompletionPhase.CommittingDocuments.class);

    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCurrentCompletionProgressIndicator();
    LOG.assertTrue(currentCompletion == this, currentCompletion + "!=" + this);

    CompletionPhase oldPhase = CompletionServiceImpl.getCompletionPhase();
    if (oldPhase instanceof CompletionPhase.CommittingDocuments p) {
      LOG.assertTrue(oldPhase.indicator != null, oldPhase);
      p.replaced = true;
    }
    CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    if (disposeOffsetMap) {
      disposeIndicator();
    }
  }

  void disposeIndicator() {
    synchronized (myLock) {
      Disposer.dispose(this);
    }
  }

  @Override
  public void registerChildDisposable(@NotNull Supplier<? extends Disposable> child) {
    synchronized (myLock) {
      // avoid registering stuff on an indicator being disposed concurrently
      checkCanceled();
      Disposer.register(this, child.get());
    }
  }

  @TestOnly
  public static void cleanupForNextTest() {
    CompletionService completionService = ApplicationManager.getApplication().getServiceIfCreated(CompletionService.class);
    if (!(completionService instanceof CompletionServiceImpl)) {
      return;
    }

    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCurrentCompletionProgressIndicator();
    if (currentCompletion != null) {
      currentCompletion.finishCompletionProcess(true);
      CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());
    }
    else {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }
    StatisticsUpdate.cancelLastCompletionStatisticsUpdate();
  }

  boolean blockingWaitForFinish(int timeoutMs) {
    if (handler.isTestingMode() && !TestModeFlags.is(CompletionAutoPopupHandler.ourTestingAutopopup)) {
      if (!finishSemaphore.waitFor(100 * 1000)) {
        throw new AssertionError("Too long completion");
      }
      return true;
    }

    if (freezeSemaphore.waitFor(timeoutMs)) {
      // the completion is really finished, now we may auto-insert or show lookup
      return !isRunning() && !isCanceled();
    }
    return false;
  }

  @Override
  public void stop() {
    super.stop();

    queue.cancelAllUpdates();
    freezeSemaphore.up();
    finishSemaphore.up();

    ModalityUiUtil.invokeLaterIfNeeded(queue.getModalityState(), () -> {
      CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
      if (!(phase instanceof CompletionPhase.BgCalculation) || phase.indicator != this) {
        return;
      }

      LOG.assertTrue(!getProject().isDisposed(), "project disposed");

      if (myEditor.isDisposed()) {
        lookup.hideLookup(false);
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
        return;
      }

      if (myEditor instanceof EditorWindow) {
        LOG.assertTrue(((EditorWindow)myEditor).getInjectedFile().isValid(), "injected file !valid");
        LOG.assertTrue(((DocumentWindow)myEditor.getDocument()).isValid(), "docWindow !valid");
      }
      PsiFile file = lookup.getPsiFile();
      LOG.assertTrue(file == null || file.isValid(), "file !valid");

      lookup.setCalculating(false);

      if (count == 0) {
        lookup.hideLookup(false);
        if (isAutopopupCompletion()) {
          CompletionServiceImpl.setCompletionPhase(new CompletionPhase.EmptyAutoPopup(myEditor, myRestartingPrefixConditions));
        }
        else {
          CompletionProgressIndicator current = CompletionServiceImpl.getCurrentCompletionProgressIndicator();
          LOG.assertTrue(current == null, current + "!=" + this);

          handleEmptyLookup(!((CompletionPhase.BgCalculation)phase).modifiersChanged);
        }
      }
      else {
        updateLookup();
        if (!CompletionServiceImpl.isPhase(CompletionPhase.NoCompletion.getClass(), CompletionPhase.EmptyAutoPopup.class)) {
          CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(this));
        }
      }
    });
  }

  private boolean hideAutopopupIfMeaningless() {
    if (lookup.isLookupDisposed() || !isAutopopupCompletion() || lookup.isSelectionTouched() || lookup.isCalculating()) {
      return false;
    }

    lookup.refreshUi(true, false);
    for (LookupElement item : lookup.getItems()) {
      if (!isAlreadyInTheEditor(item)) {
        return false;
      }

      if (item.isValid() && item.isWorthShowingInAutoPopup()) {
        return false;
      }
    }

    lookup.hideLookup(false);
    LOG.assertTrue(CompletionServiceImpl.getCompletionService().getCurrentCompletion() == null);
    CompletionServiceImpl.setCompletionPhase(new CompletionPhase.EmptyAutoPopup(myEditor, myRestartingPrefixConditions));
    return true;
  }

  private boolean isAlreadyInTheEditor(LookupElement item) {
    Editor editor = lookup.getEditor();
    int start = editor.getCaretModel().getOffset() - lookup.itemPattern(item).length();
    Document document = editor.getDocument();
    return start >= 0 && StringUtil.startsWith(document.getImmutableCharSequence().subSequence(start, document.getTextLength()),
                                               item.getLookupString());
  }

  void restorePrefix(@NotNull Runnable customRestore) {
    WriteCommandAction.runWriteCommandAction(getProject(), null, null, () -> {
      setMergeCommand();
      customRestore.run();
    });
  }

  int nextInvocationCount(int invocation, boolean reused) {
    return reused ? Math.max(myInvocationCount + 1, 2) : invocation;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  @Override
  public @NotNull Caret getCaret() {
    return myCaret;
  }

  boolean isRepeatedInvocation(CompletionType completionType, Editor editor) {
    if (completionType != myCompletionType || editor != myEditor) {
      return false;
    }

    if (isAutopopupCompletion() && !lookup.mayBeNoticed()) {
      return false;
    }

    return true;
  }

  @Override
  public boolean isAutopopupCompletion() {
    return myInvocationCount == 0;
  }

  int getInvocationCount() {
    return myInvocationCount;
  }

  @Override
  public @NotNull Project getProject() {
    return Objects.requireNonNull(myEditor.getProject());
  }

  @Override
  public void addWatchedPrefix(int startOffset, ElementPattern<String> restartCondition) {
    myRestartingPrefixConditions.add(Pair.create(startOffset, restartCondition));
  }

  @Override
  public void prefixUpdated() {
    final int caretOffset = myEditor.getCaretModel().getOffset();
    if (caretOffset < myStartCaret) {
      scheduleRestart();
      myRestartingPrefixConditions.clear();
      return;
    }

    if (shouldRestartCompletion(myEditor, myRestartingPrefixConditions, "")) {
      scheduleRestart();
      myRestartingPrefixConditions.clear();
      return;
    }

    hideAutopopupIfMeaningless();
  }

  @ApiStatus.Internal
  public static boolean shouldRestartCompletion(@NotNull Editor editor,
                                                @NotNull Set<? extends Pair<Integer, ElementPattern<String>>> restartingPrefixConditions,
                                                @NotNull String toAppend) {
    int caretOffset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getCharsSequence();
    for (Pair<Integer, ElementPattern<String>> pair : restartingPrefixConditions) {
      int start = pair.first;
      if (caretOffset >= start && start >= 0 && caretOffset <= text.length()) {
        String newPrefix = text.subSequence(start, caretOffset).toString() + toAppend;
        if (pair.second.accepts(newPrefix)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void scheduleRestart() {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.trace("Scheduling restart");
    if (handler.isTestingMode() && !TestModeFlags.is(CompletionAutoPopupHandler.ourTestingAutopopup)) {
      closeAndFinish(false);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      new CodeCompletionHandlerBase(myCompletionType, false, false, true).invokeCompletion(getProject(), myEditor, myInvocationCount);
      return;
    }

    cancel();

    final CompletionProgressIndicator current = CompletionServiceImpl.getCurrentCompletionProgressIndicator();
    if (this != current) {
      LOG.error(current + "!=" + this + ";" +
                "current phase=" + CompletionServiceImpl.getCompletionPhase() + ";" +
                "clientId=" + ClientId.getCurrent());
    }

    hideAutopopupIfMeaningless();

    CompletionPhase oldPhase = CompletionServiceImpl.getCompletionPhase();
    if (oldPhase instanceof CompletionPhase.CommittingDocuments) {
      ((CompletionPhase.CommittingDocuments)oldPhase).replaced = true;
    }

    CompletionPhase.CommittingDocuments.scheduleAsyncCompletion(myEditor, myCompletionType, null, getProject(), this);
  }

  @Override
  public String toString() {
    return "CompletionProgressIndicator[count=" +
           count +
           ",phase=" +
           CompletionServiceImpl.getCompletionPhase() +
           "]@" +
           System.identityHashCode(this);
  }

  void handleEmptyLookup(boolean awaitSecondInvocation) {
    if (isAutopopupCompletion() && ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    LOG.assertTrue(!isAutopopupCompletion());

    CompletionParameters parameters = getParameters();
    if (handler.invokedExplicitly && parameters != null) {
      LightweightHint hint = showErrorHint(getProject(), getEditor(), getNoSuggestionsMessage(parameters));
      if (awaitSecondInvocation) {
        CompletionServiceImpl.setCompletionPhase(new CompletionPhase.NoSuggestionsHint(hint, this));
        return;
      }
    }
    CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
  }

  private @HintText String getNoSuggestionsMessage(CompletionParameters parameters) {
    return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      return CompletionContributor.forParameters(parameters)
        .stream()
        .map(c -> c.handleEmptyLookup(parameters, getEditor()))
        .filter(StringUtil::isNotEmpty)
        .findFirst()
        .orElse(LangBundle.message("completion.no.suggestions"));
    });
  }

  private LightweightHint showErrorHint(Project project, Editor editor, @HintText String text) {
    LightweightHint[] result = {null};
    EditorHintListener listener = new EditorHintListener() {
      @Override
      public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
        result[0] = hint;
      }
    };
    SimpleMessageBusConnection connection = project.getMessageBus().simpleConnect();
    try {
      connection.subscribe(EditorHintListener.TOPIC, listener);
      assert text != null;
      myEmptyCompletionNotifier.showIncompleteHint(editor, text, DumbService.isDumb(project));
    }
    finally {
      connection.disconnect();
    }
    return result[0];
  }

  public static boolean shouldPreselectFirstSuggestion(CompletionParameters parameters) {
    if (Registry.is("ide.completion.lookup.element.preselect.depends.on.context")) {
      for (CompletionPreselectionBehaviourProvider provider : CompletionPreselectionBehaviourProvider.EP_NAME.getExtensionList()) {
        if (!provider.shouldPreselectFirstSuggestion(parameters)) {
          return false;
        }
      }
    }

    return true;
  }

  void runContributors(CompletionInitializationContext initContext) {
    CompletionParameters parameters = Objects.requireNonNull(myParameters);
    threading.startThread(ProgressWrapper.wrap(this), () -> {
      CompletionThreadingKt.tryReadOrCancel(this, () -> scheduleAdvertising(parameters));
    });

    WeighingDelegate weigher = threading.delegateWeighing(this);
    try {
      calculateItems(initContext, weigher, parameters);
    }
    catch (ProcessCanceledException ignore) {
      cancel(); // some contributor may just throw PCE; if indicator is not canceled everything will hang
    }
    catch (Throwable t) {
      cancel();
      LOG.error(t);
    }
  }

  private void calculateItems(CompletionInitializationContext initContext, WeighingDelegate weigher, CompletionParameters parameters) {
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      duringCompletion(initContext, parameters);
      ProgressManager.checkCanceled();

      CompletionService.getCompletionService().performCompletion(parameters, weigher);
    });
    ProgressManager.checkCanceled();

    weigher.waitFor();
    ProgressManager.checkCanceled();
  }

  @NotNull
  CompletionThreadingBase getCompletionThreading() {
    return threading;
  }

  @Override
  public void addAdvertisement(@NotNull String text, @Nullable Icon icon) {
    myAdvertiserChanges.offer(() -> lookup.addAdvertisement(text, icon));

    queue.queue(myUpdate);
  }

  @TestOnly
  public static void setGroupingTimeSpan(int timeSpan) {
    ourInsertSingleItemTimeSpan = timeSpan;
  }

  @Deprecated(forRemoval = true)
  public static void setAutopopupTriggerTime(int timeSpan) {
    ourShowPopupGroupingTime = timeSpan;
    ourShowPopupAfterFirstItemGroupingTime = timeSpan;
  }

  private static final class ModifierTracker extends KeyAdapter {
    private final JComponent myContentComponent;

    ModifierTracker(JComponent contentComponent) {
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

  private static final class ProjectEmptyCompletionNotifier implements EmptyCompletionNotifier {
    @Override
    public void showIncompleteHint(@NotNull Editor editor, @NotNull @HintText String text, boolean isDumbMode) {
      String message = isDumbMode ?
                       text + CodeInsightBundle.message("completion.incomplete.during.indexing.suffix") : text;
      HintManager.getInstance().showInformationHint(editor, StringUtil.escapeXmlEntities(message), HintManager.ABOVE);
    }
  }
}

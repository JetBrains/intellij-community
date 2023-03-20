// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionAssertions.WatchingInsertionContext;
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.OverridingAction;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

import static com.intellij.diagnostic.telemetry.TraceKt.runWithSpan;

@SuppressWarnings("deprecation")
public class CodeCompletionHandlerBase {
  private static final Logger LOG = Logger.getInstance(CodeCompletionHandlerBase.class);
  private static final Key<Boolean> CARET_PROCESSED = Key.create("CodeCompletionHandlerBase.caretProcessed");

  /**
   * If this key is set for a lookup element, the framework will only call handleInsert() on the lookup element when it is selected,
   * and will not perform any additional processing such as multi-caret handling or insertion of completion character.
   */
  public static final Key<Boolean> DIRECT_INSERTION = Key.create("CodeCompletionHandlerBase.directInsertion");

  @NotNull final CompletionType completionType;
  final boolean invokedExplicitly;
  final boolean synchronous;
  final boolean autopopup;
  private static int ourAutoInsertItemTimeout = Registry.intValue("ide.completion.auto.insert.item.timeout", 2000);

  private final Tracer completionTracer = TraceManager.INSTANCE.getTracer("codeCompletion");

  public static CodeCompletionHandlerBase createHandler(@NotNull CompletionType completionType) {
    return createHandler(completionType, true, false, true);
  }

  public static CodeCompletionHandlerBase createHandler(@NotNull CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {
    AnAction codeCompletionAction = ActionManager.getInstance().getAction("CodeCompletion");
    if (codeCompletionAction instanceof OverridingAction) {
      codeCompletionAction = ((ActionManagerImpl) ActionManager.getInstance()).getBaseAction((OverridingAction) codeCompletionAction);
    }
    assert (codeCompletionAction instanceof BaseCodeCompletionAction);
    BaseCodeCompletionAction baseCodeCompletionAction = (BaseCodeCompletionAction) codeCompletionAction;
    return baseCodeCompletionAction.createHandler(completionType, invokedExplicitly, autopopup, synchronous);
  }

  public CodeCompletionHandlerBase(@NotNull CompletionType completionType) {
    this(completionType, true, false, true);
  }

  public CodeCompletionHandlerBase(@NotNull CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {
    this.completionType = completionType;
    this.invokedExplicitly = invokedExplicitly;
    this.autopopup = autopopup;
    this.synchronous = synchronous;

    if (autopopup) {
      assert !invokedExplicitly;
    }
  }

  public void handleCompletionElementSelected(@NotNull LookupElement item,
                                              char completionChar, OffsetMap offsetMap, OffsetsInFile hostOffsets, Editor editor, Integer initialOffset) {
    WatchingInsertionContext context = null;
    try {
      StatisticsUpdate update = StatisticsUpdate.collectStatisticChanges(item);
      //todo pass all relevant items
      context = insertItemHonorBlockSelection(new ArrayList<>(),item, completionChar, offsetMap, hostOffsets, editor, initialOffset, update);
      update.trackStatistics(context);
    }
    finally {
      if (context != null && context.getLaterRunnable() != null) {
        context.getLaterRunnable().run();
      }
    }
  }

  public final void invokeCompletion(final Project project, final Editor editor) {
    invokeCompletion(project, editor, 1);
  }

  public final void invokeCompletion(@NotNull final Project project, @NotNull final Editor editor, int time) {
    invokeCompletion(project, editor, time, false);
  }

  public final void invokeCompletion(@NotNull Project project, @NotNull Editor editor, int time, boolean hasModifiers) {
    clearCaretMarkers(editor);
    invokeCompletionWithTracing(project, editor, time, hasModifiers, editor.getCaretModel().getPrimaryCaret());
  }

  private void invokeCompletion(@NotNull Project project, @NotNull Editor editor, int time, boolean hasModifiers, @NotNull Caret caret) {
      markCaretAsProcessed(caret);

      if (invokedExplicitly) {
        StatisticsUpdate.applyLastCompletionStatisticsUpdate();
      }

      checkNoWriteAccess();

      CompletionAssertions.checkEditorValid(editor);

      int offset = editor.getCaretModel().getOffset();
      if (editor.isViewer() || editor.getDocument().getRangeGuard(offset, offset) != null) {
        editor.getDocument().fireReadOnlyModificationAttempt();
        EditorModificationUtil.checkModificationAllowed(editor);
        return;
      }

      if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
        return;
      }
      CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
      boolean repeated = phase.indicator != null && phase.indicator.isRepeatedInvocation(completionType, editor);

      final int newTime = phase.newCompletionStarted(time, repeated);
      if (invokedExplicitly) {
        time = newTime;
      }
      final int invocationCount = time;
      if (CompletionServiceImpl.isPhase(CompletionPhase.InsertedSingleItem.class)) {
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      }
      CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass(), CompletionPhase.CommittingDocuments.class);

      if (invocationCount > 1 && completionType == CompletionType.BASIC) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION);
      }

      long startingTime = System.currentTimeMillis();
      Runnable initCmd = () -> {
        WriteAction.run(() -> EditorUtil.fillVirtualSpaceUntilCaret(editor));
        CompletionInitializationContextImpl context = withTimeout(calcSyncTimeOut(startingTime), () ->
          CompletionInitializationUtil.createCompletionInitializationContext(project, editor, caret, invocationCount, completionType));

        boolean hasValidContext = context != null;
        if (!hasValidContext) {
          final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(caret, project);
          context = new CompletionInitializationContextImpl(editor, caret, psiFile, completionType, invocationCount);
        }

        doComplete(context, hasModifiers, hasValidContext, startingTime);
      };
    try {
      if (autopopup) {
        CommandProcessor.getInstance().runUndoTransparentAction(initCmd);
      }
      else {
        CommandProcessor.getInstance().executeCommand(project, initCmd, null, null, editor.getDocument());
      }
    }
    catch (IndexNotReadyException e) {
      if (invokedExplicitly) {
        DumbService.getInstance(project).showDumbModeNotification(CodeInsightBundle.message("completion.not.available.during.indexing"));
      }
      throw e;
    }
  }

  private void invokeCompletionWithTracing(@NotNull Project project,
                                           @NotNull Editor editor,
                                           int time,
                                           boolean hasModifiers,
                                           @NotNull Caret caret) {
    runWithSpan(completionTracer, "invokeCompletion", (span) -> {
      span.setAttribute("project", project.getName());
      span.setAttribute("caretOffset", caret.hasSelection() ? caret.getSelectionStart() : caret.getOffset());

      invokeCompletion(project, editor, time, hasModifiers, caret);
    });
  }

  private static void checkNoWriteAccess() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
        throw new AssertionError("Completion should not be invoked inside write action");
      }
    }
  }

  @NotNull
  private LookupImpl obtainLookup(Editor editor, Project project) {
    CompletionAssertions.checkEditorValid(editor);
    LookupImpl existing = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (existing != null && existing.isCompletion()) {
      existing.markReused();
      if (!autopopup) {
        existing.setLookupFocusDegree(LookupFocusDegree.FOCUSED);
      }
      return existing;
    }

    LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).createLookup(editor, LookupElement.EMPTY_ARRAY, "",
                                                                                    new LookupArranger.DefaultArranger());
    if (editor.isOneLineMode()) {
      lookup.setCancelOnClickOutside(true);
      lookup.setCancelOnOtherWindowOpen(true);
    }
    lookup.setLookupFocusDegree(autopopup ? LookupFocusDegree.UNFOCUSED : LookupFocusDegree.FOCUSED);
    return lookup;
  }

  private void doComplete(CompletionInitializationContextImpl initContext, boolean hasModifiers, boolean isValidContext, long startingTime) {
    final Editor editor = initContext.getEditor();
    CompletionAssertions.checkEditorValid(editor);

    LookupImpl lookup = obtainLookup(editor, initContext.getProject());

    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    if (phase instanceof CompletionPhase.CommittingDocuments) {
      if (phase.indicator != null) {
        phase.indicator.closeAndFinish(false);
      }
      ((CompletionPhase.CommittingDocuments)phase).replaced = true;
    } else {
      CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());
    }

    CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, initContext.getCaret(),
                                                                            initContext.getInvocationCount(), this,
                                                                            initContext.getOffsetMap(),
                                                                            initContext.getHostOffsets(),
                                                                            hasModifiers, lookup);


    if (synchronous && isValidContext) {
      OffsetsInFile hostCopyOffsets = withTimeout(calcSyncTimeOut(startingTime), () -> {
        PsiDocumentManager.getInstance(initContext.getProject()).commitAllDocuments();
        return CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator).get();
      });
      if (hostCopyOffsets != null) {
        trySynchronousCompletion(initContext, hasModifiers, startingTime, indicator, hostCopyOffsets);
        return;
      }
    }
    scheduleContributorsAfterAsyncCommit(initContext, indicator, hasModifiers);
  }

  private void scheduleContributorsAfterAsyncCommit(CompletionInitializationContextImpl initContext,
                                                    CompletionProgressIndicator indicator,
                                                    boolean hasModifiers) {
    CompletionPhase phase;
    if (synchronous) {
      phase = new CompletionPhase.BgCalculation(indicator);
      indicator.showLookup();
    } else {
      phase = new CompletionPhase.CommittingDocuments(indicator, InjectedLanguageEditorUtil.getTopLevelEditor(indicator.getEditor()));
    }
    CompletionServiceImpl.setCompletionPhase(phase);

    ReadAction
      .nonBlocking(() -> CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator))
      .expireWith(phase)
      .withDocumentsCommitted(indicator.getProject())
      .finishOnUiThread(ModalityState.defaultModalityState(), applyPsiChanges -> {
        OffsetsInFile hostCopyOffsets = applyPsiChanges.get();

        if (phase instanceof CompletionPhase.CommittingDocuments) {
          ((CompletionPhase.CommittingDocuments)phase).replaced = true;
        }
        CompletionServiceImpl.setCompletionPhase(new CompletionPhase.BgCalculation(indicator));
        startContributorThread(initContext, indicator, hostCopyOffsets, hasModifiers);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void trySynchronousCompletion(CompletionInitializationContextImpl initContext,
                                        boolean hasModifiers,
                                        long startingTime,
                                        CompletionProgressIndicator indicator, OffsetsInFile hostCopyOffsets) {
    CompletionServiceImpl.setCompletionPhase(new CompletionPhase.Synchronous(indicator));

    Future<?> future = startContributorThread(initContext, indicator, hostCopyOffsets, hasModifiers);
    if (future == null) {
      return;
    }

    int timeout = calcSyncTimeOut(startingTime);
    if (indicator.blockingWaitForFinish(timeout)) {
      checkForExceptions(future);
      try {
        indicator.getLookup().refreshUi(true, false);
        completionFinished(indicator, hasModifiers);
      }
      catch (Throwable e) {
        LOG.error(e);
        indicator.closeAndFinish(true);
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      }
      return;
    }

    CompletionServiceImpl.setCompletionPhase(new CompletionPhase.BgCalculation(indicator));
    indicator.showLookup();
  }

  @Nullable
  private Future<?> startContributorThread(CompletionInitializationContextImpl initContext,
                                           CompletionProgressIndicator indicator,
                                           OffsetsInFile hostCopyOffsets,
                                           boolean hasModifiers) {
    if (!hostCopyOffsets.getFile().isValid()) {
      completionFinished(indicator, hasModifiers);
      return null;
    }

    return indicator.getCompletionThreading()
      .startThread(indicator, Context.current().wrap(() -> AsyncCompletion.tryReadOrCancel(indicator, Context.current().wrap(() -> {
        OffsetsInFile finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.getFile(), hostCopyOffsets);
        indicator.registerChildDisposable(finalOffsets::getOffsets);

        CompletionParameters parameters =
          CompletionInitializationUtil.createCompletionParameters(initContext, indicator, finalOffsets);
        parameters.setIsTestingMode(isTestingMode());
        indicator.setParameters(parameters);

        indicator.runContributors(initContext);
      }))));
  }

  private static void checkForExceptions(Future<?> future) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      try {
        future.get();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private static void checkNotSync(CompletionProgressIndicator indicator, List<LookupElement> allItems) {
    if (CompletionServiceImpl.isPhase(CompletionPhase.Synchronous.class)) {
      LOG.error("sync phase survived: " + allItems + "; indicator=" + CompletionServiceImpl.getCompletionPhase().indicator + "; myIndicator=" + indicator);
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }
  }

  private AutoCompletionDecision shouldAutoComplete(@NotNull CompletionProgressIndicator indicator,
                                                    @NotNull List<? extends LookupElement> items,
                                                    @NotNull CompletionParameters parameters) {
    if (!invokedExplicitly) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    final LookupElement item = items.get(0);
    if (items.size() == 1) {
      final AutoCompletionPolicy policy = getAutocompletionPolicy(item);
      if (policy == AutoCompletionPolicy.NEVER_AUTOCOMPLETE) return AutoCompletionDecision.SHOW_LOOKUP;
      if (policy == AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE) return AutoCompletionDecision.insertItem(item);
      if (!indicator.getLookup().itemMatcher(item).isStartMatch(item)) return AutoCompletionDecision.SHOW_LOOKUP;
    }
    if (!isAutocompleteOnInvocation(parameters.getCompletionType())) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    if (isInsideIdentifier(indicator.getOffsetMap())) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    if (items.size() == 1 && getAutocompletionPolicy(item) == AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE) {
      return AutoCompletionDecision.insertItem(item);
    }

    AutoCompletionContext context =
      new AutoCompletionContext(parameters, items.toArray(LookupElement.EMPTY_ARRAY), indicator.getOffsetMap(), indicator.getLookup());
    AutoCompletionDecision resultingDecision = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      for (final CompletionContributor contributor : CompletionContributor.forParameters(parameters)) {
        AutoCompletionDecision decision = contributor.handleAutoCompletionPossibility(context);
        if (decision != null) {
          return decision;
        }
      }
      return null;
    });

    if (resultingDecision != null) {
      return resultingDecision;
    }

    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  @Nullable
  private static AutoCompletionPolicy getAutocompletionPolicy(LookupElement element) {
    return element.getAutoCompletionPolicy();
  }

  private static boolean isInsideIdentifier(final OffsetMap offsetMap) {
    return offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != offsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  protected void completionFinished(final CompletionProgressIndicator indicator, boolean hasModifiers) {
    List<LookupElement> items = indicator.getLookup().getItems();
    if (items.isEmpty()) {
      LookupManager.hideActiveLookup(indicator.getProject());

      Caret nextCaret = getNextCaretToProcess(indicator.getEditor());
      if (nextCaret != null) {
        invokeCompletionWithTracing(indicator.getProject(), indicator.getEditor(), indicator.getInvocationCount(), hasModifiers, nextCaret);
      }
      else {
        indicator.handleEmptyLookup(true);
        checkNotSync(indicator, items);
      }
      return;
    }

    LOG.assertTrue(!indicator.isRunning(), "running");
    LOG.assertTrue(!indicator.isCanceled(), "canceled");

    try {
      CompletionParameters parameters = indicator.getParameters();
      AutoCompletionDecision decision = parameters == null ? AutoCompletionDecision.CLOSE_LOOKUP : shouldAutoComplete(indicator, items, parameters);
      if (decision == AutoCompletionDecision.SHOW_LOOKUP) {
        indicator.getLookup().setCalculating(false);
        indicator.showLookup();
        CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(indicator));
      }
      else if (decision instanceof AutoCompletionDecision.InsertItem) {
        final Runnable restorePrefix = rememberDocumentState(indicator.getEditor());

        final LookupElement item = ((AutoCompletionDecision.InsertItem)decision).getElement();
        CommandProcessor.getInstance().executeCommand(indicator.getProject(), () -> {
          indicator.setMergeCommand();
          indicator.getLookup().finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR, item);
        }, CodeInsightBundle.message("completion.automatic.command.name"), null);

        // the insert handler may have started a live template with completion
        if (CompletionService.getCompletionService().getCurrentCompletion() == null &&
            // ...or scheduled another autopopup
            !CompletionServiceImpl.isPhase(CompletionPhase.CommittingDocuments.class)) {
          CompletionServiceImpl.setCompletionPhase(hasModifiers? new CompletionPhase.InsertedSingleItem(indicator, restorePrefix) : CompletionPhase.NoCompletion);
        }
      } else if (decision == AutoCompletionDecision.CLOSE_LOOKUP) {
        LookupManager.hideActiveLookup(indicator.getProject());
      }
    }
    catch (Throwable e) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      LOG.error(e);
    }
    finally {
      checkNotSync(indicator, items);
    }
  }

  protected void lookupItemSelected(final CompletionProgressIndicator indicator, @NotNull final LookupElement item, final char completionChar,
                                         final List<LookupElement> items) {
    WatchingInsertionContext context = null;
    try {
      StatisticsUpdate update = StatisticsUpdate.collectStatisticChanges(item);
      if (item.getUserData(DIRECT_INSERTION) != null) {
        context = callHandleInsert(indicator, item, completionChar);
      }
      else {
        context = insertItemHonorBlockSelection(indicator, item, completionChar, update);
      }
      update.trackStatistics(context);
    }
    finally {
      afterItemInsertion(indicator, context == null ? null : context.getLaterRunnable());
    }
  }

  public static WatchingInsertionContext insertItemHonorBlockSelection(List<LookupElement> itemsAround,
                                                                        LookupElement item,
                                                                        char completionChar,
                                                                        OffsetMap offsetMap,
                                                                        OffsetsInFile hostOffset,
                                                                        Editor editor,
                                                                        Integer caretOffset,
                                                                        StatisticsUpdate update) {

    final int idEndOffset = CompletionUtil.calcIdEndOffset(offsetMap, editor, caretOffset);
    final int idEndOffsetDelta = idEndOffset - caretOffset;

    WatchingInsertionContext context = doInsertItem(hostOffset,
      item,
      completionChar,
      update,
      editor,
      Objects.requireNonNull(editor.getProject()),
      caretOffset,
      offsetMap,
      itemsAround,
      idEndOffset,
      idEndOffsetDelta);

    if (context.shouldAddCompletionChar()) {
      WriteAction.run(() -> addCompletionChar(context, item));
    }

    return context;
  }

  private static WatchingInsertionContext insertItemHonorBlockSelection(CompletionProcessEx indicator,
                                                                        LookupElement item,
                                                                        char completionChar,
                                                                        StatisticsUpdate update) {
    final Editor editor = indicator.getEditor();
    int caretOffset = indicator.getCaret().getOffset();
    OffsetMap offsetMap = indicator.getOffsetMap();

    Lookup lookup = indicator.getLookup();
    List<LookupElement> items = lookup != null ? lookup.getItems() : Collections.emptyList();

    final int idEndOffset = CompletionUtil.calcIdEndOffset(offsetMap, editor, caretOffset);
    final int idEndOffsetDelta = idEndOffset - caretOffset;

    WatchingInsertionContext context = doInsertItem(indicator.getHostOffsets(),
      item,
      completionChar,
      update,
      editor,
      indicator.getProject(),
      caretOffset,
      offsetMap,
      items,
      idEndOffset,
      idEndOffsetDelta);

    if (lookup != null) {
      update.addSparedChars(lookup, item, context);
    }

    if (context.shouldAddCompletionChar()) {
      WriteAction.run(() -> addCompletionChar(context, item));
    }
    checkPsiTextConsistency(indicator);

    return context;
  }

  private static WatchingInsertionContext doInsertItem(@NotNull OffsetsInFile topLevelOffsets,
                                                       LookupElement item,
                                                       char completionChar,
                                                       StatisticsUpdate update,
                                                       @NotNull Editor editor,
                                                       @NotNull Project project,
                                                       int caretOffset,
                                                       OffsetMap offsetMap,
                                                       List<LookupElement> items,
                                                       int idEndOffset,
                                                       int idEndOffsetDelta) {
    WatchingInsertionContext context;
    if (editor.getCaretModel().supportsMultipleCarets()) {
      Ref<WatchingInsertionContext> lastContext = Ref.create();
      Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
      boolean wasInjected = hostEditor != editor;
      PsiDocumentManager.getInstance(project).commitDocument(hostEditor.getDocument());
      hostEditor.getCaretModel().runForEachCaret(caret -> {
        OffsetsInFile targetOffsets;
        if (!wasInjected) {
          targetOffsets = topLevelOffsets;
        }
        else {
          targetOffsets = topLevelOffsets.toInjectedIfAny(caret.getOffset());
        }
        doInsertItemForSingleCaret(item, completionChar, update, items, idEndOffsetDelta, lastContext, hostEditor, targetOffsets);
      });
      context = lastContext.get();
    }
    else {
      PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      context = insertItem(items, item, completionChar, update, editor, psiFile, caretOffset,
        idEndOffset, offsetMap);
    }
    return context;
  }

  private static void doInsertItemForSingleCaret(LookupElement item,
                                                 char completionChar,
                                                 StatisticsUpdate update,
                                                 List<LookupElement> items,
                                                 int idEndOffsetDelta,
                                                 Ref<WatchingInsertionContext> lastContext,
                                                 Editor hostEditor,
                                                 OffsetsInFile targetOffsets) {
    PsiFile targetFile = targetOffsets.getFile();
    Editor targetEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, targetFile);
    int targetCaretOffset = targetEditor.getCaretModel().getOffset();
    int idEnd = targetCaretOffset + idEndOffsetDelta;
    if (idEnd > targetEditor.getDocument().getTextLength()) {
      idEnd = targetCaretOffset; // no replacement by Tab when offsets gone wrong for some reason
    }

    WatchingInsertionContext currentContext = insertItem(items, item, completionChar, update,
                                                         targetEditor, targetFile,
                                                         targetCaretOffset, idEnd,
                                                         targetOffsets.getOffsets());
    lastContext.set(currentContext);
  }

  private static void checkPsiTextConsistency(CompletionProcessEx indicator) {
    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(InjectedLanguageEditorUtil.getTopLevelEditor(indicator.getEditor()), indicator.getProject());
    if (psiFile != null) {
      if (Registry.is("ide.check.stub.text.consistency") ||
          ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest()) {
        StubTextInconsistencyException.checkStubTextConsistency(psiFile);
        if (PsiDocumentManager.getInstance(psiFile.getProject()).hasUncommitedDocuments()) {
          PsiDocumentManager.getInstance(psiFile.getProject()).commitAllDocuments();
          StubTextInconsistencyException.checkStubTextConsistency(psiFile);
        }
      }
    }
  }

  public void afterItemInsertion(final CompletionProgressIndicator indicator, final Runnable laterRunnable) {
    if (laterRunnable != null) {
      ActionTracker tracker = new ActionTracker(indicator.getEditor(), indicator);
      Runnable wrapper = () -> {
        if (!Disposer.isDisposed(indicator) && !indicator.getProject().isDisposed() && !tracker.hasAnythingHappened()) {
          laterRunnable.run();
        }
        indicator.disposeIndicator();
      };
      if (isTestingMode()) {
        wrapper.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(wrapper);
      }
    }
    else {
      indicator.disposeIndicator();
    }
  }

  private static WatchingInsertionContext insertItem(final List<LookupElement> lookupItems,
                                                     final LookupElement item,
                                                     final char completionChar,
                                                     final StatisticsUpdate update,
                                                     final Editor editor,
                                                     final PsiFile psiFile,
                                                     final int caretOffset,
                                                     final int idEndOffset,
                                                     final OffsetMap offsetMap) {
    editor.getCaretModel().moveToOffset(caretOffset);

    WatchingInsertionContext context =
      CompletionUtil.createInsertionContext(lookupItems, item, completionChar, editor, psiFile, caretOffset, idEndOffset, offsetMap);
    int initialStartOffset = Math.max(0, caretOffset - item.getLookupString().length());
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        if (caretOffset < idEndOffset && completionChar == Lookup.REPLACE_SELECT_CHAR) {
          Document document = editor.getDocument();
          if (document.getRangeGuard(caretOffset, idEndOffset) == null) {
            document.deleteString(caretOffset, idEndOffset);
          }
        }

        assert context.getStartOffset() >= 0 : "stale startOffset: was " + initialStartOffset + "; selEnd=" + caretOffset + "; idEnd=" + idEndOffset + "; file=" + psiFile;
        assert context.getTailOffset() >= 0 : "stale tail: was " + initialStartOffset + "; selEnd=" + caretOffset + "; idEnd=" + idEndOffset + "; file=" + psiFile;

        Project project = psiFile.getProject();
        if (item.requiresCommittedDocuments()) {
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        }
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
          item.handleInsert(context);
        });
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      }
      finally {
        context.stopWatching();
      }

      EditorModificationUtil.scrollToCaret(editor);
    });

    return context;
  }

  private static WatchingInsertionContext callHandleInsert(CompletionProgressIndicator indicator, LookupElement item, char completionChar) {
    final Editor editor = indicator.getEditor();

    final int caretOffset = indicator.getCaret().getOffset();
    final int idEndOffset = CompletionUtil.calcIdEndOffset(indicator.getOffsetMap(), editor, indicator.getCaret().getOffset());
    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, indicator.getProject());

    WatchingInsertionContext context = CompletionUtil.createInsertionContext(indicator.getLookup().getItems(), item, completionChar, editor, psiFile,
                                                                             caretOffset, idEndOffset, indicator.getOffsetMap());
    try {
      item.handleInsert(context);
    }
    finally {
      context.stopWatching();
    }
    return context;
  }

  public static void addCompletionChar(InsertionContext context, LookupElement item) {
    if (!context.getOffsetMap().containsOffset(InsertionContext.TAIL_OFFSET)) {
      @NonNls String message = "tailOffset<0 after inserting " + item + " of " + item.getClass();
      if (context instanceof WatchingInsertionContext) {
        message += "; invalidated at: " + ((WatchingInsertionContext)context).invalidateTrace + "\n--------";
      }
      LOG.info(message);
    }
    else if (!CompletionAssertions.isEditorValid(context.getEditor())) {
      LOG.info("Injected editor invalidated " + context.getEditor());
    }
    else {
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
    }
    if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      Language language = PsiUtilBase.getLanguageInEditor(context.getEditor(), context.getFile().getProject());
      if (language != null) {
        for (SmartEnterProcessor processor : SmartEnterProcessors.INSTANCE.allForLanguage(language)) {
          if (processor.processAfterCompletion(context.getEditor(), context.getFile())) break;
        }
      }
    }
    else {
      DataContext dataContext = DataManager.getInstance().getDataContext(context.getEditor().getContentComponent());
      EditorActionManager.getInstance();
      TypedAction.getInstance().getHandler().execute(context.getEditor(), context.getCompletionChar(), dataContext);
    }
  }

  private static boolean isAutocompleteOnInvocation(final CompletionType type) {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (type == CompletionType.SMART) {
      return settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
    }
    return settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
  }

  private static Runnable rememberDocumentState(final Editor _editor) {
    final Editor editor = InjectedLanguageEditorUtil.getTopLevelEditor(_editor);
    final String documentText = editor.getDocument().getText();
    final int caret = editor.getCaretModel().getOffset();
    final int selStart = editor.getSelectionModel().getSelectionStart();
    final int selEnd = editor.getSelectionModel().getSelectionEnd();

    final int vOffset = editor.getScrollingModel().getVerticalScrollOffset();
    final int hOffset = editor.getScrollingModel().getHorizontalScrollOffset();

    return () -> {
      DocumentEx document = (DocumentEx) editor.getDocument();

      document.replaceString(0, document.getTextLength(), documentText);
      editor.getCaretModel().moveToOffset(caret);
      editor.getSelectionModel().setSelection(selStart, selEnd);

      editor.getScrollingModel().scrollHorizontally(hOffset);
      editor.getScrollingModel().scrollVertically(vOffset);
    };
  }

  private static void clearCaretMarkers(@NotNull Editor editor) {
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      caret.putUserData(CARET_PROCESSED, null);
    }
  }

  private static void markCaretAsProcessed(@NotNull Caret caret) {
    caret.putUserData(CARET_PROCESSED, Boolean.TRUE);
  }

  private static Caret getNextCaretToProcess(@NotNull Editor editor) {
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      if (caret.getUserData(CARET_PROCESSED) == null) {
        return caret;
      }
    }
    return null;
  }

  @Nullable
  private <T> T withTimeout(long maxDurationMillis, @NotNull Computable<T> task) {
    if (isTestingMode()) {
      return task.compute();
    }

    return ProgressIndicatorUtils.withTimeout(maxDurationMillis, task);
  }

  private static int calcSyncTimeOut(long startTime) {
    return (int)Math.max(300, ourAutoInsertItemTimeout - (System.currentTimeMillis() - startTime));
  }

  @SuppressWarnings("unused") // for Rider
  @TestOnly
  public static void setAutoInsertTimeout(int timeout) {
    ourAutoInsertItemTimeout = timeout;
  }

  protected boolean isTestingCompletionQualityMode() {
    return false;
  }

  protected boolean isTestingMode() {
    return ApplicationManager.getApplication().isUnitTestMode() || isTestingCompletionQualityMode();
  }
}

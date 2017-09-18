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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionAssertions.WatchingInsertionContext;
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

@SuppressWarnings("deprecation")
public class CodeCompletionHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private static final Key<Boolean> CARET_PROCESSED = Key.create("CodeCompletionHandlerBase.caretProcessed");

  @NotNull private final CompletionType myCompletionType;
  final boolean invokedExplicitly;
  final boolean synchronous;
  final boolean autopopup;
  private static int ourAutoInsertItemTimeout = 2000;

  public static CodeCompletionHandlerBase createHandler(@NotNull CompletionType completionType) {
    return createHandler(completionType, true, false, true);
  }

  public static CodeCompletionHandlerBase createHandler(@NotNull CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {
    AnAction codeCompletionAction = ActionManager.getInstance().getAction("CodeCompletion");
    assert (codeCompletionAction instanceof BaseCodeCompletionAction);
    BaseCodeCompletionAction baseCodeCompletionAction = (BaseCodeCompletionAction) codeCompletionAction;
    return baseCodeCompletionAction.createHandler(completionType, invokedExplicitly, autopopup, synchronous);
  }

  public CodeCompletionHandlerBase(@NotNull CompletionType completionType) {
    this(completionType, true, false, true);
  }

  public CodeCompletionHandlerBase(@NotNull CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {
    myCompletionType = completionType;
    this.invokedExplicitly = invokedExplicitly;
    this.autopopup = autopopup;
    this.synchronous = synchronous;

    if (invokedExplicitly) {
      assert synchronous;
    }
    if (autopopup) {
      assert !invokedExplicitly;
    }
  }

  public final void invokeCompletion(final Project project, final Editor editor) {
    try {
      invokeCompletion(project, editor, 1);
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification("Code completion is not available here while indices are being built");
    }
  }

  public final void invokeCompletion(@NotNull final Project project, @NotNull final Editor editor, int time) {
    invokeCompletion(project, editor, time, false, false);
  }

  public final void invokeCompletion(@NotNull final Project project, @NotNull final Editor editor, int time, boolean hasModifiers, boolean restarted) {
    clearCaretMarkers(editor);
    invokeCompletion(project, editor, time, hasModifiers, restarted, editor.getCaretModel().getPrimaryCaret());
  }

  public final void invokeCompletion(@NotNull final Project project, @NotNull final Editor editor, int time, boolean hasModifiers, boolean restarted, @NotNull final Caret caret) {
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
    boolean repeated = phase.indicator != null && phase.indicator.isRepeatedInvocation(myCompletionType, editor);
    /*
    if (repeated && isAutocompleteCommonPrefixOnInvocation() && phase.fillInCommonPrefix()) {
      return;
    }
    */

    final int newTime = phase.newCompletionStarted(time, repeated);
    if (invokedExplicitly) {
      time = newTime;
    }
    final int invocationCount = time;
    if (CompletionServiceImpl.isPhase(CompletionPhase.InsertedSingleItem.class)) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass(), CompletionPhase.CommittingDocuments.class);

    if (invocationCount > 1 && myCompletionType == CompletionType.BASIC) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION);
    }

    final CompletionInitializationContext[] initializationContext = {null};


    Runnable initCmd = () -> {
      Runnable runnable = () -> {
        EditorUtil.fillVirtualSpaceUntilCaret(editor);
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        CompletionAssertions.checkEditorValid(editor);

        final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(caret, project);
        assert psiFile != null : "no PSI file: " + FileDocumentManager.getInstance().getFile(editor.getDocument());
        psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);
        CompletionAssertions.assertCommitSuccessful(editor, psiFile);

        initializationContext[0] = runContributorsBeforeCompletion(editor, psiFile, invocationCount, caret);
      };
      ApplicationManager.getApplication().runWriteAction(runnable);
    };
    if (autopopup) {
      CommandProcessor.getInstance().runUndoTransparentAction(initCmd);
      CompletionAssertions.checkEditorValid(editor);
      if (!restarted && shouldSkipAutoPopup(editor, initializationContext[0].getFile())) {
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
        return;
      }
    } else {
      CommandProcessor.getInstance().executeCommand(project, initCmd, null, null);
    }

    insertDummyIdentifier(initializationContext[0], hasModifiers, invocationCount);
  }

  private CompletionInitializationContext runContributorsBeforeCompletion(Editor editor, PsiFile psiFile, int invocationCount, @NotNull Caret caret) {
    final Ref<CompletionContributor> current = Ref.create(null);
    CompletionInitializationContext context = new CompletionInitializationContext(editor, caret, psiFile, myCompletionType, invocationCount) {
      CompletionContributor dummyIdentifierChanger;

      @Override
      public void setDummyIdentifier(@NotNull String dummyIdentifier) {
        super.setDummyIdentifier(dummyIdentifier);

        if (dummyIdentifierChanger != null) {
          LOG.error("Changing the dummy identifier twice, already changed by " + dummyIdentifierChanger);
        }
        dummyIdentifierChanger = current.get();
      }
    };
    List<CompletionContributor> contributors = CompletionContributor.forLanguage(context.getPositionLanguage());
    Project project = psiFile.getProject();
    List<CompletionContributor> filteredContributors = DumbService.getInstance(project).filterByDumbAwareness(contributors);
    for (final CompletionContributor contributor : filteredContributors) {
      current.set(contributor);
      contributor.beforeCompletion(context);
      CompletionAssertions.checkEditorValid(editor);
      assert !PsiDocumentManager.getInstance(project).isUncommited(editor.getDocument()) : "Contributor " + contributor + " left the document uncommitted";
    }
    return context;
  }

  private static void checkNoWriteAccess() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
        throw new AssertionError("Completion should not be invoked inside write action");
      }
    }
  }

  private static boolean shouldSkipAutoPopup(Editor editor, PsiFile psiFile) {
    int offset = editor.getCaretModel().getOffset();
    int psiOffset = Math.max(0, offset - 1);

    PsiElement elementAt = InjectedLanguageUtil.findInjectedElementNoCommit(psiFile, psiOffset);
    if (elementAt == null) {
      elementAt = psiFile.findElementAt(psiOffset);
    }
    if (elementAt == null) return true;

    Language language = PsiUtilCore.findLanguageFromElement(elementAt);

    for (CompletionConfidence confidence : CompletionConfidenceEP.forLanguage(language)) {
      final ThreeState result = confidence.shouldSkipAutopopup(elementAt, psiFile, offset);
      if (result != ThreeState.UNSURE) {
        LOG.debug(confidence + " has returned shouldSkipAutopopup=" + result);
        return result == ThreeState.YES;
      }
    }
    return false;
  }

  @NotNull
  private LookupImpl obtainLookup(Editor editor, Project project) {
    CompletionAssertions.checkEditorValid(editor);
    LookupImpl existing = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (existing != null && existing.isCompletion()) {
      existing.markReused();
      if (!autopopup) {
        existing.setFocusDegree(LookupImpl.FocusDegree.FOCUSED);
      }
      return existing;
    }

    LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).createLookup(editor, LookupElement.EMPTY_ARRAY, "",
                                                                                    new LookupArranger.DefaultArranger());
    if (editor.isOneLineMode()) {
      lookup.setCancelOnClickOutside(true);
      lookup.setCancelOnOtherWindowOpen(true);
    }
    lookup.setFocusDegree(autopopup ? LookupImpl.FocusDegree.UNFOCUSED : LookupImpl.FocusDegree.FOCUSED);
    return lookup;
  }

  private void doComplete(CompletionInitializationContext initContext,
                          boolean hasModifiers,
                          int invocationCount,
                          OffsetTranslator translator, OffsetsInFile hostOffsets, OffsetsInFile hostCopyOffsets) {
    final Editor editor = initContext.getEditor();
    CompletionAssertions.checkEditorValid(editor);

    CompletionContext context = createCompletionContext(initContext.getFile(), hostCopyOffsets);
    LookupImpl lookup = obtainLookup(editor, initContext.getProject());
    CompletionParameters parameters = createCompletionParameters(invocationCount, context, editor);

    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    if (phase instanceof CompletionPhase.CommittingDocuments) {
      if (phase.indicator != null) {
        phase.indicator.closeAndFinish(false);
      }
      ((CompletionPhase.CommittingDocuments)phase).replaced = true;
    } else {
      CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());
    }

    final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, initContext.getCaret(),
                                                                                  parameters, this,
                                                                                  initContext.getOffsetMap(), hostOffsets, hasModifiers, lookup);
    Disposer.register(indicator, hostCopyOffsets.getOffsets());
    Disposer.register(indicator, context.getOffsetMap());
    Disposer.register(indicator, translator);

    CompletionServiceImpl.setCompletionPhase(synchronous ? new CompletionPhase.Synchronous(indicator) : new CompletionPhase.BgCalculation(indicator));

    indicator.startCompletion(initContext);

    if (!synchronous) {
      return;
    }

    indicator.makeSureLookupIsShown(ourAutoInsertItemTimeout);
    if (indicator.blockingWaitForFinish(ourAutoInsertItemTimeout)) {
      try {
        indicator.getLookup().refreshUi(true, false);
      }
      catch (Exception e) {
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
        LOG.error(e);
        return;
      }

      completionFinished(indicator, hasModifiers);
      return;
    }

    CompletionServiceImpl.setCompletionPhase(new CompletionPhase.BgCalculation(indicator));
    indicator.showLookup();
  }

  private static void checkNotSync(CompletionProgressIndicator indicator, List<LookupElement> allItems) {
    if (CompletionServiceImpl.isPhase(CompletionPhase.Synchronous.class)) {
      LOG.error("sync phase survived: " + allItems + "; indicator=" + CompletionServiceImpl.getCompletionPhase().indicator + "; myIndicator=" + indicator);
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }
  }

  private CompletionParameters createCompletionParameters(int invocationCount,
                                                          final CompletionContext newContext, Editor editor) {
    final int offset = newContext.getStartOffset();
    final PsiFile fileCopy = newContext.file;
    PsiFile originalFile = fileCopy.getOriginalFile();
    final PsiElement insertedElement = findCompletionPositionLeaf(newContext, offset, fileCopy, originalFile);
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, newContext);
    return new CompletionParameters(insertedElement, originalFile, myCompletionType, offset, invocationCount, editor);
  }

  @NotNull
  private static PsiElement findCompletionPositionLeaf(CompletionContext newContext, int offset, PsiFile fileCopy, PsiFile originalFile) {
    final PsiElement insertedElement = newContext.file.findElementAt(offset);
    CompletionAssertions.assertCompletionPositionPsiConsistent(newContext, offset, fileCopy, originalFile, insertedElement);
    return insertedElement;
  }

  private AutoCompletionDecision shouldAutoComplete(final CompletionProgressIndicator indicator, List<LookupElement> items) {
    if (!invokedExplicitly) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    final CompletionParameters parameters = indicator.getParameters();
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

    AutoCompletionContext context = new AutoCompletionContext(parameters, items.toArray(new LookupElement[items.size()]), indicator.getOffsetMap(), indicator.getLookup());
    for (final CompletionContributor contributor : CompletionContributor.forParameters(parameters)) {
      final AutoCompletionDecision decision = contributor.handleAutoCompletionPossibility(context);
      if (decision != null) {
        return decision;
      }
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
    final List<LookupElement> items = indicator.getLookup().getItems();
    if (items.isEmpty()) {
      LookupManager.getInstance(indicator.getProject()).hideActiveLookup();

      Caret nextCaret = getNextCaretToProcess(indicator.getEditor());
      if (nextCaret != null) {
        invokeCompletion(indicator.getProject(), indicator.getEditor(), indicator.getParameters().getInvocationCount(), hasModifiers, false, nextCaret);
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
      final AutoCompletionDecision decision = shouldAutoComplete(indicator, items);
      if (decision == AutoCompletionDecision.SHOW_LOOKUP) {
        CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(indicator));
        indicator.getLookup().setCalculating(false);
        indicator.showLookup();
      }
      else if (decision instanceof AutoCompletionDecision.InsertItem) {
        final Runnable restorePrefix = rememberDocumentState(indicator.getEditor());

        final LookupElement item = ((AutoCompletionDecision.InsertItem)decision).getElement();
        CommandProcessor.getInstance().executeCommand(indicator.getProject(), () -> {
          indicator.setMergeCommand();
          indicator.getLookup().finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR, item);
        }, "Autocompletion", null);

        // the insert handler may have started a live template with completion
        if (CompletionService.getCompletionService().getCurrentCompletion() == null &&
            // ...or scheduled another autopopup
            !CompletionServiceImpl.isPhase(CompletionPhase.CommittingDocuments.class)) {
          CompletionServiceImpl.setCompletionPhase(hasModifiers? new CompletionPhase.InsertedSingleItem(indicator, restorePrefix) : CompletionPhase.NoCompletion);
        }
      } else if (decision == AutoCompletionDecision.CLOSE_LOOKUP) {
        LookupManager.getInstance(indicator.getProject()).hideActiveLookup();
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

  private void insertDummyIdentifier(final CompletionInitializationContext initContext,
                                     final boolean hasModifiers,
                                     final int invocationCount) {
    CompletionAssertions.checkEditorValid(initContext.getEditor());

    Editor hostEditor = InjectedLanguageUtil.getTopLevelEditor(initContext.getEditor());
    OffsetsInFile topLevelOffsets = new OffsetsInFile(initContext.getFile(), initContext.getOffsetMap()).toTopLevelFile();
    OffsetMap hostMap = topLevelOffsets.getOffsets();

    PsiFile hostCopy = createFileCopy(topLevelOffsets.getFile());
    Document copyDocument = hostCopy.getViewProvider().getDocument();
    assert copyDocument != null : "no document";
    OffsetsInFile copyOffsets = topLevelOffsets.toFileCopy(hostCopy);
    OffsetTranslator translator = new OffsetTranslator(hostEditor.getDocument(), initContext.getFile(), copyDocument);

    CompletionAssertions.checkEditorValid(initContext.getEditor());
    String dummyIdentifier = initContext.getDummyIdentifier();
    if (!StringUtil.isEmpty(dummyIdentifier)) {
      int startOffset = hostMap.getOffset(CompletionInitializationContext.START_OFFSET);
      int endOffset = hostMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
      copyDocument.replaceString(startOffset, endOffset, dummyIdentifier);
    }
    CompletionAssertions.checkEditorValid(initContext.getEditor());

    Project project = initContext.getProject();

    if (!synchronous) {
      if (CompletionServiceImpl.isPhase(CompletionPhase.NoCompletion.getClass()) ||
          !CompletionServiceImpl.assertPhase(CompletionPhase.CommittingDocuments.class)) {
        Disposer.dispose(translator);
        return;
      }

      final CompletionPhase.CommittingDocuments phase = (CompletionPhase.CommittingDocuments)CompletionServiceImpl.getCompletionPhase();

      AutoPopupController.runTransactionWithEverythingCommitted(project, () -> {
        if (phase.checkExpired() || isAnythingInvalidatedAfterCommit(initContext, hostCopy)) {
          Disposer.dispose(translator);
          return;
        }
        doComplete(initContext, hasModifiers, invocationCount, translator, topLevelOffsets, copyOffsets);
      });
    }
    else {
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      if (isAnythingInvalidatedAfterCommit(initContext, hostCopy)) {
        Disposer.dispose(translator);
        return;
      }

      doComplete(initContext, hasModifiers, invocationCount, translator, topLevelOffsets, copyOffsets);
    }
  }

  private static boolean isAnythingInvalidatedAfterCommit(CompletionInitializationContext initContext, PsiFile hostCopy) {
    return !initContext.getFile().isValid() || !hostCopy.isValid() ||
           !CompletionAssertions.isEditorValid(initContext.getEditor());
  }

  private static CompletionContext createCompletionContext(PsiFile originalFile, OffsetsInFile hostCopyOffsets) {
    CompletionAssertions.assertHostInfo(hostCopyOffsets.getFile(), hostCopyOffsets.getOffsets());

    int hostStartOffset = hostCopyOffsets.getOffsets().getOffset(CompletionInitializationContext.START_OFFSET);
    OffsetsInFile result = hostCopyOffsets;
    OffsetsInFile translatedOffsets = hostCopyOffsets.toInjectedIfAny(hostStartOffset);
    if (translatedOffsets != hostCopyOffsets) {
      PsiFile injected = translatedOffsets.getFile();
      if (injected instanceof PsiFileImpl) {
        ((PsiFileImpl)injected).setOriginalFile(originalFile);
      }
      DocumentWindow documentWindow = InjectedLanguageUtil.getDocumentWindow(injected);
      CompletionAssertions.assertInjectedOffsets(hostStartOffset, injected, documentWindow);

      if (injected.getTextRange().contains(translatedOffsets.getOffsets().getOffset(CompletionInitializationContext.START_OFFSET))) {
        result = translatedOffsets;
      }
    }

    return new CompletionContext(result.getFile(), result.getOffsets());
  }

  protected void lookupItemSelected(final CompletionProgressIndicator indicator, @NotNull final LookupElement item, final char completionChar,
                                         final List<LookupElement> items) {
    if (indicator.isAutopopupCompletion()) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_BASIC);
    }

    WatchingInsertionContext context = null;
    try {
      StatisticsUpdate update = StatisticsUpdate.collectStatisticChanges(item);
      context = insertItemHonorBlockSelection(indicator, item, completionChar, items, update);
      update.trackStatistics(context);
    }
    finally {
      afterItemInsertion(indicator, context == null ? null : context.getLaterRunnable());
    }

  }

  private static WatchingInsertionContext insertItemHonorBlockSelection(CompletionProgressIndicator indicator,
                                                                        LookupElement item,
                                                                        char completionChar,
                                                                        List<LookupElement> items,
                                                                        StatisticsUpdate update) {
    final Editor editor = indicator.getEditor();

    final int caretOffset = indicator.getCaret().getOffset();
    final int idEndOffset = indicator.getOffsetMap().containsOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) ?
                            indicator.getIdentifierEndOffset() :
                            CompletionInitializationContext.calcDefaultIdentifierEnd(editor, caretOffset);
    final int idEndOffsetDelta = idEndOffset - caretOffset;

    WatchingInsertionContext context;
    if (editor.getCaretModel().supportsMultipleCarets()) {
      Ref<WatchingInsertionContext> lastContext = Ref.create();
      Editor hostEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
      OffsetsInFile topLevelOffsets = indicator.getHostOffsets();
      hostEditor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          PsiDocumentManager.getInstance(indicator.getProject()).commitDocument(hostEditor.getDocument());
          OffsetsInFile targetOffsets = topLevelOffsets.toInjectedIfAny(caret.getOffset());
          PsiFile targetFile = targetOffsets.getFile();
          Editor targetEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, targetFile);
          int targetCaretOffset = targetEditor.getCaretModel().getOffset();
          int idEnd = targetCaretOffset + idEndOffsetDelta;
          if (idEnd > targetEditor.getDocument().getTextLength()) {
            idEnd = targetCaretOffset; // no replacement by Tab when offsets gone wrong for some reason
          }
          WatchingInsertionContext currentContext = insertItem(indicator, item, completionChar, items, update,
                                                                                    targetEditor, targetFile,
                                                                                    targetCaretOffset, idEnd,
                                                                                    targetOffsets.getOffsets());
          lastContext.set(currentContext);
        }
      });
      context = lastContext.get();
    } else {
      context = insertItem(indicator, item, completionChar, items, update, editor, indicator.getParameters().getOriginalFile(), caretOffset,
                           idEndOffset, indicator.getOffsetMap());
    }
    if (context.shouldAddCompletionChar()) {
      WriteAction.run(() -> addCompletionChar(context, item, editor, indicator, completionChar));
    }
    return context;
  }

  public static void afterItemInsertion(final CompletionProgressIndicator indicator, final Runnable laterRunnable) {
    if (laterRunnable != null) {
      ActionTracker tracker = new ActionTracker(indicator.getEditor(), indicator);
      Runnable wrapper = () -> {
        if (!indicator.getProject().isDisposed() && !tracker.hasAnythingHappened()) {
          laterRunnable.run();
        }
        indicator.disposeIndicator();
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        wrapper.run();
      }
      else {
        TransactionGuard.getInstance().submitTransactionLater(indicator, wrapper);
      }
    }
    else {
      indicator.disposeIndicator();
    }
  }

  private static WatchingInsertionContext insertItem(final CompletionProgressIndicator indicator,
                                                                          final LookupElement item,
                                                                          final char completionChar,
                                                                          List<LookupElement> items,
                                                                          final StatisticsUpdate update,
                                                                          final Editor editor,
                                                                          final PsiFile psiFile,
                                                                          final int caretOffset,
                                                                          final int idEndOffset, final OffsetMap offsetMap) {
    editor.getCaretModel().moveToOffset(caretOffset);
    final int initialStartOffset = caretOffset - item.getLookupString().length();
    assert initialStartOffset >= 0 : "negative startOffset: " + caretOffset + "; " + item.getLookupString();

    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, initialStartOffset);
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, caretOffset);
    offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, idEndOffset);

    WatchingInsertionContext context = new WatchingInsertionContext(offsetMap, psiFile, completionChar, items, editor);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        if (caretOffset < idEndOffset && completionChar == Lookup.REPLACE_SELECT_CHAR) {
          editor.getDocument().deleteString(caretOffset, idEndOffset);
        }

        assert context.getStartOffset() >= 0 : "stale startOffset: was " + initialStartOffset + "; selEnd=" + caretOffset + "; idEnd=" + idEndOffset + "; file=" + context.getFile();
        assert context.getTailOffset() >= 0 : "stale tail: was " + initialStartOffset + "; selEnd=" + caretOffset + "; idEnd=" + idEndOffset + "; file=" + context.getFile();

        Project project = indicator.getProject();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        item.handleInsert(context);
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      }
      finally {
        context.stopWatching();
      }

      EditorModificationUtil.scrollToCaret(editor);
    });
    update.addSparedChars(indicator, item, context);
    return context;
  }

  private static void addCompletionChar(WatchingInsertionContext context,
                                        LookupElement item,
                                        Editor editor, CompletionProgressIndicator indicator, char completionChar) {
    if (!context.getOffsetMap().containsOffset(InsertionContext.TAIL_OFFSET)) {
      LOG.info("tailOffset<0 after inserting " + item + " of " + item.getClass() + "; invalidated at: " + context.invalidateTrace + "\n--------");
    }
    else if (!CompletionAssertions.isEditorValid(context.getEditor())) {
      LOG.info("Injected editor invalidated " + context.getEditor());
    }
    else {
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
    }
    if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      Language language = PsiUtilBase.getLanguageInEditor(editor, indicator.getProject());
      if (language != null) {
        for (SmartEnterProcessor processor : SmartEnterProcessors.INSTANCE.forKey(language)) {
          if (processor.processAfterCompletion(editor, indicator.getParameters().getOriginalFile())) break;
        }
      }
    }
    else {
      DataContext dataContext = DataManager.getInstance().getDataContext(editor.getContentComponent());
      EditorActionManager.getInstance().getTypedAction().getHandler().execute(editor, completionChar, dataContext);
    }
  }

  private static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

  private static boolean isCopyUpToDate(Document document, @NotNull PsiFile copyFile, @NotNull PsiFile originalFile) {
    if (!copyFile.getClass().equals(originalFile.getClass()) ||
        !copyFile.isValid() ||
        !copyFile.getName().equals(originalFile.getName())) {
      return false;
    }
    // the psi file cache might have been cleared by some external activity,
    // in which case PSI-document sync may stop working
    PsiFile current = PsiDocumentManager.getInstance(copyFile.getProject()).getPsiFile(document);
    return current != null && current.getViewProvider().getPsi(copyFile.getLanguage()) == copyFile;
  }

  private static PsiFile createFileCopy(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    boolean mayCacheCopy = file.isPhysical() &&
                           // we don't want to cache code fragment copies even if they appear to be physical
                           virtualFile != null && virtualFile.isInLocalFileSystem();
    if (mayCacheCopy) {
      final Pair<PsiFile, Document> cached = SoftReference.dereference(file.getUserData(FILE_COPY_KEY));
      if (cached != null && isCopyUpToDate(cached.second, cached.first, file)) {
        final PsiFile copy = cached.first;
        final Document document = cached.second;
        CompletionAssertions.assertCorrectOriginalFile("Cached", file, copy);
        Document originalDocument = file.getViewProvider().getDocument();
        assert originalDocument != null;
        assert originalDocument.getTextLength() == file.getTextLength() : originalDocument;
        document.replaceString(0, document.getTextLength(), originalDocument.getImmutableCharSequence());
        return copy;
      }
    }

    final PsiFile copy = (PsiFile)file.copy();
    if (copy.isPhysical() || copy.getViewProvider().isEventSystemEnabled()) {
      LOG.error("File copy should be non-physical and non-event-system-enabled! Language=" + file.getLanguage() + "; file=" + file + " of " + file.getClass());
    }
    CompletionAssertions.assertCorrectOriginalFile("New", file, copy);

    if (mayCacheCopy) {
      final Document document = copy.getViewProvider().getDocument();
      assert document != null;
      syncAcceptSlashR(file.getViewProvider().getDocument(), document);
      file.putUserData(FILE_COPY_KEY, new SoftReference<>(Pair.create(copy, document)));
    }
    return copy;
  }

  private static void syncAcceptSlashR(Document originalDocument, Document documentCopy) {
    if (!(originalDocument instanceof DocumentImpl) || !(documentCopy instanceof DocumentImpl)) {
      return;
    }

    ((DocumentImpl) documentCopy).setAcceptSlashR(((DocumentImpl) originalDocument).acceptsSlashR());
  }

  private static boolean isAutocompleteOnInvocation(final CompletionType type) {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (type == CompletionType.SMART) {
      return settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
    }
    return settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
  }

  private static Runnable rememberDocumentState(final Editor _editor) {
    final Editor editor = InjectedLanguageUtil.getTopLevelEditor(_editor);
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

  @TestOnly
  public static void setAutoInsertTimeout(int timeout) {
    ourAutoInsertItemTimeout = timeout;
  }
}

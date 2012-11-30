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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.reference.SoftReference;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CodeCompletionHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private final CompletionType myCompletionType;
  final boolean invokedExplicitly;
  final boolean synchronous;
  final boolean autopopup;

  public CodeCompletionHandlerBase(final CompletionType completionType) {
    this(completionType, true, false, true);
  }

  public CodeCompletionHandlerBase(CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {
    myCompletionType = completionType;
    LOG.assertTrue(myCompletionType != CompletionType.CLASS_NAME, "Class name completion shouldn't be invoked directly");
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
    final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    assert psiFile != null : "no PSI file: " + FileDocumentManager.getInstance().getFile(editor.getDocument());

    checkNoWriteAccess();

    checkEditorValid2(editor);

    if (editor.isViewer()) {
      editor.getDocument().fireReadOnlyModificationAttempt();
      return;
    }

    if (invokedExplicitly) {
      CompletionLookupArranger.applyLastCompletionStatisticsUpdate();
    }

    final Document document = editor.getDocument();
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;

    if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
      return;
    }

    psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);

    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    boolean repeated = phase.indicator != null && phase.indicator.isRepeatedInvocation(myCompletionType, editor);
    /*
    if (repeated && isAutocompleteCommonPrefixOnInvocation() && phase.fillInCommonPrefix()) {
      return;
    }
    */

    int newTime = phase.newCompletionStarted(time, repeated);
    if (invokedExplicitly) {
      time = newTime;
    }
    if (CompletionServiceImpl.isPhase(CompletionPhase.InsertedSingleItem.class)) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass(), CompletionPhase.CommittingDocuments.class);

    if (time > 1) {
      if (myCompletionType == CompletionType.BASIC) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION);
      }
    }

    final CompletionInitializationContext[] initializationContext = {null};


    Runnable initCmd = new Runnable() {
      @Override
      public void run() {

        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

            EditorUtil.fillVirtualSpaceUntilCaret(editor);
            documentManager.commitAllDocuments();

            int docLength = editor.getDocument().getTextLength();
            int psiLength = psiFile.getTextLength();
            if (docLength != psiLength) {
              if (ApplicationManagerEx.getApplicationEx().isInternal()) {
                String docText = editor.getDocument().getText();
                String psiText = psiFile.getText();
                String message = "unsuccessful commit: (injected=" +(editor instanceof EditorWindow) +"); document " + System.identityHashCode(editor.getDocument()) + "; " +
                                 "docText=\n'" + docText +"' (" + docText.length() +" chars; .length()="+ docLength+")\n" +
                                 "; fileText=\n'" + psiText + "' (" + psiText.length() +" chars; .length()="+ psiLength+")\n"
                                 ;
                throw new AssertionError(message);
              }

              throw new AssertionError("unsuccessful commit: injected=" + (editor instanceof EditorWindow));
            }

            checkEditorValid2(editor);

            final Ref<CompletionContributor> current = Ref.create(null);
            initializationContext[0] = new CompletionInitializationContext(editor, psiFile, myCompletionType) {
              CompletionContributor dummyIdentifierChanger;
              @Override
              public void setFileCopyPatcher(@NotNull FileCopyPatcher fileCopyPatcher) {
                super.setFileCopyPatcher(fileCopyPatcher);

                if (dummyIdentifierChanger != null) {
                  LOG.error("Changing the dummy identifier twice, already changed by " + dummyIdentifierChanger);
                }
                dummyIdentifierChanger = current.get();
              }
            };
            for (final CompletionContributor contributor : CompletionContributor.forLanguage(initializationContext[0].getPositionLanguage())) {
              if (DumbService.getInstance(project).isDumb() && !DumbService.isDumbAware(contributor)) {
                continue;
              }

              current.set(contributor);
              contributor.beforeCompletion(initializationContext[0]);
              checkEditorValid2(editor);
              assert !documentManager.isUncommited(document) : "Contributor " + contributor + " left the document uncommitted";
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    };
    if (autopopup) {
      CommandProcessor.getInstance().runUndoTransparentAction(initCmd);
      if (!restarted && shouldSkipAutoPopup(editor, psiFile)) {
        return;
      }
    } else {
      CommandProcessor.getInstance().executeCommand(project, initCmd, null, null);
    }

    insertDummyIdentifier(initializationContext[0], hasModifiers, time);
  }

  private static void checkEditorValid2(Editor editor) {
    if (editor instanceof EditorWindow && !((EditorWindow)editor).isValid()) {
      throw new AssertionError();
    }
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

    Language language = PsiUtilBase.findLanguageFromElement(elementAt);

    for (CompletionConfidence confidence : CompletionConfidenceEP.forLanguage(language)) {
      final ThreeState result = confidence.shouldSkipAutopopup(elementAt, psiFile, offset);
      if (result == ThreeState.YES) {
        return true;
      }
      if (result == ThreeState.NO) {
        return false;
      }
    }
    return false;
  }

  @NotNull
  private LookupImpl obtainLookup(Editor editor) {
    checkEditorValid2(editor);
    LookupImpl existing = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (existing != null && existing.isCompletion()) {
      existing.markReused();
      if (!autopopup) {
        existing.setFocused(true);
      }
      return existing;
    }

    LookupImpl lookup = (LookupImpl)LookupManager.getInstance(editor.getProject()).createLookup(editor, LookupElement.EMPTY_ARRAY, "",
                                                                                                new LookupArranger.DefaultArranger());
    if (editor.isOneLineMode()) {
      lookup.setCancelOnClickOutside(true);
      lookup.setCancelOnOtherWindowOpen(true);
    }
    lookup.setFocused(!autopopup);
    return lookup;
  }

  private void doComplete(CompletionInitializationContext initContext,
                          boolean hasModifiers,
                          int invocationCount,
                          PsiFile hostFile,
                          int hostStartOffset, Editor hostEditor, OffsetMap hostMap, OffsetTranslator translator) {
    final Editor editor = initContext.getEditor();
    checkEditorValid2(editor);

    CompletionContext context = createCompletionContext(hostFile, hostStartOffset, hostEditor, hostMap);
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

    final Semaphore freezeSemaphore = new Semaphore();
    freezeSemaphore.down();
    final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, parameters, this, freezeSemaphore,
                                                                                  initContext.getOffsetMap(), hasModifiers);
    Disposer.register(indicator, hostMap);
    Disposer.register(indicator, context.getOffsetMap());
    Disposer.register(indicator, translator);

    CompletionServiceImpl.setCompletionPhase(synchronous ? new CompletionPhase.Synchronous(indicator) : new CompletionPhase.BgCalculation(indicator));

    final AtomicReference<LookupElement[]> data = indicator.startCompletion(initContext);

    if (!synchronous) {
      return;
    }

    if (freezeSemaphore.waitFor(2000)) {
      final LookupElement[] allItems = data.get();
      if (allItems != null && !indicator.isRunning() && !indicator.isCanceled()) { // the completion is really finished, now we may auto-insert or show lookup
        completionFinished(initContext.getStartOffset(), initContext.getSelectionEndOffset(), indicator, allItems, hasModifiers);
        checkNotSync(indicator, allItems);
        return;
      }
    }

    CompletionServiceImpl.setCompletionPhase(new CompletionPhase.BgCalculation(indicator));
    indicator.showLookup();
  }

  private static void checkNotSync(CompletionProgressIndicator indicator, LookupElement[] allItems) {
    if (CompletionServiceImpl.isPhase(CompletionPhase.Synchronous.class)) {
      LOG.error("sync phase survived: " + Arrays.toString(allItems) + "; indicator=" + CompletionServiceImpl.getCompletionPhase().indicator + "; myIndicator=" + indicator);
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }
  }

  private CompletionParameters createCompletionParameters(int invocationCount, final CompletionContext newContext, final Editor editor) {
    final int offset = newContext.getStartOffset();
    final PsiFile fileCopy = newContext.file;
    PsiFile originalFile = fileCopy.getOriginalFile();
    final PsiElement insertedElement = findCompletionPositionLeaf(newContext, offset, fileCopy, originalFile);
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, newContext);
    return new CompletionParameters(insertedElement, originalFile, myCompletionType, offset, invocationCount, obtainLookup(editor));
  }

  @NotNull
  private static PsiElement findCompletionPositionLeaf(CompletionContext newContext, int offset, PsiFile fileCopy, PsiFile originalFile) {
    final PsiElement insertedElement = newContext.file.findElementAt(offset);
    if (insertedElement == null) {
      LOG.error(LogMessageEx.createEvent("No element at insertion offset", "offset=" + newContext.getStartOffset() + "\n" + DebugUtil.currentStackTrace(),
                                         createFileTextAttachment(fileCopy, originalFile), createAstAttachment(fileCopy, originalFile)));
    }

    LOG.assertTrue(fileCopy.findElementAt(offset) == insertedElement, "wrong offset");

    final TextRange range = insertedElement.getTextRange();
    if (!range.substring(fileCopy.getText()).equals(insertedElement.getText())) {
      LOG.error(LogMessageEx.createEvent("Inconsistent completion tree", "range=" + range + "\n" + DebugUtil.currentStackTrace(),
                                         createFileTextAttachment(fileCopy, originalFile), createAstAttachment(fileCopy, originalFile),
                                         new Attachment("Element at caret.txt", insertedElement.getText())));
    }
    return insertedElement;
  }

  private static Attachment createAstAttachment(PsiFile fileCopy, final PsiFile originalFile) {
    return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath() + " syntactic tree.txt", DebugUtil.psiToString(fileCopy, false));
  }

  private static Attachment createFileTextAttachment(PsiFile fileCopy, final PsiFile originalFile) {
    return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath(), fileCopy.getText());
  }

  private AutoCompletionDecision shouldAutoComplete(final CompletionProgressIndicator indicator, final LookupElement[] items) {
    if (!invokedExplicitly) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    final CompletionParameters parameters = indicator.getParameters();
    final LookupElement item = items[0];
    if (items.length == 1) {
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
    if (items.length == 1 && getAutocompletionPolicy(item) == AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE) {
      return AutoCompletionDecision.insertItem(item);
    }

    AutoCompletionContext context = new AutoCompletionContext(parameters, items, indicator.getOffsetMap(), indicator.getLookup());
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
    final AutoCompletionPolicy policy = AutoCompletionPolicy.getPolicy(element);
    if (policy != null) {
      return policy;
    }

    final LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
    if (item != null) {
      return item.getAutoCompletionPolicy();
    }

    return null;
  }

  private static boolean isInsideIdentifier(final OffsetMap offsetMap) {
    return offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != offsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }


  protected void completionFinished(final int offset1,
                                    final int offset2,
                                    final CompletionProgressIndicator indicator,
                                    final LookupElement[] items, boolean hasModifiers) {
    if (items.length == 0) {
      LookupManager.getInstance(indicator.getProject()).hideActiveLookup();
      indicator.handleEmptyLookup(true);
      checkNotSync(indicator, items);
      return;
    }

    LOG.assertTrue(!indicator.isRunning(), "running");
    LOG.assertTrue(!indicator.isCanceled(), "canceled");

    indicator.getLookup().refreshUi(true, false);
    final AutoCompletionDecision decision = shouldAutoComplete(indicator, items);
    if (decision == AutoCompletionDecision.SHOW_LOOKUP) {
      CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(indicator));
      indicator.getLookup().setCalculating(false);
      indicator.showLookup();
    }
    else if (decision instanceof AutoCompletionDecision.InsertItem) {
      final Runnable restorePrefix = rememberDocumentState(indicator.getEditor());

      final LookupElement item = ((AutoCompletionDecision.InsertItem)decision).getElement();
      CommandProcessor.getInstance().executeCommand(indicator.getProject(), new Runnable() {
                                                      @Override
                                                      public void run() {
                                                        indicator.setMergeCommand();
                                                        indicator.getLookup().finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR, item);
                                                      }
                                                    }, "Autocompletion", null);



      // the insert handler may have started a live template with completion
      if (CompletionService.getCompletionService().getCurrentCompletion() == null &&
          !ApplicationManager.getApplication().isUnitTestMode()) {
        CompletionServiceImpl.setCompletionPhase(hasModifiers? new CompletionPhase.InsertedSingleItem(indicator, restorePrefix) : CompletionPhase.NoCompletion);
      }
      checkNotSync(indicator, items);
    } else if (decision == AutoCompletionDecision.CLOSE_LOOKUP) {
      LookupManager.getInstance(indicator.getProject()).hideActiveLookup();
      checkNotSync(indicator, items);
    }
  }

  private void insertDummyIdentifier(final CompletionInitializationContext initContext,
                                     final boolean hasModifiers,
                                     final int invocationCount) {
    final PsiFile originalFile = initContext.getFile();
    final PsiFile[] fileCopy = {null};
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        AccessToken token = WriteAction.start();
        try {
          fileCopy[0] = createFileCopy(originalFile);
        }
        finally {
          token.finish();
        }
      }
    });
    final PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(fileCopy[0]);
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(hostFile.getProject());
    final int hostStartOffset = injectedLanguageManager.injectedToHost(fileCopy[0], initContext.getStartOffset());
    final Editor hostEditor = InjectedLanguageUtil.getTopLevelEditor(initContext.getEditor());

    final OffsetMap hostMap = new OffsetMap(hostEditor.getDocument());
    final OffsetMap original = initContext.getOffsetMap();
    for (final OffsetKey key : original.getAllOffsets()) {
      hostMap.addOffset(key, injectedLanguageManager.injectedToHost(fileCopy[0], original.getOffset(key)));
    }

    final Document document = fileCopy[0].getViewProvider().getDocument();
    assert document != null : "no document";
    final OffsetTranslator translator = new OffsetTranslator(initContext.getEditor().getDocument(), initContext.getFile(), document);

    checkEditorValid2(initContext.getEditor());
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            initContext.getFileCopyPatcher().patchFileCopy(fileCopy[0], document, initContext.getOffsetMap());
          }
        });
      }
    });
    checkEditorValid2(initContext.getEditor());
    final Document hostDocument = hostFile.getViewProvider().getDocument();
    assert hostDocument != null : "no host document";

    final Project project = hostFile.getProject();

    if (!synchronous) {
      if (CompletionServiceImpl.isPhase(CompletionPhase.NoCompletion.getClass()) ||
          !CompletionServiceImpl.assertPhase(CompletionPhase.CommittingDocuments.class)) {
        Disposer.dispose(translator);
        return;
      }

      final CompletionPhase.CommittingDocuments phase = (CompletionPhase.CommittingDocuments)CompletionServiceImpl.getCompletionPhase();

      CompletionAutoPopupHandler.runLaterWithCommitted(project, hostDocument, new Runnable() {
        @Override
        public void run() {
          if (phase.checkExpired()) {
            Disposer.dispose(translator);
            return;
          }
          doComplete(initContext, hasModifiers, invocationCount, hostFile, hostStartOffset, hostEditor, hostMap, translator);
        }
      });
    }
    else {
      PsiDocumentManager.getInstance(hostFile.getProject()).commitDocument(hostDocument);

      doComplete(initContext, hasModifiers, invocationCount, hostFile, hostStartOffset, hostEditor, hostMap, translator);
    }
  }

  private static CompletionContext createCompletionContext(PsiFile hostFile,
                                                           int hostStartOffset,
                                                           Editor hostEditor,
                                                           OffsetMap hostMap) {
    assert hostFile.isValid() : "file became invalid: " + hostFile.getClass();
    if (hostMap.getOffset(CompletionInitializationContext.START_OFFSET) >= hostFile.getTextLength()) {
      throw new AssertionError("startOffset outside the host file: " + hostMap.getOffset(CompletionInitializationContext.START_OFFSET) + "; " + hostFile);
    }

    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(hostFile.getProject());
    CompletionContext context;
    PsiFile injected = InjectedLanguageUtil.findInjectedPsiNoCommit(hostFile, hostStartOffset);
    if (injected != null) {
      TextRange host = injectedLanguageManager.injectedToHost(injected, injected.getTextRange());
      assert hostStartOffset >= host.getStartOffset() : "startOffset before injected";
      assert hostStartOffset <= host.getEndOffset() : "startOffset after injected";

      EditorWindow injectedEditor = (EditorWindow)InjectedLanguageUtil
        .getEditorForInjectedLanguageNoCommit(hostEditor, hostFile, hostStartOffset);
      assert injected == injectedEditor.getInjectedFile();
      final OffsetMap map = new OffsetMap(injectedEditor.getDocument());
      for (final OffsetKey key : hostMap.getAllOffsets()) {
        map.addOffset(key, injectedEditor.logicalPositionToOffset(injectedEditor.hostToInjected(hostEditor.offsetToLogicalPosition(hostMap.getOffset(key)))));
      }
      context = new CompletionContext(hostFile.getProject(), injectedEditor, injected, map);
      assert hostStartOffset == injectedLanguageManager.injectedToHost(injected, context.getStartOffset()) : "inconsistent injected offset translation";
    } else {
      context = new CompletionContext(hostFile.getProject(), hostEditor, hostFile, hostMap);
    }

    assert context.getStartOffset() < context.file.getTextLength() : "start outside the file";
    assert context.getStartOffset() >= 0 : "start < 0";

    return context;
  }

  private boolean isAutocompleteCommonPrefixOnInvocation() {
    return invokedExplicitly && CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
  }

  protected void lookupItemSelected(final CompletionProgressIndicator indicator, @NotNull final LookupElement item, final char completionChar,
                                         final List<LookupElement> items) {
    if (indicator.isAutopopupCompletion()) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_BASIC);
    }

    final CompletionLookupArranger.StatisticsUpdate update =
      CompletionLookupArranger.collectStatisticChanges(item, indicator.getParameters().getLookup());

    final Editor editor = indicator.getEditor();

    final int caretOffset = editor.getCaretModel().getOffset();
    final int idDelta = indicator.getIdentifierEndOffset() - caretOffset;

    WatchingInsertionContext context = null;
    if (editor.getSelectionModel().hasBlockSelection() && editor.getSelectionModel().getBlockSelectionEnds().length > 0) {
      List<RangeMarker> insertionPoints = new ArrayList<RangeMarker>();
      for (int endOffset : editor.getSelectionModel().getBlockSelectionEnds()) {
        insertionPoints.add(editor.getDocument().createRangeMarker(endOffset, endOffset));
      }

      List<RangeMarker> caretsAfter = new ArrayList<RangeMarker>();
      for (RangeMarker insertionPoint : insertionPoints) {
        if (insertionPoint.isValid()) {
          context = insertItem(indicator, item, completionChar, items, update, editor, insertionPoint.getStartOffset(), idDelta);
          int offset = editor.getCaretModel().getOffset();
          caretsAfter.add(editor.getDocument().createRangeMarker(offset, offset));
        }
      }
      assert context != null;

      restoreBlockSelection(editor, caretsAfter);

      for (RangeMarker insertionPoint : insertionPoints) {
        insertionPoint.dispose();
      }
      for (RangeMarker marker : caretsAfter) {
        marker.dispose();
      }
      
    } else {
      context = insertItem(indicator, item, completionChar, items, update, editor, caretOffset, idDelta);
    }
    CompletionLookupArranger.trackStatistics(context, update);

    final Runnable runnable = context.getLaterRunnable();
    if (runnable != null) {
      final Runnable runnable1 = new Runnable() {
        @Override
        public void run() {
          if (!indicator.getProject().isDisposed()) {
            runnable.run();
          }
          indicator.disposeIndicator();
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        runnable1.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable1);
      }
    }
    else {
      indicator.disposeIndicator();
    }
  }

  private static void restoreBlockSelection(Editor editor, List<RangeMarker> caretsAfter) {
    int column = -1;
    int minLine = Integer.MAX_VALUE;
    int maxLine = -1;
    for (RangeMarker marker : caretsAfter) {
      if (marker.isValid()) {
        LogicalPosition lp = editor.offsetToLogicalPosition(marker.getStartOffset());
        if (column == -1) {
          column = lp.column;
        } else if (column != lp.column) {
          return;
        }
        minLine = Math.min(minLine, lp.line);
        maxLine = Math.max(maxLine, lp.line);
      }
    }
    editor.getSelectionModel().setBlockSelection(new LogicalPosition(minLine, column), new LogicalPosition(maxLine, column));
  }

  private static WatchingInsertionContext insertItem(final CompletionProgressIndicator indicator,
                                                     final LookupElement item,
                                                     final char completionChar,
                                                     List<LookupElement> items,
                                                     final CompletionLookupArranger.StatisticsUpdate update,
                                                     final Editor editor, final int caretOffset, final int idDelta) {
    editor.getCaretModel().moveToOffset(caretOffset);
    final int initialStartOffset = caretOffset - item.getLookupString().length();
    assert initialStartOffset >= 0 : "negative startOffset: " + caretOffset + "; " + item.getLookupString();
    final int idEndOffset = caretOffset + Math.max(idDelta, 0);
    
    indicator.getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, initialStartOffset);
    indicator.getOffsetMap().addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, caretOffset);
    indicator.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, idEndOffset);

    final WatchingInsertionContext context = new WatchingInsertionContext(indicator, completionChar, items, editor);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (caretOffset != idEndOffset && completionChar == Lookup.REPLACE_SELECT_CHAR) {
          editor.getDocument().deleteString(caretOffset, idEndOffset);
        }

        assert context.getStartOffset() >= 0 : "stale startOffset: was " + initialStartOffset + "; selEnd=" + caretOffset + "; idEnd=" + idEndOffset + "; file=" + context.getFile();
        assert context.getTailOffset() >= 0 : "stale tail: was " + initialStartOffset + "; selEnd=" + caretOffset + "; idEnd=" + idEndOffset + "; file=" + context.getFile();

        item.handleInsert(context);
        Project project = indicator.getProject();
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();

        if (context.shouldAddCompletionChar()) {
          addCompletionChar(project, context, item, editor, indicator, completionChar);
        }
        context.stopWatching();
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    });
    update.addSparedChars(indicator, item, context, completionChar);
    return context;
  }

  private static void addCompletionChar(Project project,
                                        WatchingInsertionContext context,
                                        LookupElement item,
                                        Editor editor, CompletionProgressIndicator indicator, char completionChar) {
    int tailOffset = context.getTailOffset();
    if (tailOffset < 0) {
      LOG.info("tailOffset<0 after inserting " + item + " of " + item.getClass() + "; invalidated at: " + context.invalidateTrace + "\n--------");
    }
    else {
      editor.getCaretModel().moveToOffset(tailOffset);
    }
    if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      final Language language = PsiUtilBase.getLanguageInEditor(editor, project);
      final List<SmartEnterProcessor> processors = SmartEnterProcessors.INSTANCE.forKey(language);
      if (processors.size() > 0) {
        for (SmartEnterProcessor processor : processors) {
          processor.process(project, editor, indicator.getParameters().getOriginalFile());
        }
      }
    }
    else {
      DataContext dataContext = DataManager.getInstance().getDataContext(editor.getContentComponent());
      EditorActionManager.getInstance().getTypedAction().getHandler().execute(editor, completionChar, dataContext);
    }
  }

  public static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

  private static boolean isCopyUpToDate(Document document, @NotNull PsiFile file) {
    if (!file.isValid()) {
      return false;
    }
    // the psi file cache might have been cleared by some external activity,
    // in which case PSI-document sync may stop working
    PsiFile current = PsiDocumentManager.getInstance(file.getProject()).getPsiFile(document);
    return current != null && current.getViewProvider().getPsi(file.getLanguage()) == file;
  }

  private static PsiFile createFileCopy(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (file.isPhysical() && virtualFile != null && virtualFile.getFileSystem() == LocalFileSystem.getInstance()
        // must not cache injected file copy, since it does not reflect changes in host document
        && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      final SoftReference<Pair<PsiFile, Document>> reference = file.getUserData(FILE_COPY_KEY);
      if (reference != null) {
        final Pair<PsiFile, Document> pair = reference.get();
        if (pair != null && pair.first.getClass().equals(file.getClass()) && isCopyUpToDate(pair.second, pair.first)) {
          final PsiFile copy = pair.first;
          if (copy.getViewProvider().getModificationStamp() > file.getViewProvider().getModificationStamp()) {
            ((PsiModificationTrackerImpl) file.getManager().getModificationTracker()).incCounter();
          }
          final Document document = pair.second;
          assert document != null;
          document.setText(file.getText());
          return copy;
        }
      }
    }

    final PsiFile copy = (PsiFile)file.copy();
    final Document document = copy.getViewProvider().getDocument();
    assert document != null;
    file.putUserData(FILE_COPY_KEY, new SoftReference<Pair<PsiFile,Document>>(Pair.create(copy, document)));
    return copy;
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

    return new Runnable() {
      @Override
      public void run() {
        DocumentEx document = (DocumentEx) editor.getDocument();

        document.replaceString(0, document.getTextLength(), documentText);
        editor.getCaretModel().moveToOffset(caret);
        editor.getSelectionModel().setSelection(selStart, selEnd);

        editor.getScrollingModel().scrollHorizontally(hOffset);
        editor.getScrollingModel().scrollVertically(vOffset);
      }
    };
  }

  private static class WatchingInsertionContext extends InsertionContext {
    private RangeMarkerEx tailWatcher;
    String invalidateTrace;
    DocumentEvent killer;
    private RangeMarkerSpy spy;

    public WatchingInsertionContext(CompletionProgressIndicator indicator, char completionChar, List<LookupElement> items, Editor editor) {
      super(indicator.getOffsetMap(), completionChar, items.toArray(new LookupElement[items.size()]),
            indicator.getParameters().getOriginalFile(), editor,
            completionChar != Lookup.AUTO_INSERT_SELECT_CHAR && completionChar != Lookup.REPLACE_SELECT_CHAR &&
            completionChar != Lookup.NORMAL_SELECT_CHAR);
    }

    @Override
    public void setTailOffset(int offset) {
      super.setTailOffset(offset);
      watchTail(offset);
    }

    private void watchTail(int offset) {
      stopWatching();
      tailWatcher = (RangeMarkerEx)getDocument().createRangeMarker(offset, offset);
      if (!tailWatcher.isValid()) {
        throw new AssertionError(getDocument() + "; offset=" + offset);
      }
      tailWatcher.setGreedyToRight(true);
      spy = new RangeMarkerSpy(tailWatcher) {
        @Override
        protected void invalidated(DocumentEvent e) {
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            LOG.error("Tail offset invalidated, say thanks to the "+ e);
          }

          if (invalidateTrace == null) {
            invalidateTrace = DebugUtil.currentStackTrace();
            killer = e;
          }
        }
      };
      getDocument().addDocumentListener(spy);
    }

    void stopWatching() {
      if (tailWatcher != null) {
        getDocument().removeDocumentListener(spy);
        tailWatcher.dispose();
      }
    }

    @Override
    public int getTailOffset() {
      int offset = super.getTailOffset();
      if (tailWatcher.getStartOffset() != tailWatcher.getEndOffset() && offset > 0) {
        watchTail(offset);
      }

      return offset;
    }
  }
}

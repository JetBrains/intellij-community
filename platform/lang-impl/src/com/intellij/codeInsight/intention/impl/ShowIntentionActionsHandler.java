// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionSource;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewEditor;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewUnsupportedOperationException;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.IntentionFUSCollector;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.modcommand.ModCommandWithContext;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThreeState;
import com.intellij.util.TripleFunction;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.stubs.StubInconsistencyReporter.SourceOfCheck.DeliberateAdditionalCheckInIntentions;

public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance(ShowIntentionActionsHandler.class);

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    invoke(project, editor, psiFile, false);
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, boolean showFeedbackOnEmptyMenu) {
    long start = System.currentTimeMillis();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }

    LookupEx lookup = LookupManager.getActiveLookup(editor);
    if (lookup != null) {
      lookup.showElementActions(null);
      return;
    }

    if (!LightEdit.owns(project)) {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
      letAutoImportComplete(editor, file, codeAnalyzer);
    }

    IntentionsUI.getInstance(project).hide();

    if (HintManagerImpl.getInstanceImpl().performCurrentQuestionAction()) {
      return;
    }

    //intentions check isWritable before modification: if (!file.isWritable()) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) {
      CommandProcessor.getInstance().executeCommand(project, () -> state.gotoEnd(false),
                                                    LangBundle.message("command.name.finish.template"), null);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    showIntentionHint(project, editor, file, showFeedbackOnEmptyMenu);
    long elapsed = System.currentTimeMillis() - start;
    IntentionFUSCollector.reportPopupDelay(project, elapsed, file.getFileType());
  }

  protected void showIntentionHint(@NotNull Project project,
                                   @NotNull Editor editor,
                                   @NotNull PsiFile file,
                                   boolean showFeedbackOnEmptyMenu) {
    CachedIntentions cachedIntentions = calcCachedIntentions(project, editor, file);
    cachedIntentions.wrapAndUpdateGutters();
    if (cachedIntentions.getAllActions().isEmpty()) {
      showEmptyMenuFeedback(editor, showFeedbackOnEmptyMenu);
    }
    else {
      editor.getScrollingModel().runActionOnScrollingFinished(() -> {
        IntentionHintComponent.showIntentionHint(project, file, editor, true, cachedIntentions);
      });
    }
  }

  private static void showEmptyMenuFeedback(@NotNull Editor editor, boolean showFeedbackOnEmptyMenu) {
    if (showFeedbackOnEmptyMenu) {
      HintManager.getInstance()
        .showInformationHint(editor, LangBundle.message("hint.text.no.context.actions.available.at.this.location"));
    }
  }

  @ApiStatus.Internal
  public static @NotNull CachedIntentions calcCachedIntentions(@NotNull Project project,
                                                               @NotNull Editor editor,
                                                               @NotNull PsiFile file) {
    ThreadingAssertions.assertEventDispatchThread();
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IllegalStateException("must not wait for intentions inside write action");
    }
    String progressTitle = CodeInsightBundle.message("progress.title.searching.for.context.actions");
    DumbService dumbService = DumbService.getInstance(project);
    boolean useAlternativeResolve = dumbService.isAlternativeResolveEnabled();
    ThrowableComputable<CachedIntentions, RuntimeException> prioritizedRunnable =
      () -> ProgressManager.getInstance().computePrioritized(() -> {
        DaemonCodeAnalyzerImpl.waitForLazyQuickFixesUnderCaret(file, editor);
        return ReadAction.compute(() -> CachedIntentions.createAndUpdateActions(
          project, file, editor,
          ShowIntentionsPass.getActionsToShow(editor, file)));
      });
    ThrowableComputable<CachedIntentions, RuntimeException> process =
      useAlternativeResolve
      ? () -> dumbService.computeWithAlternativeResolveEnabled(prioritizedRunnable)
      : prioritizedRunnable;
    return WriteIntentReadAction.compute((Computable<CachedIntentions>)() ->
      ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project)
    );
  }

  private static void letAutoImportComplete(@NotNull Editor editor, @NotNull PsiFile file, DaemonCodeAnalyzerImpl codeAnalyzer) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> codeAnalyzer.autoImportReferenceAtCursor(editor, file));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  /**
   * @deprecated Use {@link #availableFor(PsiFile, Editor, int, IntentionAction)} instead.
   */
  @Deprecated(forRemoval = true, since = "2023.3")
  public static boolean availableFor(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull IntentionAction action) {
    return availableFor(psiFile, editor, editor.getCaretModel().getOffset(), action);
  }

  public static boolean availableFor(@NotNull PsiFile psiFile, @NotNull Editor editor, int offset, @NotNull IntentionAction action) {
    if (!psiFile.isValid() || editor.isViewer()) return false;

    try {
      Project project = psiFile.getProject();
      action = IntentionActionDelegate.unwrap(action);

      if (action instanceof SuppressIntentionActionFromFix) {
        ThreeState shouldBeAppliedToInjectionHost = ((SuppressIntentionActionFromFix)action).isShouldBeAppliedToInjectionHost();
        if (editor instanceof EditorWindow && shouldBeAppliedToInjectionHost == ThreeState.YES) {
          return false;
        }
        if (!(editor instanceof EditorWindow) && shouldBeAppliedToInjectionHost == ThreeState.NO) {
          return false;
        }
      }

      if (action instanceof PsiElementBaseIntentionAction psiAction) {
        if (!psiAction.checkFile(psiFile)) {
          return false;
        }
        PsiElement leaf = psiFile.findElementAt(offset);
        if (leaf == null || !psiAction.isAvailable(project, editor, leaf)) {
          return false;
        }
      }
      else {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          ModCommandAction modCommand = action.asModCommandAction();
          if (modCommand != null) {
            ActionContext actionContext = ActionContext.from(editor, psiFile);
            ThrowableComputable<Boolean, RuntimeException> computable =
              () -> ReadAction.nonBlocking(() -> modCommand.getPresentation(actionContext) != null)
                .expireWith(project)
                .executeSynchronously();
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(
              computable, LangBundle.message("command.check.availability.for", modCommand.getFamilyName()), true, project);
          }
        }
        return action.isAvailable(project, editor, psiFile);
      }
    }
    catch (IndexNotReadyException e) {
      return false;
    }
    catch (IntentionPreviewUnsupportedOperationException e) {
      // check action availability can be invoked on a mock editor and may produce exceptions
      return false;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      // avoid breaking highlighting when an exception is thrown from an intention
      LOG.error(e);
      return false;
    }
    return true;
  }

  public static @Nullable Pair<PsiFile, Editor> chooseBetweenHostAndInjected(
    @NotNull PsiFile hostFile,
    @NotNull Editor hostEditor,
    int hostOffset,
    @NotNull TripleFunction<? super @NotNull PsiFile, ? super @NotNull Editor, ? super @NotNull Integer, @NotNull Boolean> predicate
  ) {
    var injectedFile = InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, hostOffset);
    return chooseBetweenHostAndInjected(hostFile, hostEditor, hostOffset, injectedFile, predicate);
  }

  public static @Nullable Pair<PsiFile, Editor> chooseBetweenHostAndInjected(
    @NotNull PsiFile hostFile,
    @NotNull Editor hostEditor,
    int hostOffset,
    @Nullable PsiFile injectedFile,
    @NotNull TripleFunction<? super @NotNull PsiFile, ? super @NotNull Editor, ? super @NotNull Integer, @NotNull Boolean> predicate) {
    try {
      Editor editorToApply = null;
      PsiFile fileToApply = null;

      if (injectedFile != null && !(hostEditor instanceof IntentionPreviewEditor)) {
        Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, injectedFile);
        if (hostEditor != injectedEditor && injectedEditor instanceof EditorWindow editorWindow) {
          int injectedOffset = injectedEditor.logicalPositionToOffset(
            editorWindow.hostToInjected(hostEditor.offsetToLogicalPosition(hostOffset)));
          if (predicate.fun(injectedFile, injectedEditor, injectedOffset)) {
            editorToApply = injectedEditor;
            fileToApply = injectedFile;
          }
        }
      }

      if (editorToApply == null && predicate.fun(hostFile, hostEditor, hostOffset)) {
        editorToApply = hostEditor;
        fileToApply = hostFile;
      }
      if (editorToApply == null) return null;
      return Pair.create(fileToApply, editorToApply);
    }
    catch (IntentionPreviewUnsupportedOperationException e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw e;
      }
      return null;
    }
  }

  /**
   * Chooses a file and editor between host and injected files for the given intention action and invokes the action within a command.
   */
  public static boolean chooseActionAndInvoke(@NotNull PsiFile hostFile,
                                              @Nullable Editor hostEditor,
                                              @NotNull IntentionAction action,
                                              @NotNull @NlsContexts.Command String commandName) {
    return chooseActionAndInvoke(hostFile, hostEditor, action, commandName, -1, IntentionSource.OTHER);
  }

  /**
   * Chooses a file and editor between host and injected files for the given intention action and invokes the action within a command.
   */
  public static boolean chooseActionAndInvoke(@NotNull PsiFile hostFile,
                                              @Nullable Editor hostEditor,
                                              @NotNull IntentionAction action,
                                              @NotNull @NlsContexts.Command String commandName,
                                              @NotNull IntentionSource source) {
    return chooseActionAndInvoke(hostFile, hostEditor, action, commandName, -1, source);
  }

  /**
   * Chooses a file and editor between host and injected files for the given intention action and invokes the action within a command.
   */
  public static boolean chooseActionAndInvoke(@NotNull PsiFile hostFile,
                                              @Nullable Editor hostEditor,
                                              @NotNull IntentionAction action,
                                              @NotNull @NlsContexts.Command String commandName,
                                              int fixOffset) {
    return chooseActionAndInvoke(hostFile, hostEditor, action, commandName, fixOffset, IntentionSource.OTHER);
  }

  /**
   * Chooses a file and editor between host and injected files for the given intention action and invokes the action within a command.
   */
  public static boolean chooseActionAndInvoke(@NotNull PsiFile hostFile,
                                              @Nullable Editor hostEditor,
                                              @NotNull IntentionAction action,
                                              @NotNull @NlsContexts.Command String commandName,
                                              int fixOffset,
                                              @NotNull IntentionSource source) {
    Project project = hostFile.getProject();
    ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFixesStats().registerInvocation();

    try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      ModCommandAction commandAction = action.asModCommandAction();
      if (commandAction != null) {
        invokeCommandAction(hostFile, hostEditor, commandName, commandAction, fixOffset, source);
      }
      else {
        Pair<PsiFile, Editor> pair = chooseFileForAction(hostFile, hostEditor, action);
        if (pair == null) return false;
        CommandProcessor.getInstance().executeCommand(project, () ->
          invokeIntention(action, pair.second, pair.first, fixOffset, source), commandName, null);
        checkPsiTextConsistency(hostFile);
      }
    }
    return true;
  }

  private static void invokeCommandAction(@NotNull PsiFile hostFile,
                                          @Nullable Editor hostEditor,
                                          @NotNull @NlsContexts.Command String commandName,
                                          @NotNull ModCommandAction commandAction, int fixOffset,
                                          @NotNull IntentionSource source) {
    ModCommandWithContext
      contextAndCommand = ModCommandService.getInstance().chooseFileAndPerform(hostFile, hostEditor, commandAction, fixOffset);
    if (contextAndCommand == null) return;

    ActionContext context = contextAndCommand.context();
    Project project = context.project();
    IntentionFUSCollector.record(project, commandAction, context.file().getLanguage(), hostEditor, fixOffset, source);
    CommandProcessor.getInstance().executeCommand(project, () -> contextAndCommand.executeInteractively(hostEditor), commandName, null);
  }

  private static void checkPsiTextConsistency(@NotNull PsiFile hostFile) {
    if (Registry.is("ide.check.stub.text.consistency") ||
        ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest()) {
      if (hostFile.isValid()) {
        StubTextInconsistencyException.checkStubTextConsistency(hostFile, DeliberateAdditionalCheckInIntentions);
      }
    }
  }

  private static void invokeIntention(@NotNull IntentionAction action,
                                      @Nullable Editor editor,
                                      @NotNull PsiFile file,
                                      int fixOffset,
                                      @NotNull IntentionSource source) {
    IntentionFUSCollector.record(file.getProject(), action, file.getLanguage(), editor, fixOffset, source);
    PsiElement elementToMakeWritable = action.getElementToMakeWritable(file);
    if (elementToMakeWritable != null && !FileModificationService.getInstance().preparePsiElementsForWrite(elementToMakeWritable)) {
      return;
    }
    SmartPsiFileRange originalOffset = null;
    if (editor != null && fixOffset >= 0) {
      originalOffset = SmartPointerManager.getInstance(file.getProject())
        .createSmartPsiFileRangePointer(file, TextRange.from(editor.getCaretModel().getOffset(), 0));
      editor.getCaretModel().moveToOffset(fixOffset);
    }
    try {
      if (action.startInWriteAction()) {
        WriteAction.run(() -> action.invoke(file.getProject(), editor, file));
      }
      else {
        action.invoke(file.getProject(), editor, file);
      }
    }
    finally {
      if (originalOffset != null && originalOffset.getRange() != null && editor.getCaretModel().getOffset() == fixOffset &&
          TemplateManager.getInstance(file.getProject()).getActiveTemplate(editor) == null) {
        editor.getCaretModel().moveToOffset(originalOffset.getRange().getStartOffset());
      }
    }
  }

  public static @Nullable Pair<PsiFile, Editor> chooseFileForAction(@NotNull PsiFile hostFile,
                                                                    @Nullable Editor hostEditor,
                                                                    @NotNull IntentionAction action) {
    if (hostEditor == null) {
      return Pair.create(hostFile, null);
    }

    return chooseBetweenHostAndInjected(
      hostFile, hostEditor, hostEditor.getCaretModel().getOffset(),
      (psiFile, editor, offset) -> availableFor(psiFile, editor, offset, action)
    );
  }
}

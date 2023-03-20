// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewEditor;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewUnsupportedOperationException;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.IntentionsCollector;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.util.PairProcessor;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance(ShowIntentionActionsHandler.class);

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    invoke(project, editor, file, false);
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, boolean showFeedbackOnEmptyMenu) {
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

    if (HintManagerImpl.getInstanceImpl().performCurrentQuestionAction()) return;

    //intentions check isWritable before modification: if (!file.isWritable()) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) {
      CommandProcessor.getInstance().executeCommand(project, () -> state.gotoEnd(false),
                                                    LangBundle.message("command.name.finish.template"), null);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    showIntentionHint(project, editor, file, calcIntentions(project, editor, file), showFeedbackOnEmptyMenu);
  }

  protected void showIntentionHint(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull ShowIntentionsPass.IntentionsInfo intentions, boolean showFeedbackOnEmptyMenu) {
    if (!intentions.isEmpty()) {
      editor.getScrollingModel().runActionOnScrollingFinished(() -> {
          CachedIntentions cachedIntentions = CachedIntentions.createAndUpdateActions(project, file, editor, intentions);
          cachedIntentions.wrapAndUpdateGutters();
          if (cachedIntentions.getAllActions().isEmpty()) {
            showEmptyMenuFeedback(editor, showFeedbackOnEmptyMenu);
          }
          else {
            IntentionHintComponent.showIntentionHint(project, file, editor, true, cachedIntentions);
          }
      });
    }
    else {
      showEmptyMenuFeedback(editor, showFeedbackOnEmptyMenu);
    }
  }

  private static void showEmptyMenuFeedback(@NotNull Editor editor, boolean showFeedbackOnEmptyMenu) {
    if (showFeedbackOnEmptyMenu) {
      HintManager.getInstance()
        .showInformationHint(editor, LangBundle.message("hint.text.no.context.actions.available.at.this.location"));
    }
  }

  @ApiStatus.Internal
  public static @NotNull ShowIntentionsPass.IntentionsInfo calcIntentions(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) throw new IllegalStateException("must not wait for intentions inside write action");
    String progressTitle = CodeInsightBundle.message("progress.title.searching.for.context.actions");
    DumbService dumbService = DumbService.getInstance(project);
    boolean useAlternativeResolve = dumbService.isAlternativeResolveEnabled();
    ThrowableComputable<ShowIntentionsPass.IntentionsInfo, RuntimeException> prioritizedRunnable = () -> ProgressManager.getInstance().computePrioritized(() ->  {
      DaemonCodeAnalyzerImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(file, editor);
      return ReadAction.compute(() -> ShowIntentionsPass.getActionsToShow(editor, file, false));
    });
    ThrowableComputable<ShowIntentionsPass.IntentionsInfo, RuntimeException> process = useAlternativeResolve ?
      () -> dumbService.computeWithAlternativeResolveEnabled(prioritizedRunnable) : prioritizedRunnable;
    ShowIntentionsPass.IntentionsInfo intentions =
      ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project);

    ShowIntentionsPass.getActionsToShowSync(editor, file, intentions);
    return intentions;
  }

  private static void letAutoImportComplete(@NotNull Editor editor, @NotNull PsiFile file, DaemonCodeAnalyzerImpl codeAnalyzer) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> codeAnalyzer.autoImportReferenceAtCursor(editor, file));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static boolean availableFor(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull IntentionAction action) {
    if (!psiFile.isValid()) return false;

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
        PsiElement leaf = psiFile.findElementAt(editor.getCaretModel().getOffset());
        if (leaf == null || !psiAction.isAvailable(project, editor, leaf)) {
          return false;
        }
      }
      else if (!action.isAvailable(project, editor, psiFile)) {
        return false;
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

  public static @Nullable Pair<PsiFile, Editor> chooseBetweenHostAndInjected(@NotNull PsiFile hostFile,
                                                                             @NotNull Editor hostEditor,
                                                                             @Nullable PsiFile injectedFile,
                                                                             @NotNull PairProcessor<? super PsiFile, ? super Editor> predicate) {
    try {
      Editor editorToApply = null;
      PsiFile fileToApply = null;

      Editor injectedEditor = null;
      // TODO: support intention preview in injections
      if (injectedFile != null && !(hostEditor instanceof IntentionPreviewEditor)) {
        injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, injectedFile);
        if (predicate.process(injectedFile, injectedEditor)) {
          editorToApply = injectedEditor;
          fileToApply = injectedFile;
        }
      }

      if (editorToApply == null && hostEditor != injectedEditor && predicate.process(hostFile, hostEditor)) {
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

  public static boolean chooseActionAndInvoke(@NotNull PsiFile hostFile,
                                              @Nullable Editor hostEditor,
                                              @NotNull IntentionAction action,
                                              @NotNull @NlsContexts.Command String commandName) {
    Project project = hostFile.getProject();
    ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFixesStats().registerInvocation();

    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      Pair<PsiFile, Editor> pair = chooseFileForAction(hostFile, hostEditor, action);
      if (pair == null) return false;
      CommandProcessor.getInstance().executeCommand(project, () ->
        invokeIntention(action, pair.second, pair.first), commandName, null);
      checkPsiTextConsistency(hostFile);
    }
    return true;
  }

  private static void checkPsiTextConsistency(@NotNull PsiFile hostFile) {
    if (Registry.is("ide.check.stub.text.consistency") ||
        ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest()) {
      if (hostFile.isValid()) {
        StubTextInconsistencyException.checkStubTextConsistency(hostFile);
      }
    }
  }

  private static void invokeIntention(@NotNull IntentionAction action, @Nullable Editor editor, @NotNull PsiFile file) {
    IntentionsCollector.record(file.getProject(), action, file.getLanguage());
    PsiElement elementToMakeWritable = action.getElementToMakeWritable(file);
    if (elementToMakeWritable != null && !FileModificationService.getInstance().preparePsiElementsForWrite(elementToMakeWritable)) {
      return;
    }

    if (action.startInWriteAction()) {
      WriteAction.run(() -> action.invoke(file.getProject(), editor, file));
    }
    else {
      action.invoke(file.getProject(), editor, file);
    }
  }


  public static @Nullable Pair<PsiFile, Editor> chooseFileForAction(@NotNull PsiFile hostFile,
                                                                    @Nullable Editor hostEditor,
                                                                    @NotNull IntentionAction action) {
    if (hostEditor == null) {
      return Pair.create(hostFile, null);
    }

    PsiFile injectedFile = InjectedLanguageUtil.findInjectedPsiNoCommit(hostFile, hostEditor.getCaretModel().getOffset());
    return chooseBetweenHostAndInjected(
      hostFile, hostEditor, injectedFile,
      (psiFile, editor) -> availableFor(psiFile, editor, action)
    );
  }
}

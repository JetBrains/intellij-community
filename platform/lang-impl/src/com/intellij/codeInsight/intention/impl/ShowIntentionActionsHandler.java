// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.IntentionsCollector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.util.PairProcessor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class ShowIntentionActionsHandler implements CodeInsightActionHandler {

  @Override
  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }

    final LookupEx lookup = LookupManager.getActiveLookup(editor);
    if (lookup != null) {
      lookup.showElementActions();
      return;
    }

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    letAutoImportComplete(editor, file, codeAnalyzer);

    ShowIntentionsPass.IntentionsInfo intentions = ShowIntentionsPass.getActionsToShow(editor, file);
    IntentionsUI.getInstance(project).hide();

    if (HintManagerImpl.getInstanceImpl().performCurrentQuestionAction()) return;

    //intentions check isWritable before modification: if (!file.isWritable()) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) {
      return;
    }

    showIntentionHint(project, editor, file, intentions);
  }

  // added for override into Rider
  @SuppressWarnings("WeakerAccess")
  protected void showIntentionHint(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    if (!intentions.isEmpty()) {
      CachedIntentions cachedIntentions = CachedIntentions.createAndUpdateActions(project, file, editor, intentions);
      IntentionHintComponent.showIntentionHint(project, file, editor, true, cachedIntentions);
    }
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

    int offset = editor.getCaretModel().getOffset();
    PsiElement psiElement = psiFile.findElementAt(offset);
    boolean inProject = psiFile.getManager().isInProject(psiFile);
    try {
      Project project = psiFile.getProject();
      if (action instanceof IntentionActionDelegate) action = ((IntentionActionDelegate)action).getDelegate();
      if (action instanceof IntentionActionDelegate) action = ((IntentionActionDelegate)action).getDelegate();
      if (action instanceof SuppressIntentionActionFromFix) {
        final ThreeState shouldBeAppliedToInjectionHost = ((SuppressIntentionActionFromFix)action).isShouldBeAppliedToInjectionHost();
        if (editor instanceof EditorWindow && shouldBeAppliedToInjectionHost == ThreeState.YES) {
          return false;
        }
        if (!(editor instanceof EditorWindow) && shouldBeAppliedToInjectionHost == ThreeState.NO) {
          return false;
        }
      }
      
      if (action instanceof PsiElementBaseIntentionAction) {
        if (!inProject || psiElement == null || !((PsiElementBaseIntentionAction)action).isAvailable(project, editor, psiElement)) return false;
      }
      else if (!action.isAvailable(project, editor, psiFile)) {
        return false;
      }
    }
    catch (IndexNotReadyException e) {
      return false;
    }
    return true;
  }

  @Nullable
  public static Pair<PsiFile,Editor> chooseBetweenHostAndInjected(@NotNull PsiFile hostFile, @NotNull Editor hostEditor, @NotNull PairProcessor<PsiFile, Editor> predicate) {
    Editor editorToApply = null;
    PsiFile fileToApply = null;

    int offset = hostEditor.getCaretModel().getOffset();
    PsiFile injectedFile = InjectedLanguageUtil.findInjectedPsiNoCommit(hostFile, offset);
    if (injectedFile != null) {
      Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, injectedFile);
      if (predicate.process(injectedFile, injectedEditor)) {
        editorToApply = injectedEditor;
        fileToApply = injectedFile;
      }
    }

    if (editorToApply == null && predicate.process(hostFile, hostEditor)) {
      editorToApply = hostEditor;
      fileToApply = hostFile;
    }
    if (editorToApply == null) return null;
    return Pair.create(fileToApply, editorToApply);
  }

  public static boolean chooseActionAndInvoke(@NotNull PsiFile hostFile,
                                              @NotNull final Editor hostEditor,
                                              @NotNull final IntentionAction action,
                                              @NotNull String text) {
    final Project project = hostFile.getProject();
    return chooseActionAndInvoke(hostFile, hostEditor, action, text, project);
  }

  static boolean chooseActionAndInvoke(@NotNull PsiFile hostFile,
                                       @Nullable final Editor hostEditor,
                                       @NotNull final IntentionAction action,
                                       @NotNull String text,
                                       @NotNull final Project project) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.quickFix");
    ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFixesStats().registerInvocation();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    Pair<PsiFile, Editor> pair = chooseFileForAction(hostFile, hostEditor, action);
    if (pair == null) return false;

    CommandProcessor.getInstance().executeCommand(project, () ->
      TransactionGuard.getInstance().submitTransactionAndWait(
        () -> invokeIntention(action, pair.second, pair.first)), text, null);

    checkPsiTextConsistency(hostFile);

    return true;
  }

  private static void checkPsiTextConsistency(@NotNull PsiFile hostFile) {
    if (Registry.is("ide.check.stub.text.consistency") ||
        ApplicationManager.getApplication().isUnitTestMode() && !ApplicationInfoImpl.isInStressTest()) {
      if (hostFile.isValid()) {
        StubTextInconsistencyException.checkStubTextConsistency(hostFile);
      }
    }
  }

  private static void invokeIntention(@NotNull IntentionAction action, @Nullable Editor editor, @NotNull PsiFile file) {
    IntentionsCollector.getInstance().record(action, file.getLanguage());
    PsiElement elementToMakeWritable = action.getElementToMakeWritable(file);
    if (elementToMakeWritable != null && !FileModificationService.getInstance().preparePsiElementsForWrite(elementToMakeWritable)) {
      return;
    }

    Runnable r = () -> action.invoke(file.getProject(), editor, file);
    if (action.startInWriteAction()) {
      WriteAction.run(r::run);
    } else {
      r.run();
    }
  }


  static Pair<PsiFile, Editor> chooseFileForAction(@NotNull PsiFile hostFile,
                                                   @Nullable Editor hostEditor,
                                                   @NotNull IntentionAction action) {
    return hostEditor == null ? Pair.create(hostFile, null) :
           chooseBetweenHostAndInjected(hostFile, hostEditor, (psiFile, editor) -> availableFor(psiFile, editor, action));
  }
}

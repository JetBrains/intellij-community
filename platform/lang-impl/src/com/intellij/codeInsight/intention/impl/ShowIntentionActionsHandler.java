/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairProcessor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler");

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
    codeAnalyzer.autoImportReferenceAtCursor(editor, file); //let autoimport complete

    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    ShowIntentionsPass.getActionsToShow(editor, file, intentions, -1);
    IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();
    if (hintComponent != null) {
      IntentionHintComponent.PopupUpdateResult result = hintComponent.isForEditor(editor)
                                                        ? hintComponent.updateActions(intentions)
                                                        : IntentionHintComponent.PopupUpdateResult.HIDE_AND_RECREATE;
      if (result == IntentionHintComponent.PopupUpdateResult.HIDE_AND_RECREATE) {
        hintComponent.hide();
      }
    }

    if (HintManagerImpl.getInstanceImpl().performCurrentQuestionAction()) return;

    //intentions check isWritable before modification: if (!file.isWritable()) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) {
      return;
    }

    if (!intentions.isEmpty()) {
      IntentionHintComponent.showIntentionHint(project, file, editor, intentions, true);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  // returns editor,file where the action is available or null if there are none
  public static boolean availableFor(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull IntentionAction action) {
    if (!psiFile.isValid()) return false;

    int offset = editor.getCaretModel().getOffset();
    PsiElement psiElement = psiFile.findElementAt(offset);
    boolean inProject = psiFile.getManager().isInProject(psiFile);
    try {
      Project project = psiFile.getProject();
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

    final Pair<PsiFile, Editor> pair = hostEditor != null ? chooseBetweenHostAndInjected(hostFile, hostEditor, new PairProcessor<PsiFile, Editor>() {
      @Override
      public boolean process(PsiFile psiFile, Editor editor) {
        return availableFor(psiFile, editor, action);
      }
    }) : Pair.<PsiFile, Editor>create(hostFile, null);
    if (pair == null) return false;

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          action.invoke(project, pair.second, pair.first);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        if (hostEditor != null) {
          DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(hostEditor);
        }
      }
    };

    if (action.startInWriteAction()) {
      final Runnable _runnable = runnable;
      runnable = new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(_runnable);
        }
      };
    }

    CommandProcessor.getInstance().executeCommand(project, runnable, text, null);
    return true;
  }
}

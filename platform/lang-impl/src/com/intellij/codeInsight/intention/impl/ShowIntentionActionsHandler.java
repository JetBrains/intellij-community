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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler");

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (HintManagerImpl.getInstanceImpl().performCurrentQuestionAction()) return;

    //intentions check isWritable before modification: if (!file.isWritable()) return;
    if (file instanceof PsiCodeFragment) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) {
      return;
    }

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    codeAnalyzer.autoImportReferenceAtCursor(editor, file); //let autoimport complete

    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    ShowIntentionsPass.getActionsToShow(editor, file, intentions, -1);
    
    if (!intentions.isEmpty()) {
      IntentionHintComponent.showIntentionHint(project, file, editor, intentions, true);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  // returns editor,file where the action is available or null if there are none
  public static boolean availableFor(@NotNull PsiFile file, @NotNull Editor editor, @NotNull IntentionAction action) {
    if (!file.isValid()) return false;

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    boolean inProject = file.getManager().isInProject(file);
    return isAvailableHere(editor, file, element, inProject, action);
  }
  
  private static boolean isAvailableHere(Editor editor, PsiFile psiFile, PsiElement psiElement, boolean inProject, IntentionAction action) {
    try {
      Project project = psiFile.getProject();
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
  public static Pair<PsiFile,Editor> chooseBetweenHostAndInjected(PsiFile hostFile, Editor hostEditor, PairProcessor<PsiFile, Editor> predicate) {
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
  
  public static boolean chooseActionAndInvoke(PsiFile hostFile, final Editor hostEditor, final IntentionAction action, final String text) {
    final Project project = hostFile.getProject();

    Pair<PsiFile, Editor> pair = chooseBetweenHostAndInjected(hostFile, hostEditor, new PairProcessor<PsiFile, Editor>() {
      public boolean process(PsiFile psiFile, Editor editor) {
        return availableFor(psiFile, editor, action);
      }
    });
    if (pair == null) return false;
    final Editor editorToApply = pair.second;
    final PsiFile fileToApply = pair.first;

    Runnable runnable = new Runnable() {
      public void run() {
        try {
          action.invoke(project, editorToApply, fileToApply);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(hostEditor);
      }
    };

    if (action.startInWriteAction()) {
      final Runnable _runnable = runnable;
      runnable = new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(_runnable);
        }
      };
    }

    CommandProcessor.getInstance().executeCommand(project, runnable, text, null);
    return true;
  }
}

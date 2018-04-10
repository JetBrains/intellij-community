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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler");
  public static volatile boolean ourTestingAutopopup = false;

  @NotNull
  @Override
  public Result checkAutoPopup(char charTyped, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);

    if (LOG.isDebugEnabled()) {
      LOG.debug("checkAutoPopup: character=" + charTyped + ";");
      LOG.debug("phase=" + CompletionServiceImpl.getCompletionPhase());
      LOG.debug("lookup=" + lookup);
      LOG.debug("currentCompletion=" + CompletionServiceImpl.getCompletionService().getCurrentCompletion());
    }

    if (lookup != null) {
      if (editor.getSelectionModel().hasSelection()) {
        lookup.performGuardedChange(() -> EditorModificationUtil.deleteSelectedText(editor));
      }
      return Result.STOP;
    }

    if (Character.isLetterOrDigit(charTyped) || charTyped == '_') {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      return Result.STOP;
    }

    return Result.CONTINUE;
  }

  public static void invokeCompletion(@NotNull CompletionType completionType,
                                      boolean autopopup,
                                      Project project, Editor editor, int time, boolean restart) {
    if (editor.isDisposed()) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      return;
    }

    // retrieve the injected file from scratch since our typing might have destroyed the old one completely
    Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(topLevelEditor.getDocument());
    if (file == null) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      return;
    }

    PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    if (!PsiDocumentManager.getInstance(project).isCommitted(editor.getDocument())) {
      LOG.error("Non-committed document");
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
    Editor newEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(topLevelEditor, topLevelFile);
    try {
      CodeCompletionHandlerBase.createHandler(completionType, false, autopopup, false).invokeCompletion(project, newEditor, time, false, restart);
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  /**
   * @deprecated
   * @see AutoPopupController#runTransactionWithEverythingCommitted(Project, Runnable)
   */
  @Deprecated
  public static void runLaterWithCommitted(@NotNull final Project project, final Document document, @NotNull final Runnable runnable) {
    AutoPopupController.runTransactionWithEverythingCommitted(project, runnable);
  }
}

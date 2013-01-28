/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.editorActions.emacs.EmacsProcessingHandler;
import com.intellij.codeInsight.editorActions.emacs.LanguageEmacsExtension;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class EmacsStyleIndentAction extends BaseCodeInsightAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.actions.EmacsStyleIndentAction");

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  @Override
  protected boolean isValidForFile(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    final PsiElement context = file.findElementAt(editor.getCaretModel().getOffset());
    return context != null && LanguageFormatting.INSTANCE.forContext(context) != null;
  }

  //----------------------------------------------------------------------
  private static class Handler implements CodeInsightActionHandler {

    @Override
    public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
      if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
        return;
      }

      EmacsProcessingHandler emacsProcessingHandler = LanguageEmacsExtension.INSTANCE.forLanguage(file.getLanguage());
      if (emacsProcessingHandler != null) {
        EmacsProcessingHandler.Result result = emacsProcessingHandler.changeIndent(project, editor, file);
        if (result == EmacsProcessingHandler.Result.STOP) {
          return;
        }
      }

      final Document document = editor.getDocument();
      final int startOffset = editor.getCaretModel().getOffset();
      final int line = editor.offsetToLogicalPosition(startOffset).line;
      final int col = editor.getCaretModel().getLogicalPosition().column;
      final int lineStart = document.getLineStartOffset(line);
      final int initLineEnd = document.getLineEndOffset(line);
      try{
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        final int newPos = codeStyleManager.adjustLineIndent(file, lineStart);
        final int newCol = newPos - lineStart;
        final int lineInc = document.getLineEndOffset(line) - initLineEnd;
        if (newCol >= col + lineInc && newCol >= 0) {
          final LogicalPosition pos = new LogicalPosition(line, newCol);
          editor.getCaretModel().moveToLogicalPosition(pos);
          editor.getSelectionModel().removeSelection();
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}

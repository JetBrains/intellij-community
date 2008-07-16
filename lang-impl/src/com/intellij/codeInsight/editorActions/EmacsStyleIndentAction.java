package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class EmacsStyleIndentAction extends BaseCodeInsightAction{

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.actions.EmacsStyleIndentAction");

  protected CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  protected boolean isValidForFile(final Project project, final Editor editor, final PsiFile file) {
    final PsiElement context = file.findElementAt(editor.getCaretModel().getOffset());
    return context != null && LanguageFormatting.INSTANCE.forContext(context) != null;
  }

  //----------------------------------------------------------------------
  private static class Handler implements CodeInsightActionHandler {

    public void invoke(final Project project, final Editor editor, final PsiFile file) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      if (!file.isWritable()){
        if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
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
        if (newCol >= col + lineInc) {
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

    public boolean startInWriteAction() {
      return true;
    }
  }
}
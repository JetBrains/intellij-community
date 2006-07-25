
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class AutoIndentLinesHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.AutoIndentLinesHandler");

  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!file.isWritable()){
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
        return;
      }
    }

    Document document = editor.getDocument();
    int startOffset, endOffset;
    RangeMarker selectionEndMarker = null;
    if (editor.getSelectionModel().hasSelection()){
      startOffset = editor.getSelectionModel().getSelectionStart();
      endOffset = editor.getSelectionModel().getSelectionEnd();
      selectionEndMarker = document.createRangeMarker(endOffset, endOffset);
      endOffset -= 1;
    }
    else{
      startOffset = endOffset = editor.getCaretModel().getOffset();
    }
    int line1 = editor.offsetToLogicalPosition(startOffset).line;
    int col = editor.getCaretModel().getLogicalPosition().column;

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    try{
      if (startOffset == endOffset) {
        int lineStart = document.getLineStartOffset(line1);
        if (codeStyleManager.isLineToBeIndented(file, lineStart)) {
          codeStyleManager.adjustLineIndent(file, lineStart);
        }
      } else {
        codeStyleManager.adjustLineIndent(file, new TextRange(startOffset, endOffset));
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }

    if (selectionEndMarker == null){
      if (line1 < document.getLineCount() - 1){
        if (document.getLineStartOffset(line1 + 1) + col >= document.getTextLength()) {
          col = document.getLineEndOffset(line1 + 1) - document.getLineStartOffset(line1 + 1);
        }
        LogicalPosition pos = new LogicalPosition(line1 + 1, col);
        editor.getCaretModel().moveToLogicalPosition(pos);
        editor.getSelectionModel().removeSelection();
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
    else{
      if (!selectionEndMarker.isValid()) return;
      endOffset = selectionEndMarker.getEndOffset();
      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
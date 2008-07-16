package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 6:30:10 PM
 * To change this template use Options | File Templates.
 */
public class BlockBraceFixer implements Fixer{
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiCodeBlock && afterUnmatchedBrace(editor,psiElement.getContainingFile().getFileType())) {
      PsiCodeBlock block = (PsiCodeBlock) psiElement;
      int stopOffset = block.getTextRange().getEndOffset();
      final PsiStatement[] statements = block.getStatements();
      if (statements.length > 0) {
        stopOffset = statements[0].getTextRange().getEndOffset();
      }
      editor.getDocument().insertString(stopOffset, "}");
    }
  }

  private boolean afterUnmatchedBrace(Editor editor, FileType fileType) {
    return EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, editor.getCaretModel().getOffset(), fileType);
  }
}

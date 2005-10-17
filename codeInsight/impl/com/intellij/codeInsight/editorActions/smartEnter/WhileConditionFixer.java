package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 5:32:01 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class WhileConditionFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiWhileStatement) {
      final Document doc = editor.getDocument();
      final PsiWhileStatement whileStatement = (PsiWhileStatement) psiElement;
      if (whileStatement.getCondition() == null) {
        if (whileStatement.getLParenth() == null || whileStatement.getRParenth() == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(whileStatement.getTextRange().getStartOffset()));
          final PsiStatement block = whileStatement.getBody();
          if (block != null) {
            stopOffset = Math.min(stopOffset, block.getTextRange().getStartOffset());
          }
          doc.replaceString(whileStatement.getTextRange().getStartOffset(), stopOffset, "while ()");
        } else {
          processor.registerUnresolvedError(whileStatement.getLParenth().getTextRange().getEndOffset());
        }
      } else if (whileStatement.getRParenth() == null) {
        doc.insertString(whileStatement.getCondition().getTextRange().getEndOffset(), ")");
      }
    }
  }
}

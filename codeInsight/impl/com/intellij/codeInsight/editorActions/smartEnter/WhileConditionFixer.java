package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
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
      final PsiJavaToken rParenth = whileStatement.getRParenth();
      final PsiJavaToken lParenth = whileStatement.getLParenth();
      final PsiExpression condition = whileStatement.getCondition();

      if (condition == null) {
        if (lParenth == null || rParenth == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(whileStatement.getTextRange().getStartOffset()));
          final PsiStatement block = whileStatement.getBody();
          if (block != null) {
            stopOffset = Math.min(stopOffset, block.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, whileStatement.getTextRange().getEndOffset());

          doc.replaceString(whileStatement.getTextRange().getStartOffset(), stopOffset, "while ()");
          processor.registerUnresolvedError(whileStatement.getTextRange().getStartOffset() + "while (".length());
        } else {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
      } else if (rParenth == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
  }
}

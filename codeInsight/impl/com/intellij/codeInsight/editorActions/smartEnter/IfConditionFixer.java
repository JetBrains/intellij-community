package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class IfConditionFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiIfStatement) {
      final Document doc = editor.getDocument();
      final PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
      final PsiJavaToken rParen = ifStatement.getRParenth();
      final PsiJavaToken lParen = ifStatement.getLParenth();
      final PsiExpression condition = ifStatement.getCondition();

      if (condition == null) {
        if (lParen == null || rParen == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(ifStatement.getTextRange().getStartOffset()));
          final PsiStatement then = ifStatement.getThenBranch();
          if (then != null) {
            stopOffset = Math.min(stopOffset, then.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, ifStatement.getTextRange().getEndOffset());

          doc.replaceString(ifStatement.getTextRange().getStartOffset(), stopOffset, "if ()");
          
          processor.registerUnresolvedError(ifStatement.getTextRange().getStartOffset() + "if (".length());
        } else {
          processor.registerUnresolvedError(lParen.getTextRange().getEndOffset());
        }
      } else if (rParen == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
  }
}

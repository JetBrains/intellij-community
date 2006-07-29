package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 5:32:01 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class IfConditionFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiIfStatement) {
      final Document doc = editor.getDocument();
      final PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
      if (ifStatement.getCondition() == null) {
        if (ifStatement.getLParenth() == null || ifStatement.getRParenth() == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(ifStatement.getTextRange().getStartOffset()));
          final PsiStatement then = ifStatement.getThenBranch();
          if (then != null) {
            stopOffset = Math.min(stopOffset, then.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, ifStatement.getTextRange().getEndOffset());

          doc.replaceString(ifStatement.getTextRange().getStartOffset(), stopOffset, "if ()");
        } else {
          processor.registerUnresolvedError(ifStatement.getLParenth().getTextRange().getEndOffset());
        }
      } else if (ifStatement.getRParenth() == null) {
        doc.insertString(ifStatement.getCondition().getTextRange().getEndOffset(), ")");
      }
    }
  }
}

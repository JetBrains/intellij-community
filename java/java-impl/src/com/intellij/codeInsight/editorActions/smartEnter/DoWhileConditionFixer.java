package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 5:32:01 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DoWhileConditionFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiDoWhileStatement) {
      final Document doc = editor.getDocument();
      final PsiDoWhileStatement stmt = (PsiDoWhileStatement) psiElement;
      if (stmt.getBody() == null || !(stmt.getBody() instanceof PsiBlockStatement) && stmt.getWhileKeyword() == null) {
        final int startOffset = stmt.getTextRange().getStartOffset();
        doc.replaceString(startOffset, startOffset + "do".length(), "do {} while()");
        return;
      }

      if (stmt.getCondition() == null) {
        if (stmt.getWhileKeyword() == null) {
          final int endOffset = stmt.getTextRange().getEndOffset();
          doc.insertString(endOffset, "while()");
        } else if (stmt.getLParenth() == null || stmt.getRParenth() == null) {
          final TextRange whileRange = stmt.getWhileKeyword().getTextRange();
          doc.replaceString(whileRange.getStartOffset(), whileRange.getEndOffset(), "while()");
        } else {
          processor.registerUnresolvedError(stmt.getLParenth().getTextRange().getEndOffset());
        }
      }
    }
  }
}

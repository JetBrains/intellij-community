package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspMethodCall;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:35:49 PM
 * To change this template use Options | File Templates.
 */
public class MethodCallFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    PsiExpressionList args = null;
    if (psiElement instanceof PsiMethodCallExpression && !(psiElement instanceof JspMethodCall)) {
      args = ((PsiMethodCallExpression) psiElement).getArgumentList();
    } else if (psiElement instanceof PsiNewExpression) {
      args = ((PsiNewExpression) psiElement).getArgumentList();
    }

    if (args == null) return;

    PsiElement parenth = args.getLastChild();

    if (parenth == null || !")".equals(parenth.getText())) {
      int endOffset = -1;
      PsiElement child = args.getFirstChild();
      while (child != null) {
        if (child instanceof PsiErrorElement) {
          final PsiErrorElement errorElement = (PsiErrorElement)child;
          if (errorElement.getErrorDescription().indexOf("')'") >= 0) {
            endOffset = errorElement.getTextRange().getStartOffset();
            break;
          }
        }
        child = child.getNextSibling();
      }

      if (endOffset == -1) {
        endOffset = args.getTextRange().getEndOffset();
      }

      final PsiExpression[] params = args.getExpressions();
      if (params.length > 0 && startLine(editor, args) != startLine(editor, params[0])) {
        endOffset = args.getTextRange().getStartOffset() + 1;
      }

      endOffset = CharArrayUtil.shiftBackward(editor.getDocument().getCharsSequence(), endOffset - 1, " \t\n") + 1;
      editor.getDocument().insertString(endOffset, ")");
    }
  }

  private int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}

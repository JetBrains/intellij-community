package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 4:20:36 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class MissingReturnExpressionFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement)
      throws IncorrectOperationException {
    if (psiElement instanceof PsiReturnStatement) {
      PsiReturnStatement retStatement = (PsiReturnStatement) psiElement;
      if (retStatement.getReturnValue() != null &&
          startLine(editor, retStatement) == startLine(editor, retStatement.getReturnValue())) {
        return;
      }

      PsiElement parent = PsiTreeUtil.getParentOfType(psiElement, PsiClassInitializer.class, PsiMethod.class);
      if (parent instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) parent;
        final PsiType returnType = method.getReturnType();
        if (returnType != null && returnType != PsiType.VOID) {
          final int startOffset = retStatement.getTextRange().getStartOffset();
          if (retStatement.getReturnValue() != null) {
            editor.getDocument().insertString(startOffset + "return".length(), ";");
          }

          processor.registerUnresolvedError(startOffset + "return".length());
        }
      }
    }
  }

  private int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}

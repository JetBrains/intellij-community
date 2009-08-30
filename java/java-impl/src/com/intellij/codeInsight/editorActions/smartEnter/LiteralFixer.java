package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 6:32:22 PM
 * To change this template use Options | File Templates.
 */
public class LiteralFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement)
      throws IncorrectOperationException {
    if (psiElement instanceof PsiJavaToken) {
      if (((PsiJavaToken) psiElement).getTokenType() == JavaTokenType.STRING_LITERAL &&
          !StringUtil.endsWithChar(psiElement.getText(), '\"')) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"");
      } else if (((PsiJavaToken) psiElement).getTokenType() == JavaTokenType.CHARACTER_LITERAL &&
                 !StringUtil.endsWithChar(psiElement.getText(), '\'')) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\'");
      }
    }
  }
}

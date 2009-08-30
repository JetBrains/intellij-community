package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 9:30:02 PM
 * To change this template use Options | File Templates.
 */
public class ParameterListFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiParameterList) {
      if (!StringUtil.endsWithChar(psiElement.getText(), ')')) {
        int offset;
        PsiParameterList list = (PsiParameterList) psiElement;
        final PsiParameter[] params = list.getParameters();
        if (params == null || params.length == 0) {
          offset = list.getTextRange().getStartOffset() + 1;
        } else {
          offset = params[params.length - 1].getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ")");
      }
    }
  }
}

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.util.IncorrectOperationException;

public class ParenthesizedFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiParenthesizedExpression) {
      final PsiElement lastChild = psiElement.getLastChild();
      if (lastChild != null && !")".equals(lastChild.getText())) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
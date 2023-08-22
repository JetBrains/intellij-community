// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.util.IncorrectOperationException;

public class MissingArrayConstructorBracketFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiNewExpression expr)) return;
    int count = 0;
    for (PsiElement element = expr.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element.getNode().getElementType() == JavaTokenType.LBRACKET) {
        count++;
      } else if (element.getNode().getElementType() == JavaTokenType.RBRACKET) {
        count--;
      }
    }
    if (count > 0) {
      editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "]");
    }
  }
}
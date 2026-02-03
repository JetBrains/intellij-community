// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class LiteralFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor,
                    @NotNull PsiElement psiElement)
    throws IncorrectOperationException {
    if (psiElement instanceof PsiJavaToken) {
      if (((PsiJavaToken)psiElement).getTokenType() == JavaTokenType.STRING_LITERAL &&
          !StringUtil.endsWithChar(psiElement.getText(), '\"')) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"");
      }
      else if (((PsiJavaToken)psiElement).getTokenType() == JavaTokenType.CHARACTER_LITERAL &&
               !StringUtil.endsWithChar(psiElement.getText(), '\'')) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "'");
      }
    }
  }
}

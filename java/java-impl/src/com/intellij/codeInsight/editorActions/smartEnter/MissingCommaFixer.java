// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class MissingCommaFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiErrorElement) {
      if (((PsiErrorElement)psiElement).getErrorDescription().equals(JavaPsiBundle.message("expected.comma.or.rparen"))) {
        PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiExpressionList) {
          PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(psiElement);
          if (next instanceof PsiExpression) {
            editor.getDocument().insertString(psiElement.getTextOffset(), ",");
          }
        }
      }
    }
  }
}

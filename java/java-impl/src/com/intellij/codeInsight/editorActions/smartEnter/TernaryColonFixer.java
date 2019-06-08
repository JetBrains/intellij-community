// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

class TernaryColonFixer implements Fixer {

  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiConditionalExpression)) {
      return;
    }

    PsiConditionalExpression ternary = (PsiConditionalExpression)psiElement;
    if (ternary.getThenExpression() == null || ternary.getNode().findChildByType(JavaTokenType.COLON) != null) {
      return;
    }


    editor.getCaretModel().moveToOffset(ternary.getTextRange().getEndOffset());
    EditorModificationUtil.insertStringAtCaret(editor, ": ");
    processor.setSkipEnter(true);
  }

}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

public class MissingCommaFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiErrorElement) ||
        !((PsiErrorElement)psiElement).getErrorDescription().equals(JavaPsiBundle.message("expected.comma.or.rparen"))) {
      return;
    }
    PsiElement parent = psiElement.getParent();
    if (!(parent instanceof PsiExpressionList)) return;
    if (!hasRParenth(psiElement, parent)) return;
    PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(psiElement);
    if (!(next instanceof PsiExpression)) return;
    PsiElement call = parent.getParent();
    if (call instanceof PsiCall) {
      PsiMethod method = ((PsiCall)call).resolveMethod();
      if (method != null) {
        PsiParameterList list = method.getParameterList();
        int count = list.getParametersCount();
        if (count == 0 || count == 1 && !method.isVarArgs()) return;
      }
    }
    editor.getDocument().insertString(psiElement.getTextOffset(), ",");
  }

  private static boolean hasRParenth(PsiElement psiElement, PsiElement parent) {
    for (PsiElement sibling = parent.getLastChild(); sibling != psiElement; sibling = sibling.getPrevSibling()) {
      if (PsiUtil.isJavaToken(sibling, JavaTokenType.RPARENTH)) {
        return true;
      }
    }
    return false;
  }
}

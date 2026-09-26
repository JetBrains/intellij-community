// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MissingArrayInitializerBraceFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, @NotNull PsiElement psiElement)
    throws IncorrectOperationException {
    if (!(psiElement instanceof PsiArrayInitializerExpression || psiElement instanceof PsiArrayInitializerMemberValue)) return;
    PsiElement child = psiElement.getFirstChild();
    if (!PsiUtil.isJavaToken(child, JavaTokenType.LBRACE)) return;
    if (!EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, child.getTextRange().getEndOffset(),
                                                                psiElement.getContainingFile().getFileType())) {
      return;
    }
    PsiElement anchor = PsiTreeUtil.getChildOfType(psiElement, PsiErrorElement.class);
    if (anchor == null) {
      PsiElement last = PsiTreeUtil.getDeepestVisibleLast(psiElement);
      while (PsiUtil.isJavaToken(last, JavaTokenType.RBRACE)) {
        last = PsiTreeUtil.prevCodeLeaf(last);
      }
      if (last != null && PsiTreeUtil.isAncestor(psiElement, last, true)) {
        anchor = last;
      }
    }
    int endOffset = (anchor != null ? anchor : psiElement).getTextRange().getEndOffset();
    editor.getDocument().insertString(endOffset, "}");
  }
}
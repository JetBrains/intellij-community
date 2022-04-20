// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;

public class IfConditionFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiIfStatement) {
      final Document doc = editor.getDocument();
      final PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
      final PsiJavaToken rParen = ifStatement.getRParenth();
      final PsiJavaToken lParen = ifStatement.getLParenth();
      final PsiExpression condition = ifStatement.getCondition();

      if (condition == null) {
        if (lParen == null || rParen == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(ifStatement.getTextRange().getStartOffset()));
          final PsiStatement then = ifStatement.getThenBranch();
          if (then != null) {
            stopOffset = Math.min(stopOffset, then.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, ifStatement.getTextRange().getEndOffset());

          PsiElement lastChild = ifStatement.getLastChild();
          String innerComment = "";
          String lastComment = "";
          if (lParen != null && PsiUtilCore.getElementType(lastChild) == JavaTokenType.C_STYLE_COMMENT) {
            innerComment = lastChild.getText();
          }
          else if (lastChild instanceof PsiComment) {
            lastComment = lastChild.getText();
          }

          String prefix = "if (" + innerComment;
          doc.replaceString(ifStatement.getTextRange().getStartOffset(), stopOffset, prefix + ")" + lastComment);

          processor.registerUnresolvedError(ifStatement.getTextRange().getStartOffset() + prefix.length());
        } else {
          processor.registerUnresolvedError(lParen.getTextRange().getEndOffset());
        }
      } else if (rParen == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
    else if (psiElement instanceof PsiExpression && psiElement.getParent() instanceof PsiExpressionStatement) {
      PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(psiElement);
      if (prevLeaf != null && prevLeaf.textMatches(PsiKeyword.IF)) {
        Document doc = editor.getDocument();
        doc.insertString(psiElement.getTextRange().getEndOffset(), ")");
        doc.insertString(psiElement.getTextRange().getStartOffset(), "(");
      }
    }
  }
}

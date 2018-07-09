// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * <pre>{@code
 * if(a) {
 *   if(b) {
 *     ...
 *   }
 * }
 * =>
 * if(a && b) {
 *   ...
 * }
 * }</pre>
 */
public class NestedIfJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull final Document document, @NotNull final PsiFile psiFile, final int start, final int end) {
    PsiJavaToken elementAtStartLineEnd = tryCast(psiFile.findElementAt(start), PsiJavaToken.class);
    PsiElement nextLineElement = psiFile.findElementAt(end);
    if (elementAtStartLineEnd == null || nextLineElement == null) return CANNOT_JOIN;
    PsiIfStatement outerIf = null;
    if (elementAtStartLineEnd.getTokenType().equals(JavaTokenType.RPARENTH)) {
      outerIf = tryCast(elementAtStartLineEnd.getParent(), PsiIfStatement.class);
    } else if(elementAtStartLineEnd.getTokenType().equals(JavaTokenType.LBRACE)) {
      PsiCodeBlock block = tryCast(elementAtStartLineEnd.getParent(), PsiCodeBlock.class);
      if (block != null) {
        PsiBlockStatement blockStatement = tryCast(block.getParent(), PsiBlockStatement.class);
        if (blockStatement != null) {
          outerIf = tryCast(blockStatement.getParent(), PsiIfStatement.class);
          if (outerIf == null || outerIf.getThenBranch() != blockStatement) return CANNOT_JOIN;
        }
      }
    }
    if (outerIf == null || outerIf.getElseBranch() != null) return CANNOT_JOIN;
    PsiIfStatement innerIf = tryCast(ControlFlowUtils.stripBraces(outerIf.getThenBranch()), PsiIfStatement.class);
    if (!PsiTreeUtil.isAncestor(innerIf, nextLineElement, false)) return CANNOT_JOIN;
    if (innerIf.getThenBranch() == null || innerIf.getElseBranch() != null) return CANNOT_JOIN;

    PsiExpression outerCondition = outerIf.getCondition();
    if (outerCondition == null) return CANNOT_JOIN;
    PsiExpression innerCondition = innerIf.getCondition();
    if (innerCondition == null) return CANNOT_JOIN;

    CommentTracker ct = new CommentTracker();
    String childConditionText = ParenthesesUtils.getText(ct.markUnchanged(innerCondition), ParenthesesUtils.OR_PRECEDENCE);
    String parentConditionText = ParenthesesUtils.getText(ct.markUnchanged(outerCondition), ParenthesesUtils.OR_PRECEDENCE);

    PsiElement newCondition = ct.replace(outerCondition, parentConditionText + "&&" + childConditionText);
    ct.replaceAndRestoreComments(outerIf.getThenBranch(), innerIf.getThenBranch());
    return newCondition.getTextRange().getStartOffset() + parentConditionText.length() + 1;
  }
}

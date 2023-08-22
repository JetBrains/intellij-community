// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

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
public class NestedIfJoinLinesHandler implements JoinRawLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull Document document, @NotNull PsiFile file, int start, int end) {
    return CANNOT_JOIN;
  }

  @Override
  public int tryJoinRawLines(@NotNull Document document, @NotNull PsiFile psiFile, int start, int end) {
    if (start == 0) return CANNOT_JOIN;
    PsiJavaToken elementAtStartLineEnd = tryCast(psiFile.findElementAt(start-1), PsiJavaToken.class);
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
    PsiJavaToken lParenth = outerIf.getLParenth();
    PsiJavaToken rParenth = innerIf.getRParenth();
    if (lParenth == null || rParenth == null) return CANNOT_JOIN;

    String outerPrefix = "", innerPrefix = "";
    int outerIfOffset = outerIf.getTextRange().getStartOffset();
    int innerIfOffset = innerIf.getTextRange().getStartOffset();
    int outerIfLine = document.getLineNumber(outerIfOffset);
    int innerIfLine = document.getLineNumber(innerIfOffset);
    if (innerIfLine > outerIfLine) {
      int outerLineStart = document.getLineStartOffset(outerIfLine);
      int innerLineStart = document.getLineStartOffset(innerIfLine);
      CharSequence sequence = document.getCharsSequence();
      outerPrefix = sequence.subSequence(outerLineStart, outerIfOffset).toString();
      innerPrefix = sequence.subSequence(innerLineStart, innerIfOffset).toString();
      if (!innerPrefix.startsWith(outerPrefix) || !innerPrefix.isBlank()) {
        outerPrefix = innerPrefix = "";
      }
    }

    String childConditionText = ParenthesesUtils.getText(innerCondition, ParenthesesUtils.OR_PRECEDENCE);
    String parentConditionText = ParenthesesUtils.getText(outerCondition, ParenthesesUtils.OR_PRECEDENCE);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiFile.getProject());
    PsiElement lastChild = outerCondition.getLastChild();
    String condition;
    if (lastChild instanceof PsiErrorElement &&
        PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesAndCommentsBackward(lastChild), JavaTokenType.ANDAND)) {
      // unterminated condition like if(a &&) -- reuse existing &&
      condition = parentConditionText + " " + childConditionText;
    } else {
      condition = parentConditionText + " && " + childConditionText;
    }
    String innerIfBody = innerIf.getText().substring(rParenth.getTextRangeInParent().getStartOffset());
    if (!innerPrefix.isEmpty()) {
      String finalInnerPrefix = innerPrefix;
      String finalOuterPrefix = outerPrefix;
      innerIfBody = StreamEx.split(innerIfBody, '\n', false)
        .map(s -> s.startsWith(finalInnerPrefix) ? finalOuterPrefix + s.substring(finalInnerPrefix.length()) : s)
        .joining("\n");
    }
    String resultText = outerIf.getText().substring(0, lParenth.getTextRangeInParent().getEndOffset())
                        + condition + innerIfBody;
    PsiStatement statement = factory.createStatementFromText(resultText, outerIf);
    PsiIfStatement result = (PsiIfStatement)outerIf.replace(statement);
    return Objects.requireNonNull(result.getCondition()).getTextRange().getStartOffset() +
           parentConditionText.length() + 2;
  }
}

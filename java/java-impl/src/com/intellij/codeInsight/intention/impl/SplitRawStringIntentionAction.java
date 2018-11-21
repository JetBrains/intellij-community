// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class SplitRawStringIntentionAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }

    final PsiJavaToken token = (PsiJavaToken)element;

    if (token.getTokenType() != JavaTokenType.RAW_STRING_LITERAL) {
      return false;
    }

    final String text = token.getText();
    if (text == null) {
      return false;
    }

    int leadingTicsSequence = PsiRawStringLiteralUtil.getLeadingTicksSequence(text);
    int trailingTicsSequence = PsiRawStringLiteralUtil.getTrailingTicksSequence(text);
    if (leadingTicsSequence == trailingTicsSequence) {
      int offset = editor.getCaretModel().getOffset();
      int caretInTokenIdx = offset - token.getTextOffset();
      if (caretInTokenIdx > leadingTicsSequence && 
          offset < token.getTextRange().getEndOffset() - trailingTicsSequence &&
          text.charAt(caretInTokenIdx) != '`' &&
          text.charAt(caretInTokenIdx - 1) != '`') {
        return true;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiJavaToken)) {
      return;
    }

    final PsiJavaToken token = (PsiJavaToken)element;

    if (token.getTokenType() != JavaTokenType.RAW_STRING_LITERAL) {
      return;
    }


    final String text = token.getText();
    if (text == null) {
      return;
    }

    int ticsSequenceLength = PsiRawStringLiteralUtil.getLeadingTicksSequence(text);
    String breakSequence = StringUtil.repeat("`", ticsSequenceLength);

    int offset = editor.getCaretModel().getOffset();
    int splitIdx = offset - token.getTextOffset();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiBinaryExpression replacement = (PsiBinaryExpression)factory
      .createExpressionFromText(text.substring(0, splitIdx) + breakSequence + " + " + breakSequence + text.substring(splitIdx), element);
    PsiPolyadicExpression replacedExpression = (PsiPolyadicExpression)ExpressionUtils.replacePolyadicWithParent((PsiExpression)token.getParent(), replacement);
    if (replacedExpression != null) {
      PsiElement leftOperand = replacedExpression.findElementAt(splitIdx);
      PsiExpression[] operands = replacedExpression.getOperands();
      int idx = ArrayUtil.find(operands, leftOperand);
      if (idx < operands.length - 1) {
        PsiJavaToken tokenBeforeOperand = replacedExpression.getTokenBeforeOperand(operands[idx + 1]);
        if (tokenBeforeOperand != null) {
          editor.getCaretModel().moveToOffset(tokenBeforeOperand.getTextOffset());
        }
      }
    }
    else {
      PsiBinaryExpression replaced = (PsiBinaryExpression)token.getParent().replace(replacement);
      editor.getCaretModel().moveToOffset(replaced.getOperationSign().getTextOffset());
    }
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Split raw string literal";
  }
}

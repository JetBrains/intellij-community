// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SplitTextBlockIntentionAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }

    final PsiJavaToken token = (PsiJavaToken)element;

    if (token.getTokenType() != JavaTokenType.TEXT_BLOCK_LITERAL) {
      return false;
    }

    return token.getText() != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiJavaToken)) {
      return;
    }

    final PsiJavaToken token = (PsiJavaToken)element;

    if (token.getTokenType() != JavaTokenType.TEXT_BLOCK_LITERAL) {
      return;
    }


    final String text = token.getText();
    if (text == null) {
      return;
    }

    int offset = editor.getCaretModel().getOffset();
    int splitIdx = offset - token.getTextOffset();

    String firstBlock = StringUtil.trimTrailing(text.substring(0, splitIdx));
    String lastBlock = StringUtil.trimLeading(text.substring(splitIdx));
    int indent = ((PsiLiteralExpressionImpl)element.getParent()).getTextBlockIndent();
    int trimmedSpaces = text.length() - firstBlock.length() - lastBlock.length();
    int newLineIdx = text.lastIndexOf('\n', splitIdx);
    if (StringUtil.isEmptyOrSpaces(text.substring(newLineIdx, splitIdx))) {
      trimmedSpaces -= indent + 1;
    }
    String trailingSpaces = trimmedSpaces > 0 ? " + \"" + StringUtil.repeat(" ", trimmedSpaces) + "\"" 
                                              : "";
    PsiPolyadicExpression replacement = (PsiPolyadicExpression)JavaPsiFacade.getElementFactory(project)
      .createExpressionFromText(firstBlock + "\"\"\"" + 
                                trailingSpaces + 
                                " + \"\"\"" + '\n' + 
                                StringUtil.repeat(" ", indent) + lastBlock, element);
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
      PsiPolyadicExpression replaced = (PsiPolyadicExpression)token.getParent().replace(replacement);
      PsiExpression[] operands = replaced.getOperands();
      assert operands.length > 1;
      editor.getCaretModel().moveToOffset(Objects.requireNonNull(replaced.getTokenBeforeOperand(operands[1])).getTextOffset());
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
    return "Split text block";
  }
}

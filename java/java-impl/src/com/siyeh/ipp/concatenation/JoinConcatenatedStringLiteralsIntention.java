/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.concatenation;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class JoinConcatenatedStringLiteralsIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("join.concatenated.string.literals.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("join.concatenated.string.literals.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new StringConcatPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    if (!(element instanceof PsiJavaToken token)) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element.getParent();
    final StringBuilder newExpression = new StringBuilder();
    PsiExpression[] operands = polyadicExpression.getOperands();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      if (polyadicExpression.getTokenBeforeOperand(operand) == token) {
        PsiExpression prev = operands[i - 1];
        PsiElement nextSibling = polyadicExpression.getFirstChild();
        while (nextSibling != prev) {
          newExpression.append(tracker.text(nextSibling));
          nextSibling = nextSibling.getNextSibling();
        }
        merge((PsiLiteralExpression)prev, (PsiLiteralExpression)operand, newExpression);
        nextSibling = operand.getNextSibling();
        while (nextSibling != null) {
          newExpression.append(tracker.text(nextSibling));
          nextSibling = nextSibling.getNextSibling();
        }
        break;
      }
    }
    PsiReplacementUtil.replaceExpression(polyadicExpression, newExpression.toString(), tracker);
  }

  private static void merge(PsiLiteralExpression left, PsiLiteralExpression right, StringBuilder newExpression) {
    final String leftText = getLiteralExpressionText(left);
    final String rightText = getLiteralExpressionText(right);
    if (left.isTextBlock()) {
      newExpression.append("\"\"\"\n").append(leftText)
        .append(right.isTextBlock() ? rightText : PsiLiteralUtil.escapeTextBlockCharacters(rightText)).append("\"\"\"");
    }
    else if (right.isTextBlock()) {
      newExpression.append("\"\"\"\n").append(PsiLiteralUtil.escapeTextBlockCharacters(leftText)).append(rightText).append("\"\"\"");
    }
    else {
      newExpression.append('"').append(leftText).append(rightText).append('"');
    }
  }

  private static String getLiteralExpressionText(PsiLiteralExpression expression) {
    final PsiType type = expression.getType();
    if (PsiTypes.charType().equals(type)) {
      final String result = StringUtil.unquoteString(expression.getText());
      if (result.equals("\"")) return "\\\"";
      if (result.equals("\\'")) return "'";
      return result;
    }
    else if (type instanceof PsiPrimitiveType) {
      return Objects.requireNonNull(expression.getValue()).toString();
    }
    else if (expression.isTextBlock()) {
      return PsiLiteralUtil.getTextBlockText(expression);
    }
    else {
      return PsiLiteralUtil.getStringLiteralContent(expression);
    }
  }
}

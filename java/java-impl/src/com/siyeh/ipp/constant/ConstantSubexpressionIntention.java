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
package com.siyeh.ipp.constant;

import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ConstantSubexpressionIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("constant.subexpression.intention.family.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ConstantSubexpressionPredicate();
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    final PsiJavaToken token;
    if (element instanceof PsiJavaToken) {
      token = (PsiJavaToken)element;
    }
    else {
      final PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling instanceof PsiJavaToken) {
        token = (PsiJavaToken)prevSibling;
      }
      else {
        throw new AssertionError();
      }
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element.getParent();
    final PsiPolyadicExpression subexpression = ConstantSubexpressionPredicate.getSubexpression(polyadicExpression, token);
    final String text = getPresentableText(subexpression);
    return IntentionPowerPackBundle.message("constant.expression.intention.name", text);
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken token;
    if (element instanceof PsiJavaToken) {
      token = (PsiJavaToken)element;
    }
    else {
      final PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling instanceof PsiJavaToken) {
        token = (PsiJavaToken)prevSibling;
      }
      else {
        throw new AssertionError();
      }
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element.getParent();
    final PsiPolyadicExpression subexpression = ConstantSubexpressionPredicate.getSubexpression(polyadicExpression, token);
    if (subexpression == null) {
      return;
    }
    final Object value = ExpressionUtils.computeConstantExpression(subexpression);
    @NonNls final StringBuilder newExpressionText = new StringBuilder();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    PsiExpression prevOperand = null;
    PsiJavaToken prevToken = null;
    CommentTracker commentTracker = new CommentTracker();
    for (PsiExpression operand : operands) {
      final PsiJavaToken currentToken = polyadicExpression.getTokenBeforeOperand(operand);
      if (prevToken != null) {
        newExpressionText.append(prevToken.getText());
      }
      if (token == currentToken) {
        if (newExpressionText.length() > 0) {
          newExpressionText.append(' ');
        }
        if (value instanceof Long) {
          newExpressionText.append(value).append('L');
        }
        else if (value instanceof Double) {
          final double v = ((Double)value).doubleValue();
          if (Double.isNaN(v)) {
            newExpressionText.append("java.lang.Double.NaN");
          }
          else if (Double.isInfinite(v)) {
            if (v > 0.0) {
              newExpressionText.append("java.lang.Double.POSITIVE_INFINITY");
            }
            else {
              newExpressionText.append("java.lang.Double.NEGATIVE_INFINITY");
            }
          }
          else {
            newExpressionText.append(v);
          }
        }
        else if (value instanceof Float) {
          final float v = ((Float)value).floatValue();
          if (Float.isNaN(v)) {
            newExpressionText.append("java.lang.Float.NaN");
          }
          else if (Float.isInfinite(v)) {
            if (v > 0.0F) {
              newExpressionText.append("java.lang.Float.POSITIVE_INFINITY");
            }
            else {
              newExpressionText.append("java.lang.Float.NEGATIVE_INFINITY");
            }
          }
          else {
            newExpressionText.append(v).append('f');
          }
        }
        else {
          newExpressionText.append(value);
        }
        prevOperand = null;
        prevToken = null;
      }
      else {
        if (prevOperand != null) {
          newExpressionText.append(commentTracker.text(prevOperand));
        }
        prevOperand = operand;
        prevToken = currentToken;
      }
    }
    if (prevToken != null) {
      newExpressionText.append(prevToken.getText());
    }
    if (prevOperand != null) {
      newExpressionText.append(commentTracker.text(prevOperand));
    }

    PsiReplacementUtil.replaceExpression(polyadicExpression, newExpressionText.toString(), commentTracker);
  }

  private static String getPresentableText(PsiElement element) {
    return getPresentableText(element, new StringBuilder()).toString();
  }

  private static StringBuilder getPresentableText(PsiElement element, StringBuilder builder) {
    if (element == null) {
      return builder;
    }
    if (element instanceof PsiWhiteSpace) {
      return builder.append(' ');
    }
    final PsiElement[] children = element.getChildren();
    if (children.length != 0) {
      for (PsiElement child : children) {
        getPresentableText(child, builder);
      }
    }
    else {
      builder.append(element.getText());
    }
    return builder;
  }
}
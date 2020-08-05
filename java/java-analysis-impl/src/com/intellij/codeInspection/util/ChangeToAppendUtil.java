// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public final class ChangeToAppendUtil {
  @Nullable
  public static PsiExpression buildAppendExpression(PsiExpression appendable, PsiExpression concatenation) {
    if (concatenation == null) return null;
    final PsiType type = appendable.getType();
    if (type == null) return null;
    final boolean useStringValueOf = !type.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUFFER) &&
                                     !type.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER);
    final StringBuilder result = buildAppendExpression(concatenation, useStringValueOf, new StringBuilder(appendable.getText()));
    if (result == null) return null;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(appendable.getProject());
    return factory.createExpressionFromText(result.toString(), appendable);
  }

  @Nullable
  public static StringBuilder buildAppendExpression(@Nullable PsiExpression concatenation, boolean useStringValueOf, @NonNls StringBuilder out) {
    if (concatenation == null) return null;
    final PsiType type = concatenation.getType();
    if (concatenation instanceof PsiPolyadicExpression && type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)concatenation;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      boolean isConstant = true;
      boolean isString = false;
      final StringBuilder builder = new StringBuilder();
      for (PsiExpression operand : operands) {
        if (isConstant && PsiUtil.isConstantExpression(operand)) {
          if (builder.length() != 0) {
            builder.append('+');
          }
          final PsiType operandType = operand.getType();
          if (operandType != null && operandType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            isString = true;
          }
          builder.append(CommentTracker.textWithSurroundingComments(operand));
        }
        else if (!operand.textMatches("\"\"")) {
          isConstant = false;
          if (builder.length() != 0) {
            append(builder, useStringValueOf && !isString, out);
            builder.setLength(0);
          }
          buildAppendExpression(operand, useStringValueOf, out);
        }
      }
      if (builder.length() != 0) {
        append(builder, false, out);
      }
    }
    else if (concatenation instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)concatenation;
      final PsiExpression expression = parenthesizedExpression.getExpression();
      if (expression != null) {
        return buildAppendExpression(expression, useStringValueOf, out);
      }
    }
    else {
      append(CommentTracker.textWithSurroundingComments(concatenation),
             useStringValueOf && (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)), out);
    }
    return out;
  }

  private static void append(CharSequence text, boolean useStringValueOf, StringBuilder out) {
    out.append(".append(");
    if (useStringValueOf) {
      out.append("String.valueOf(").append(text).append(')');
    }
    else {
      out.append(text);
    }
    out.append(')');
  }
}

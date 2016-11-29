/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class ChangeToAppendUtil {
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
          builder.append(operand.getText());
        }
        else {
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
      append(concatenation.getText(), useStringValueOf && (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)), out);
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

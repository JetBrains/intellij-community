/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class PsiConcatenationUtil {

  public static void buildFormatString(PsiExpression expression, StringBuilder formatString,
                                       List<PsiExpression> formatParameters, boolean printfFormat) {
    if (expression instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression) expression;
      final String text = String.valueOf(literalExpression.getValue());
      final String formatText;
      if (printfFormat) {
        formatText = StringUtil.escapeStringCharacters(text).replace("%", "%%").replace("\\'", "'");
      }
      else {
        formatText = StringUtil.escapeStringCharacters(text).replace("'", "''").replaceAll("((\\{|})+)", "'$1'");
      }
      formatString.append(formatText);
    } else if (expression instanceof PsiPolyadicExpression) {
      final PsiType type = expression.getType();
      if (type != null && type.equalsToText(JAVA_LANG_STRING)) {
        final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression) expression;
        PsiExpression[] operands = binaryExpression.getOperands();
        PsiType left = operands[0].getType();
        boolean stringStarted = left != null && left.equalsToText(JAVA_LANG_STRING);
        if (stringStarted) {
          buildFormatString(operands[0], formatString, formatParameters, printfFormat);
        }
        for (int i = 1; i < operands.length; i++) {
          PsiExpression op = operands[i];
          PsiType optype = op.getType();
          PsiType r = TypeConversionUtil.calcTypeForBinaryExpression(left, optype, binaryExpression.getOperationTokenType(), true);
          if (r != null && r.equalsToText(JAVA_LANG_STRING) && !stringStarted) {
            stringStarted = true;
            PsiElement element = binaryExpression.getTokenBeforeOperand(op);
            if (element.getPrevSibling() instanceof PsiWhiteSpace) element = element.getPrevSibling();
            String text = binaryExpression.getText().substring(0, element.getStartOffsetInParent());
            PsiExpression subExpression = JavaPsiFacade.getElementFactory(binaryExpression.getProject())
              .createExpressionFromText(text, binaryExpression);
            addFormatParameter(subExpression, formatString, formatParameters, printfFormat);
          }
          if (stringStarted) {
            if (optype != null && (optype.equalsToText(JAVA_LANG_STRING) || PsiType.CHAR.equals(optype))) {
              buildFormatString(op, formatString, formatParameters, printfFormat);
            }
            else {
              addFormatParameter(op, formatString, formatParameters, printfFormat);
            }
          }
          left = r;
        }
      }
      else {
        addFormatParameter(expression, formatString, formatParameters, printfFormat);
      }
    }
    else {
      addFormatParameter(expression, formatString, formatParameters, printfFormat);
    }
  }

  private static void addFormatParameter(@NotNull PsiExpression expression,
                                         StringBuilder formatString,
                                         List<PsiExpression> formatParameters, boolean printfFormat) {
    final PsiType type = expression.getType();
    if (!printfFormat) {
      formatString.append("{").append(formatParameters.size()).append("}");
    }
    else if (type != null &&
             (type.equalsToText("long") ||
              type.equalsToText("int") ||
              type.equalsToText("java.lang.Long") ||
              type.equalsToText("java.lang.Integer"))) {
      formatString.append("%d");
    }
    else {
      formatString.append("%s");
    }
    formatParameters.add(getBoxedArgument(expression));
  }

  private static PsiExpression getBoxedArgument(@NotNull PsiExpression arg) {
    arg = ObjectUtils.coalesce(unwrapExpression(arg), arg);
    if (PsiUtil.isLanguageLevel5OrHigher(arg)) {
      return arg;
    }
    final PsiType type = arg.getType();
    if (!(type instanceof PsiPrimitiveType) || type.equals(PsiType.NULL)) {
      return arg;
    }
    final PsiPrimitiveType primitiveType = (PsiPrimitiveType)type;
    final String boxedQName = primitiveType.getBoxedTypeName();
    if (boxedQName == null) {
      return arg;
    }
    final GlobalSearchScope resolveScope = arg.getResolveScope();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(arg.getProject());
    final PsiJavaCodeReferenceElement ref = factory.createReferenceElementByFQClassName(boxedQName, resolveScope);
    final PsiNewExpression newExpr = (PsiNewExpression)factory.createExpressionFromText("new A(b)", null);
    final PsiElement classRef = newExpr.getClassReference();
    assert classRef != null;
    classRef.replace(ref);
    final PsiExpressionList argumentList = newExpr.getArgumentList();
    assert argumentList != null;
    argumentList.getExpressions()[0].replace(arg);
    return newExpr;
  }

  @Nullable
  private static PsiExpression unwrapExpression(@NotNull PsiExpression expression) {
    while (true) {
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
        continue;
      }
      if (expression instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
        final PsiType castType = typeCastExpression.getType();
        final PsiExpression operand = typeCastExpression.getOperand();
        if (operand == null) {
          return expression;
        }
        if (TypeConversionUtil.isNumericType(castType)) {
          final PsiType operandType = operand.getType();
          if (operandType == null) {
            return expression;
          }
          final int castRank = TypeConversionUtil.getTypeRank(castType);
          final int operandRank = TypeConversionUtil.getTypeRank(operandType);
          if (castRank < operandRank || castRank == TypeConversionUtil.CHAR_RANK && operandRank != castRank) {
            return expression;
          }
        }
        expression = operand;
        continue;
      }
      return expression;
    }
  }
}

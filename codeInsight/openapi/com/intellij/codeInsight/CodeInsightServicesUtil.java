/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class CodeInsightServicesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.CodeInsightServicesUtil");

  private static IElementType[] ourTokenMap = new IElementType[]{
    JavaTokenType.EQEQ, JavaTokenType.NE,
    JavaTokenType.LT, JavaTokenType.GE,
    JavaTokenType.LE, JavaTokenType.GT,
    JavaTokenType.OROR, JavaTokenType.ANDAND
  };

  public static PsiExpression invertCondition(PsiExpression booleanExpression) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(booleanExpression.getProject()).getElementFactory();

    if (booleanExpression instanceof PsiBinaryExpression) {
      PsiBinaryExpression expression = (PsiBinaryExpression) booleanExpression;
      PsiJavaToken operationSign = expression.getOperationSign();
      for (int i = 0; i < ourTokenMap.length; i++) {
        IElementType tokenType = ourTokenMap[i];
        if (operationSign.getTokenType() == tokenType) {
          expression = (PsiBinaryExpression)expression.copy();
          expression.getOperationSign().replace(createOperationToken(factory, ourTokenMap[i + (i % 2 == 0 ? 1 : -1)]));
          if (tokenType == JavaTokenType.OROR || tokenType == JavaTokenType.ANDAND) {
            expression.getLOperand().replace(invertCondition(expression.getLOperand()));
            expression.getROperand().replace(invertCondition(expression.getROperand()));
          }
          return expression;
        }
      }
    } else if (booleanExpression instanceof PsiPrefixExpression) {
      PsiPrefixExpression expression = (PsiPrefixExpression) booleanExpression;
      PsiJavaToken operationSign = expression.getOperationSign();
      if (operationSign.getTokenType() == JavaTokenType.EXCL) {
        PsiExpression operand = expression.getOperand();
        if (operand instanceof PsiParenthesizedExpression) {
          operand = ((PsiParenthesizedExpression) operand).getExpression();
        }
        return operand;
      }
    } else if (booleanExpression instanceof PsiLiteralExpression) {
      return booleanExpression.getText().equals("true") ?
             factory.createExpressionFromText("false", null) :
             factory.createExpressionFromText("true", null);
    }

    if (booleanExpression instanceof PsiParenthesizedExpression) {
      PsiExpression operand = ((PsiParenthesizedExpression) booleanExpression).getExpression();
      operand.replace(invertCondition(operand));
      return booleanExpression;
    }

    PsiPrefixExpression result = (PsiPrefixExpression)factory.createExpressionFromText("!(a)", null);
    if (!(booleanExpression instanceof PsiBinaryExpression)) {
      result.getOperand().replace(booleanExpression);
    } else {
      PsiParenthesizedExpression e = (PsiParenthesizedExpression) result.getOperand();
      e.getExpression().replace(booleanExpression);
    }

    return result;
  }

  private static PsiElement createOperationToken(PsiElementFactory factory, IElementType tokenType) throws IncorrectOperationException {
    final String s;
    if (tokenType == JavaTokenType.EQEQ) {
      s = "==";
    }
    else if (tokenType == JavaTokenType.NE) {
      s = "!=";
    }
    else if (tokenType == JavaTokenType.LT) {
      s = "<";
    }
    else if (tokenType == JavaTokenType.LE) {
      s = "<=";
    }
    else if (tokenType == JavaTokenType.GT) {
      s = ">";
    }
    else if (tokenType == JavaTokenType.GE) {
      s = ">=";
    }
    else if (tokenType == JavaTokenType.ANDAND) {
      s = "&&";
    }
    else if (tokenType == JavaTokenType.OROR) {
      s = "||";
    }
    else {
      LOG.error("Unknown token type");
      s = "==";
    }

    PsiBinaryExpression expression = (PsiBinaryExpression) factory.createExpressionFromText("a" + s + "b", null);
    return expression.getOperationSign();
  }
}

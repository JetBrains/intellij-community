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
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

/**
 * Consider using instead
 * {@link com.siyeh.ig.psiutils.BoolUtils#getNegatedExpressionText(com.intellij.psi.PsiExpression)}
 * 
 * to be deleted in 2018.2
 */
@Deprecated
public class CodeInsightServicesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.CodeInsightServicesUtil");

  private static final IElementType[] ourTokenMap = {
    JavaTokenType.EQEQ, JavaTokenType.NE,
    JavaTokenType.LT, JavaTokenType.GE,
    JavaTokenType.LE, JavaTokenType.GT,
    JavaTokenType.OROR, JavaTokenType.ANDAND
  };

  public static PsiExpression invertCondition(PsiExpression booleanExpression) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(booleanExpression.getProject()).getElementFactory();

    if (booleanExpression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression expression = (PsiPolyadicExpression)booleanExpression;
      IElementType operationSign = expression.getOperationTokenType();
      for (int i = 0; i < ourTokenMap.length; i++) {
        IElementType tokenType = ourTokenMap[i];
        if (operationSign == tokenType) {
          expression = (PsiPolyadicExpression)expression.copy();
          PsiExpression[] operands = expression.getOperands();
          for (int o = 0; o < operands.length; o++) {
            PsiExpression op = operands[o];
            if (o != 0) {
              expression.getTokenBeforeOperand(op).replace(createOperationToken(factory, ourTokenMap[i + (i % 2 == 0 ? 1 : -1)]));
            }
            if (tokenType == JavaTokenType.OROR || tokenType == JavaTokenType.ANDAND) {
              PsiExpression inverted = invertCondition(op);
              op.replace(inverted);
            }
          }
          if (tokenType == JavaTokenType.ANDAND && booleanExpression.getParent() instanceof PsiPolyadicExpression) {
            final PsiParenthesizedExpression parth = (PsiParenthesizedExpression)factory.createExpressionFromText("(a)", expression);
            parth.getExpression().replace(expression);
            return parth;
          }
          return expression;
        }
      }
    }
    else if (booleanExpression instanceof PsiPrefixExpression) {
      PsiPrefixExpression expression = (PsiPrefixExpression)booleanExpression;
      if (expression.getOperationTokenType() == JavaTokenType.EXCL) {
        PsiExpression operand = expression.getOperand();
        if (operand instanceof PsiParenthesizedExpression) {
          final PsiElement parent = booleanExpression.getParent();
          if (parent instanceof PsiPolyadicExpression && 
              ((PsiPolyadicExpression)parent).getOperationTokenType() == JavaTokenType.ANDAND) {
            operand = ((PsiParenthesizedExpression)operand).getExpression();
          }
        }
        return operand;
      }
    }
    else if (booleanExpression instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)booleanExpression).getValue();
      if (value instanceof Boolean) {
        return factory.createExpressionFromText(String.valueOf(!((Boolean)value)), booleanExpression);
      }
    }

    if (booleanExpression instanceof PsiParenthesizedExpression) {
      PsiExpression operand = ((PsiParenthesizedExpression)booleanExpression).getExpression();
      if (operand != null) {
        operand.replace(invertCondition(operand));
        return booleanExpression;
      }
    }

    PsiPrefixExpression result = (PsiPrefixExpression)factory.createExpressionFromText("!(a)", null);
    if (!(booleanExpression instanceof PsiPolyadicExpression)) {
      result.getOperand().replace(booleanExpression);
    }
    else {
      PsiParenthesizedExpression e = (PsiParenthesizedExpression)result.getOperand();
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

    PsiBinaryExpression expression = (PsiBinaryExpression)factory.createExpressionFromText("a" + s + "b", null);
    return expression.getOperationSign();
  }
}

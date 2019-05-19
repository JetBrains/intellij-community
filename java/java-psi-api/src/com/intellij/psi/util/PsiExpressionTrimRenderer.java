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

import com.intellij.psi.*;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionTrimRenderer extends JavaRecursiveElementWalkingVisitor {
  private final StringBuilder myBuf;

  public PsiExpressionTrimRenderer(final StringBuilder buf) {
    myBuf = buf;
  }

  @Override
  public void visitExpression(final PsiExpression expression) {
    myBuf.append(expression.getText());
  }

  @Override
  public void visitInstanceOfExpression(final PsiInstanceOfExpression expression) {
    expression.getOperand().accept(this);
    myBuf.append(" ").append(PsiKeyword.INSTANCEOF).append(" ");
    final PsiTypeElement checkType = expression.getCheckType();
    if (checkType != null) {
      myBuf.append(checkType.getText());
    }
  }

  @Override
  public void visitParenthesizedExpression(final PsiParenthesizedExpression expression) {
    myBuf.append("(");
    final PsiExpression expr = expression.getExpression();
    if (expr != null) {
      expr.accept(this);
    }
    myBuf.append(")");
  }

  @Override
  public void visitTypeCastExpression(final PsiTypeCastExpression expression) {
    final PsiTypeElement castType = expression.getCastType();
    if (castType != null) {
      myBuf.append("(").append(castType.getText()).append(")");
    }
    final PsiExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);
    }
  }

  @Override
  public void visitArrayAccessExpression(final PsiArrayAccessExpression expression) {
    expression.getArrayExpression().accept(this);
    myBuf.append("[");
    final PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
    }
    myBuf.append("]");
  }

  @Override
  public void visitPrefixExpression(final PsiPrefixExpression expression) {
    myBuf.append(expression.getOperationSign().getText());
    final PsiExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);
    }
  }

  @Override
  public void visitPostfixExpression(final PsiPostfixExpression expression) {
    expression.getOperand().accept(this);
    myBuf.append(expression.getOperationSign().getText());
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    PsiExpression[] operands = expression.getOperands();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      if (i != 0) {
        PsiJavaToken token = expression.getTokenBeforeOperand(operand);
        myBuf.append(" ").append(token.getText()).append(" ");
      }
      operand.accept(this);
    }
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    PsiParameterList parameterList = expression.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();

    PsiElement firstChild = parameterList.getFirstChild();
    boolean addParenthesis = PsiUtil.isJavaToken(firstChild, JavaTokenType.LPARENTH);

    if (addParenthesis) myBuf.append('(');
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (i != 0) {
        myBuf.append(", ");
      }
      PsiTypeElement typeElement = parameter.getTypeElement();
      int formatOptions = PsiFormatUtilBase.SHOW_NAME | (typeElement == null ? 0 : PsiFormatUtilBase.SHOW_TYPE);
      myBuf.append(PsiFormatUtil.formatVariable(parameter, formatOptions, PsiSubstitutor.EMPTY));
    }
    if (addParenthesis) myBuf.append(')');
    myBuf.append(" -> {...}");
  }

  @Override
  public void visitConditionalExpression(final PsiConditionalExpression expression) {
    expression.getCondition().accept(this);

    myBuf.append(" ? ");
    final PsiExpression thenExpression = expression.getThenExpression();
    if (thenExpression != null) {
      thenExpression.accept(this);
    }

    myBuf.append(" : ");
    final PsiExpression elseExpression = expression.getElseExpression();
    if (elseExpression != null) {
      elseExpression.accept(this);
    }
  }

  @Override
  public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
    expression.getLExpression().accept(this);
    myBuf.append(expression.getOperationSign().getText());
    final PsiExpression rExpression = expression.getRExpression();
    if (rExpression != null) {
      rExpression.accept(this);
    }
  }

  @Override
  public void visitReferenceExpression(final PsiReferenceExpression expr) {
    final PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
      myBuf.append(".");
    }
    myBuf.append(expr.getReferenceName());

  }

  @Override
  public void visitMethodCallExpression(final PsiMethodCallExpression expr) {
    expr.getMethodExpression().accept(this);
    expr.getArgumentList().accept(this);
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    final PsiElement qualifier = expression.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
    }
    myBuf.append("::");
    myBuf.append(expression.getReferenceName());
  }

  @Override
  public void visitArrayInitializerExpression(final PsiArrayInitializerExpression expression) {
    myBuf.append("{");
    boolean first = true;
    for (PsiExpression expr : expression.getInitializers()) {
      if (!first) {
        myBuf.append(", ");
      }
      first = false;
      expr.accept(this);
    }
    myBuf.append("}");
  }

  @Override
  public void visitExpressionList(final PsiExpressionList list) {
    final PsiExpression[] args = list.getExpressions();
    if (args.length > 0) {
      myBuf.append("(...)");
    }
    else {
      myBuf.append("()");
    }
  }

  @Override
  public void visitNewExpression(final PsiNewExpression expr) {
    final PsiAnonymousClass anonymousClass = expr.getAnonymousClass();

    final PsiExpressionList argumentList = expr.getArgumentList();

    if (anonymousClass != null) {
      myBuf.append(PsiKeyword.NEW).append(" ").append(anonymousClass.getBaseClassType().getPresentableText());
      if (argumentList != null) argumentList.accept(this);
      myBuf.append(" {...}");
    }
    else {
      final PsiJavaCodeReferenceElement reference = expr.getClassReference();
      if (reference != null) {
        myBuf.append(PsiKeyword.NEW).append(" ").append(reference.getText());

        final PsiExpression[] arrayDimensions = expr.getArrayDimensions();
        final PsiType type = expr.getType();
        final int dimensions = type != null ? type.getArrayDimensions() : arrayDimensions.length;
        if (arrayDimensions.length > 0) myBuf.append("[");
        for (int i = 0, arrayDimensionsLength = arrayDimensions.length; i < dimensions; i++) {
          final PsiExpression dimension = i < arrayDimensionsLength ? arrayDimensions[i] : null;
          if (i > 0) myBuf.append("][");
          if (dimension != null) {
            dimension.accept(this);
          }
        }
        if (arrayDimensions.length > 0) myBuf.append("]");

        if (argumentList != null) {
          argumentList.accept(this);
        }

        final PsiArrayInitializerExpression arrayInitializer = expr.getArrayInitializer();
        if (arrayInitializer != null) {
          arrayInitializer.accept(this);
        }
      }
      else {
        myBuf.append(expr.getText());
      }
    }
  }

  public static class RenderFunction implements NotNullFunction<PsiExpression, String> {
    @NotNull
    @Override
    public String fun(@NotNull PsiExpression psiExpression) {
      return render(psiExpression);
    }
  }

  public static String render(@NotNull PsiExpression expression) {
    return render(expression, 100);
  }

  public static String render(@NotNull PsiExpression expression, int maxLength) {
    StringBuilder buf = new StringBuilder();
    expression.accept(new PsiExpressionTrimRenderer(buf));
    final String text = buf.toString();
    int firstNewLinePos = text.indexOf('\n');
    String trimmedText = text.substring(0, firstNewLinePos != -1 ? firstNewLinePos : Math.min(maxLength, text.length()));
    if (trimmedText.length() != text.length()) trimmedText += " ...";
    return trimmedText;
  }
}

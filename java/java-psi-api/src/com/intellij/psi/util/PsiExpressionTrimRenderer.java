// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionTrimRenderer extends JavaRecursiveElementWalkingVisitor {
  private final StringBuilder myBuf;

  public PsiExpressionTrimRenderer(StringBuilder buf) {
    myBuf = buf;
  }

  @Override
  public void visitExpression(@NotNull PsiExpression expression) {
    myBuf.append(expression.getText());
  }

  @Override
  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    expression.getOperand().accept(this);
    myBuf.append(" ").append(JavaKeywords.INSTANCEOF).append(" ");
    final PsiTypeElement checkType = expression.getCheckType();
    if (checkType != null) {
      myBuf.append(checkType.getText());
    }
    final PsiPrimaryPattern pattern = expression.getPattern();
    if (pattern != null) {
      myBuf.append(pattern.getText());
    }
  }

  @Override
  public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
    myBuf.append("(");
    final PsiExpression expr = expression.getExpression();
    if (expr != null) {
      expr.accept(this);
    }
    myBuf.append(")");
  }

  @Override
  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
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
  public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
    expression.getArrayExpression().accept(this);
    myBuf.append("[");
    final PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
    }
    myBuf.append("]");
  }

  @Override
  public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
    myBuf.append(expression.getOperationSign().getText());
    final PsiExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);
    }
  }

  @Override
  public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
    expression.getOperand().accept(this);
    myBuf.append(expression.getOperationSign().getText());
  }

  @Override
  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
    for (PsiElement child : expression.getChildren()) {
      if (child instanceof PsiExpression) {
        child.accept(this);
      }
      else if (child instanceof PsiJavaToken) {
        myBuf.append(" ").append(child.getText()).append(" ");
      }
    }
  }

  @Override
  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
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
  public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
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
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
    expression.getLExpression().accept(this);
    myBuf.append(expression.getOperationSign().getText());
    final PsiExpression rExpression = expression.getRExpression();
    if (rExpression != null) {
      rExpression.accept(this);
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expr) {
    final PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
      myBuf.append(".");
    }
    myBuf.append(expr.getReferenceName());

  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expr) {
    expr.getMethodExpression().accept(this);
    expr.getArgumentList().accept(this);
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    final PsiElement qualifier = expression.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
    }
    myBuf.append("::");
    myBuf.append(expression.getReferenceName());
  }

  @Override
  public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
    final PsiExpression[] initializers = expression.getInitializers();
    if (initializers.length > 1) {
      myBuf.append("{...}");
    }
    else {
      myBuf.append("{");
      if (initializers.length > 0) {
        initializers[0].accept(this);
      }
      myBuf.append("}");
    }
  }

  @Override
  public void visitExpressionList(@NotNull PsiExpressionList list) {
    final PsiExpression[] args = list.getExpressions();
    if (args.length > 0) {
      myBuf.append("(...)");
    }
    else {
      myBuf.append("()");
    }
  }

  @Override
  public void visitSwitchExpression(@NotNull PsiSwitchExpression switchExpression) {
    myBuf.append("switch (");
    final PsiExpression expression = switchExpression.getExpression();
    if (expression != null) {
      expression.accept(this);
    }
    myBuf.append(") {...}");
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expr) {
    final PsiAnonymousClass anonymousClass = expr.getAnonymousClass();

    final PsiExpressionList argumentList = expr.getArgumentList();

    if (anonymousClass != null) {
      myBuf.append(JavaKeywords.NEW).append(" ").append(anonymousClass.getBaseClassType().getPresentableText());
      if (argumentList != null) argumentList.accept(this);
      myBuf.append(" {...}");
    }
    else {
      final PsiJavaCodeReferenceElement reference = expr.getClassReference();
      if (reference != null) {
        myBuf.append(JavaKeywords.NEW).append(" ").append(reference.getText());

        final PsiExpression[] arrayDimensions = expr.getArrayDimensions();
        final PsiType type = expr.getType();
        for (PsiExpression dimension : arrayDimensions) {
          myBuf.append("[");
          dimension.accept(this);
          myBuf.append("]");
        }
        if (type != null) {
          final int dimensions = type.getArrayDimensions() - arrayDimensions.length;
          for (int i = 0; i < dimensions; i++) {
            myBuf.append("[]");
          }
        }

        if (argumentList != null) {
          argumentList.accept(this);
        }

        final PsiArrayInitializerExpression arrayInitializer = expr.getArrayInitializer();
        if (arrayInitializer != null) {
          myBuf.append(" ");
          arrayInitializer.accept(this);
        }
      }
      else {
        myBuf.append(expr.getText());
      }
    }
  }

  public static class RenderFunction implements NotNullFunction<PsiExpression, String> {
    @Override
    public @NotNull String fun(@NotNull PsiExpression psiExpression) {
      return render(psiExpression);
    }
  }

  public static @NlsSafe String render(@NotNull PsiExpression expression) {
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

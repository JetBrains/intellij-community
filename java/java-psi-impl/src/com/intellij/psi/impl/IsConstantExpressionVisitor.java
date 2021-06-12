// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.Map;

final class IsConstantExpressionVisitor extends JavaElementVisitor {
  private boolean myIsConstant;
  private final Map<PsiVariable, Boolean> varIsConst = new HashMap<>();

  public boolean isConstant() {
    return myIsConstant;
  }

  @Override
  public void visitExpression(PsiExpression expression) {
    myIsConstant = false;
  }

  @Override
  public void visitLiteralExpression(PsiLiteralExpression expression) {
    myIsConstant = !"null".equals(expression.getText());
  }

  @Override
  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    myIsConstant = true;
  }

  @Override
  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    PsiExpression expr = expression.getExpression();
    if (expr != null) {
      expr.accept(this);
    }
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    PsiExpression operand = expression.getOperand();
    if (operand == null) {
      myIsConstant = false;
      return;
    }
    operand.accept(this);
    if (!myIsConstant) return;
    PsiTypeElement element = expression.getCastType();
    if (element == null) {
      myIsConstant = false;
      return;
    }
    PsiType type = element.getType();
    if (type instanceof PsiPrimitiveType) return;
    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return;
    myIsConstant = false;
  }

  @Override
  public void visitPrefixExpression(PsiPrefixExpression expression) {
    PsiExpression operand = expression.getOperand();
    if (operand == null) {
      myIsConstant = false;
      return;
    }
    operand.accept(this);
    if (!myIsConstant) return;
    IElementType opType = expression.getOperationTokenType();
    if (opType == JavaTokenType.PLUS || opType == JavaTokenType.MINUS || opType == JavaTokenType.TILDE || opType == JavaTokenType.EXCL) {
      return;
    }
    myIsConstant = false;
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    for (PsiExpression operand : expression.getOperands()) {
      operand.accept(this);
      if (!myIsConstant) return;
      final PsiType type = operand.getType();
      if (type != null && !(type instanceof PsiPrimitiveType) && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        myIsConstant = false;
        return;
      }
    }
  }

  @Override
  public void visitConditionalExpression(PsiConditionalExpression expression) {
    PsiExpression thenExpr = expression.getThenExpression();
    PsiExpression elseExpr = expression.getElseExpression();
    if (thenExpr == null || elseExpr == null) {
      myIsConstant = false;
      return;
    }

    expression.getCondition().accept(this);
    if (!myIsConstant) return;
    thenExpr.accept(this);
    if (!myIsConstant) return;
    elseExpr.accept(this);
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null && !(qualifierExpression instanceof PsiReferenceExpression)) {
      myIsConstant = false;
      return;
    }
    PsiElement refElement = expression.resolve();
    if (!(refElement instanceof PsiVariable)) {
      myIsConstant = false;
      return;
    }
    PsiVariable variable = (PsiVariable)refElement;
    Boolean isConst = varIsConst.get(variable);
    if (isConst != null) {
      myIsConstant &= isConst.booleanValue();
      return;
    }
    if (variable instanceof PsiEnumConstant) {
      myIsConstant = true;
      varIsConst.put(variable, Boolean.TRUE);
      return;
    }
    varIsConst.put(variable, Boolean.FALSE);
    if (!variable.hasModifierProperty(PsiModifier.FINAL)){
      myIsConstant = false;
      return;
    }
    PsiExpression initializer = PsiFieldImpl.getDetachedInitializer(variable);
    if (initializer == null){
      myIsConstant = false;
      return;
    }
    initializer.accept(this);
    varIsConst.put(variable, myIsConstant);
  }
}

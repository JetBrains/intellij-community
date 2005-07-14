/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashMap;

import java.util.Map;

public class IsConstantExpressionVisitor extends PsiElementVisitor {
  protected boolean myIsConstant;
  private final Map<PsiVariable, Boolean> varIsConst = new THashMap<PsiVariable, Boolean>();

  public boolean isConstant() {
    return myIsConstant;
  }

  public void visitExpression(PsiExpression expression) {
    myIsConstant = false;
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    myIsConstant = true;
  }

  /**/public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    myIsConstant = true;
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    PsiExpression expr = expression.getExpression();
    if (expr != null){
      expr.accept(this);
    }
  }

  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    PsiExpression operand = expression.getOperand();
    if (operand == null){
      myIsConstant = false;
      return;
    }
    operand.accept(this);
    if (!myIsConstant) return;
    PsiType type = expression.getCastType().getType();
    if (type instanceof PsiPrimitiveType) return;
    if (type.equalsToText("java.lang.String")) return;
    myIsConstant = false;
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    PsiExpression operand = expression.getOperand();
    if (operand == null){
      myIsConstant = false;
      return;
    }
    operand.accept(this);
    if (!myIsConstant) return;
    IElementType opType = expression.getOperationSign().getTokenType();
    if (opType == JavaTokenType.PLUS || opType == JavaTokenType.MINUS || opType == JavaTokenType.TILDE || opType == JavaTokenType.EXCL) {
      return;
    }
    myIsConstant = false;
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    expression.getLOperand().accept(this);
    if (!myIsConstant) return;
    PsiExpression rOperand = expression.getROperand();
    if (rOperand != null){
      rOperand.accept(this);
    }
  }

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

  public void visitReferenceExpression(PsiReferenceExpression expression) {
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
    if (!variable.hasInitializer()){
      myIsConstant = false;
      return;
    }
    PsiExpression initializer = variable.getInitializer();
    initializer.accept(this);
    varIsConst.put(variable, Boolean.valueOf(myIsConstant));
  }
}

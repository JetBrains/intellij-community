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
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashMap;

import java.util.Map;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class IsConstantExpressionVisitor extends JavaElementVisitor {
  protected boolean myIsConstant;
  private final Map<PsiVariable, Boolean> varIsConst = new THashMap<PsiVariable, Boolean>();

  public boolean isConstant() {
    return myIsConstant;
  }

  @Override public void visitExpression(PsiExpression expression) {
    myIsConstant = false;
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
    myIsConstant = !"null".equals(expression.getText());
  }

  @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    myIsConstant = true;
  }

  @Override public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    PsiExpression expr = expression.getExpression();
    if (expr != null){
      expr.accept(this);
    }
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    PsiExpression operand = expression.getOperand();
    if (operand == null){
      myIsConstant = false;
      return;
    }
    operand.accept(this);
    if (!myIsConstant) return;
    PsiTypeElement element = expression.getCastType();
    if (element == null){
      myIsConstant = false;
      return;
    }
    PsiType type = element.getType();
    if (type instanceof PsiPrimitiveType) return;
    if (type.equalsToText(JAVA_LANG_STRING)) return;
    myIsConstant = false;
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    PsiExpression operand = expression.getOperand();
    if (operand == null){
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
      if (type != null && !(type instanceof PsiPrimitiveType) && !type.equalsToText(JAVA_LANG_STRING)) {
        myIsConstant = false;
        return;
      }
    }
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
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

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
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
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null){
      myIsConstant = false;
      return;
    }
    initializer.accept(this);
    varIsConst.put(variable, Boolean.valueOf(myIsConstant));
  }
}

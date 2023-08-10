// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class ClsBinaryExpressionImpl extends ClsElementImpl implements PsiBinaryExpression {
  private final ClsElementImpl myParent;
  private final PsiJavaToken myOperator;
  private final PsiExpression myLOperand;
  private final PsiExpression myROperand;

  ClsBinaryExpressionImpl(ClsElementImpl parent, PsiJavaToken sign, PsiExpression left, PsiExpression right) {
    myParent = parent;
    myOperator = new ClsJavaTokenImpl(this, sign.getTokenType(), sign.getText());
    myLOperand = ClsParsingUtil.psiToClsExpression(left, this);
    myROperand = ClsParsingUtil.psiToClsExpression(right, this);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(getText());
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
  }

  @Override
  public String getText() {
    return StringUtil.join(myLOperand.getText(), " ", myOperator.getText(), " ", myROperand.getText());
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return new PsiElement[]{myLOperand, myOperator, myROperand};
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitBinaryExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  @Override
  public PsiExpression getLOperand() {
    return myLOperand;
  }

  @NotNull
  @Override
  public PsiExpression getROperand() {
    return myROperand;
  }

  @NotNull
  @Override
  public PsiJavaToken getOperationSign() {
    return myOperator;
  }

  @NotNull
  @Override
  public IElementType getOperationTokenType() {
    return myOperator.getTokenType();
  }

  @Override
  public PsiJavaToken getTokenBeforeOperand(@NotNull PsiExpression operand) {
    return getOperationSign();
  }

  @Override
  public PsiType getType() {
    return myLOperand.getType();
  }

  @Override
  public PsiExpression @NotNull [] getOperands() {
    return new PsiExpression[]{getLOperand(), getROperand()};
  }

  @Override
  public String toString() {
    return "PsiBinaryExpression:" + getText();
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiPostfixExpressionImpl extends ExpressionPsiElement implements PsiPostfixExpression {
  private static final Logger LOG = Logger.getInstance(PsiPostfixExpressionImpl.class);

  public PsiPostfixExpressionImpl() {
    super(JavaElementType.POSTFIX_EXPRESSION);
  }

  @Override
  public @NotNull PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  @Override
  public @NotNull PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  @Override
  public @NotNull IElementType getOperationTokenType() {
    return getOperationSign().getTokenType();
  }

  @Override
  public PsiType getType() {
    return getOperand().getType();
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.OPERAND:
        return getFirstChildNode();

      case ChildRole.OPERATION_SIGN:
        return getLastChildNode();
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child == getFirstChildNode()) return ChildRole.OPERAND;
    if (child == getLastChildNode()) return ChildRole.OPERATION_SIGN;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPostfixExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiPostfixExpression:" + getText();
  }
}


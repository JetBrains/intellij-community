// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiTypeCastExpressionImpl extends ExpressionPsiElement implements PsiTypeCastExpression, Constants {
  private static final Logger LOG = Logger.getInstance(PsiTypeCastExpressionImpl.class);

  public PsiTypeCastExpressionImpl() {
    super(TYPE_CAST_EXPRESSION);
  }

  @Override
  public PsiTypeElement getCastType() {
    return (PsiTypeElement)findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @Override
  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  @Override
  public @Nullable PsiType getType() {
    final PsiTypeElement castType = getCastType();
    if (castType == null) return null;
    return PsiUtil.captureToplevelWildcards(castType.getType(), this);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.TYPE:
        return findChildByType(TYPE);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.OPERAND:
        return findChildByType(EXPRESSION_BIT_SET);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    assert child.getTreeParent() == this: "child:"+child+"; child.getTreeParent():"+child.getTreeParent();
    IElementType i = child.getElementType();
    if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == TYPE) {
      return ChildRole.TYPE;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.OPERAND;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeCastExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiTypeCastExpression:" + getText();
  }
}


// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class PsiAssignmentExpressionImpl extends ExpressionPsiElement implements PsiAssignmentExpression {
  private static final Logger LOG = Logger.getInstance(PsiAssignmentExpressionImpl.class);

  public PsiAssignmentExpressionImpl() {
    super(JavaElementType.ASSIGNMENT_EXPRESSION);
  }

  @Override
  public @NotNull PsiExpression getLExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.LOPERAND);
  }

  @Override
  public PsiExpression getRExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ROPERAND);
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
    //15.26.1 left side must be variable/array access probably wrapped in parenthesis, otherwise it's an invalid expression
    //because assignment expression itself is not a poly expression, its type may be calculated at any time
    //thus it's important to ensure that type of left side is not calculated for invalid expression, e.g. bar() = ""
    PsiExpression lExpression = PsiUtil.deparenthesizeExpression(getLExpression());
    if (lExpression instanceof PsiReferenceExpression || lExpression instanceof PsiArrayAccessExpression) {
      PsiType type = lExpression.getType();
      if (type == null || type == PsiTypes.nullType()) {
        PsiExpression rExpression = getRExpression();
        if (rExpression != null && !PsiPolyExpressionUtil.isPolyExpression(rExpression)) {
          return rExpression.getType();
        }
      }
      return type;
    }
    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.LOPERAND:
        return getFirstChildNode();

      case ChildRole.ROPERAND:
        return ElementType.EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;

      case ChildRole.OPERATION_SIGN:
        return findChildByType(OUR_OPERATIONS_BIT_SET);

      default:
        return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
      if (child == getFirstChildNode()) return ChildRole.LOPERAND;
      if (child == getLastChildNode()) return ChildRole.ROPERAND;
      return ChildRoleBase.NONE;
    }
    else if (OUR_OPERATIONS_BIT_SET.contains(child.getElementType())) {
      return ChildRole.OPERATION_SIGN;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  private static final TokenSet OUR_OPERATIONS_BIT_SET = JavaBinaryOperations.ASSIGNMENT_OPS;

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAssignmentExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiAssignmentExpression:" + getText();
  }
}


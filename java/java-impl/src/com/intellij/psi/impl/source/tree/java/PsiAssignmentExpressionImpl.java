package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class PsiAssignmentExpressionImpl extends ExpressionPsiElement implements PsiAssignmentExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl");

  public PsiAssignmentExpressionImpl() {
    super(JavaElementType.ASSIGNMENT_EXPRESSION);
  }

  @NotNull
  public PsiExpression getLExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.LOPERAND);
  }

  public PsiExpression getRExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ROPERAND);
  }

  @NotNull
  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  @NotNull
  public IElementType getOperationTokenType() {
    return getOperationSign().getTokenType();
  }

  public PsiType getType() {
    return getLExpression().getType();
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LOPERAND:
        return getFirstChildNode();

      case ChildRole.ROPERAND:
        return ElementType.EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;

      case ChildRole.OPERATION_SIGN:
        return findChildByType(OUR_OPERATIONS_BIT_SET);
    }
  }

  public int getChildRole(ASTNode child) {
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

  private static final TokenSet OUR_OPERATIONS_BIT_SET = TokenSet.create(JavaTokenType.EQ, JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ,
                                                                         JavaTokenType.PERCEQ, JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ,
                                                                         JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ, JavaTokenType.GTGTGTEQ,
                                                                         JavaTokenType.ANDEQ, JavaTokenType.OREQ, JavaTokenType.XOREQ);

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAssignmentExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiAssignmentExpression:" + getText();
  }
}


// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiBreakStatementImpl extends CompositePsiElement implements PsiBreakStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiBreakStatementImpl");

  public PsiBreakStatementImpl() {
    super(JavaElementType.BREAK_STATEMENT);
  }

  @Override
  public PsiReferenceExpression getLabelExpression() {
    PsiExpression expression = getExpression();
    boolean isLabel = PsiImplUtil.isUnqualifiedReference(expression) && !(PsiImplUtil.findEnclosingSwitchOrLoop(this) instanceof PsiSwitchExpression);
    return isLabel ? (PsiReferenceExpression)expression : null;
  }

  @Override
  public PsiExpression getValueExpression() {
    PsiExpression expression = getExpression();
    boolean isValue = expression != null && PsiImplUtil.findEnclosingSwitchOrLoop(this) instanceof PsiSwitchExpression;
    return isValue ? expression : null;
  }

  @Override
  public PsiExpression getExpression() {
    return (PsiExpression)findPsiChildByType(ElementType.EXPRESSION_BIT_SET);
  }

  @Override
  public PsiElement findExitedElement() {
    PsiElement enclosing = PsiImplUtil.findEnclosingSwitchOrLoop(this);
    PsiExpression expression = getExpression();

    if (enclosing instanceof PsiSwitchExpression || !PsiImplUtil.isUnqualifiedReference(expression)) {
      return enclosing;
    }

    PsiLabeledStatement labeled = PsiImplUtil.findEnclosingLabeledStatement(this, expression.getText());
    if (labeled != null) {
      return labeled.getStatement();
    }

    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.BREAK_KEYWORD: return findChildByType(JavaTokenType.BREAK_KEYWORD);
      case ChildRole.LABEL: return findChildByType(ElementType.EXPRESSION_BIT_SET);
      case ChildRole.CLOSING_SEMICOLON: return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
      default: return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.BREAK_KEYWORD) return ChildRole.BREAK_KEYWORD;
    if (ElementType.EXPRESSION_BIT_SET.contains(i)) return ChildRole.LABEL;
    if (i == JavaTokenType.SEMICOLON) return ChildRole.CLOSING_SEMICOLON;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitBreakStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiBreakStatement";
  }
}
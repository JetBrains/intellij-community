// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionStatementImpl extends CompositePsiElement implements PsiExpressionStatement {
  private static final Logger LOG = Logger.getInstance(PsiExpressionStatementImpl.class);

  public PsiExpressionStatementImpl() {
    super(JavaElementType.EXPRESSION_STATEMENT);
  }

  @Override
  public @NotNull PsiExpression getExpression() {
    PsiExpression expression = (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(findChildByType(ElementType.EXPRESSION_BIT_SET));
    if (expression != null) return expression;
    LOG.error("Illegal PSI: \n" + DebugUtil.psiToString(getParent(), true));
    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.EXPRESSION:
        return findChildByType(ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpressionStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiExpressionStatement";
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.EXPRESSION) {
      getTreeParent().deleteChildInternal(this);
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  @Override
  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType()) &&
        !ElementType.EXPRESSION_BIT_SET.contains(newElement.getElementType())) {
      throw new IncorrectOperationException("Expression expected; got: " + newElement.getElementType());
    }
    super.replaceChildInternal(child, newElement);
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiSwitchLabelStatementImpl extends PsiSwitchLabelStatementBaseImpl implements PsiSwitchLabelStatement {
  private static final Logger LOG = Logger.getInstance(PsiSwitchLabelStatementImpl.class);

  public PsiSwitchLabelStatementImpl() {
    super(JavaElementType.SWITCH_LABEL_STATEMENT);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.CASE_KEYWORD: return findChildByType(JavaTokenType.CASE_KEYWORD);
      case ChildRole.DEFAULT_KEYWORD: return findChildByType(JavaTokenType.DEFAULT_KEYWORD);
      case ChildRole.CASE_EXPRESSION: return findChildByType(ElementType.EXPRESSION_BIT_SET);
      case ChildRole.COLON: return findChildByType(JavaTokenType.COLON);
      default: return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.CASE_KEYWORD) return ChildRole.CASE_KEYWORD;
    if (i == JavaTokenType.DEFAULT_KEYWORD) return ChildRole.DEFAULT_KEYWORD;
    if (i == JavaTokenType.COLON) return ChildRole.COLON;
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) return ChildRole.CASE_EXPRESSION;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchLabelStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSwitchLabelStatement";
  }
}
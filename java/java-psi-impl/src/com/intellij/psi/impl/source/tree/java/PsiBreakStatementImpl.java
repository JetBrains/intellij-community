// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiBreakStatementImpl extends CompositePsiElement implements PsiBreakStatement {
  private static final Logger LOG = Logger.getInstance(PsiBreakStatementImpl.class);

  public PsiBreakStatementImpl() {
    super(JavaElementType.BREAK_STATEMENT);
  }

  @Override
  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findPsiChildByType(JavaTokenType.IDENTIFIER);
  }

  @Nullable
  @Override
  public PsiStatement findExitedStatement() {
    PsiIdentifier label = getLabelIdentifier();
    if (label != null) {
      PsiLabeledStatement labeled = PsiImplUtil.findEnclosingLabeledStatement(this, label.getText());
      return labeled != null ? labeled.getStatement() : null;
    }
    else {
      return PsiImplUtil.findEnclosingSwitchOrLoop(this);
    }
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
  public PsiReference getReference() {
    PsiIdentifier label = getLabelIdentifier();
    return label != null ? new PsiLabelReference(this, label) : null;
  }

  @Override
  public String toString() {
    return "PsiBreakStatement";
  }
}
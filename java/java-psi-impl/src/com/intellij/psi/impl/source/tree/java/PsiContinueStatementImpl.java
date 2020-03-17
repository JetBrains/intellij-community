// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiContinueStatementImpl extends CompositePsiElement implements PsiContinueStatement {
  private static final Logger LOG = Logger.getInstance(PsiContinueStatementImpl.class);

  public PsiContinueStatementImpl() {
    super(JavaElementType.CONTINUE_STATEMENT);
  }

  @Override
  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findPsiChildByType(JavaTokenType.IDENTIFIER);
  }

  @Override
  public PsiStatement findContinuedStatement() {
    PsiIdentifier label = getLabelIdentifier();
    if (label != null) {
      PsiLabeledStatement labeled = PsiImplUtil.findEnclosingLabeledStatement(this, label.getText());
      return labeled != null ? labeled.getStatement() : null;
    }
    else {
      return PsiImplUtil.findEnclosingLoop(this);
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.CONTINUE_KEYWORD: return findChildByType(JavaTokenType.CONTINUE_KEYWORD);
      case ChildRole.LABEL: return findChildByType(JavaTokenType.IDENTIFIER);
      case ChildRole.CLOSING_SEMICOLON: return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
      default: return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.CONTINUE_KEYWORD) return ChildRole.CONTINUE_KEYWORD;
    if (i == JavaTokenType.IDENTIFIER) return ChildRole.LABEL;
    if (i == JavaTokenType.SEMICOLON) return ChildRole.CLOSING_SEMICOLON;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitContinueStatement(this);
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
    return "PsiContinueStatement";
  }
}
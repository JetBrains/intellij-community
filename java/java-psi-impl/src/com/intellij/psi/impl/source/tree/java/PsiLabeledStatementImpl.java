// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiLabeledStatementImpl extends CompositePsiElement implements PsiLabeledStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiLabeledStatementImpl.class);

  public PsiLabeledStatementImpl() {
    super(LABELED_STATEMENT);
  }

  @Override
  public @NotNull PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL_NAME);
  }

  @Override
  public PsiStatement getStatement() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.STATEMENT);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.STATEMENT:
        return PsiImplUtil.findStatementChild(this);

      case ChildRole.COLON:
        return findChildByType(COLON);

      case ChildRole.LABEL_NAME:
        return getFirstChildNode();
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IDENTIFIER) {
      return ChildRole.LABEL_NAME;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else {
      if (child.getPsi() instanceof PsiStatement) {
        return ChildRole.STATEMENT;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLabeledStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiLabeledStatement";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (lastParent == null || lastParent.getParent() != this){
      PsiElement[] children = getChildren();
      for (PsiElement aChildren : children) {
        if (!aChildren.processDeclarations(processor, state, null, place)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public @NotNull String getName() {
    return getLabelIdentifier().getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getLabelIdentifier(), name);
    return this;
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return new LocalSearchScope(this);
  }

  @Override
  public @Nullable PsiElement getNameIdentifier() {
    return getLabelIdentifier();
  }
}

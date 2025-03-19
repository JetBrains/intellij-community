// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class PsiBlockStatementImpl extends CompositePsiElement implements PsiBlockStatement {
  private static final Logger LOG = Logger.getInstance(PsiBlockStatementImpl.class);

  public PsiBlockStatementImpl() {
    super(JavaElementType.BLOCK_STATEMENT);
  }

  @Override
  public @NotNull PsiCodeBlock getCodeBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.BLOCK);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    if (role == ChildRole.BLOCK) {
      return findChildByType(JavaElementType.CODE_BLOCK);
    }
    return null;
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == JavaElementType.CODE_BLOCK) {
      return ChildRole.BLOCK;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitBlockStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiBlockStatement";
  }
}

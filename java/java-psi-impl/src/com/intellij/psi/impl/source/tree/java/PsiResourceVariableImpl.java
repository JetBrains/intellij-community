// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiResourceVariableImpl extends PsiLocalVariableImpl implements PsiResourceVariable {
  public PsiResourceVariableImpl() {
    super(JavaElementType.RESOURCE_VARIABLE);
  }

  @Override
  public PsiElement @NotNull [] getDeclarationScope() {
    final PsiResourceList resourceList = (PsiResourceList)getParent();
    final PsiTryStatement tryStatement = (PsiTryStatement)resourceList.getParent();
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    return tryBlock != null ? new PsiElement[]{resourceList, tryBlock} : new PsiElement[]{resourceList};
  }

  @Override
  public @NotNull PsiTypeElement getTypeElement() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiTypeElement.class);
  }

  @Override
  public PsiModifierList getModifierList() {
    return PsiTreeUtil.getChildOfType(this, PsiModifierList.class);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    final PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(this);
    if (PsiUtil.isJavaToken(next, JavaTokenType.SEMICOLON)) {
      getParent().deleteChildRange(this, next);
      return;
    }

    final PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(this);
    if (PsiUtil.isJavaToken(prev, JavaTokenType.SEMICOLON)) {
      getParent().deleteChildRange(prev, this);
      return;
    }

    super.delete();
  }

  @Override
  public void accept(final @NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitResourceVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public String toString() {
    return "PsiResourceVariable:" + getName();
  }
}

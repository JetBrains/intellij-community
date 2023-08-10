// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import org.jetbrains.annotations.NotNull;

public class ASTWrapperPsiElement extends ASTDelegatePsiElement {
  private final ASTNode myNode;

  public ASTWrapperPsiElement(final @NotNull ASTNode node) {
    myNode = node;
  }

  @Override
  public PsiElement getParent() {
    return SharedImplUtil.getParent(getNode());
  }

  @Override
  public @NotNull ASTNode getNode() {
    return myNode;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myNode.getElementType() + ")";
  }
}

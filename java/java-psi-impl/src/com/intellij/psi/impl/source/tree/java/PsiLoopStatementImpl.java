// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class PsiLoopStatementImpl extends CompositePsiElement implements PsiLoopStatement {
  protected PsiLoopStatementImpl(IElementType type) {
    super(type);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child == getBody()) {
      replaceChildInternal(child, (TreeElement)JavaPsiFacade.getElementFactory(getProject()).createStatementFromText("{}", null));
    } else {
      super.deleteChildInternal(child);
    }
  }
}

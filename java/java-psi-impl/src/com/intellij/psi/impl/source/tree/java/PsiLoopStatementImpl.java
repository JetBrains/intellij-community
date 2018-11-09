// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

abstract class PsiLoopStatementImpl extends CompositePsiElement implements PsiLoopStatement {
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

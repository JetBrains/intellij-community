// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import org.jetbrains.annotations.NotNull;

final class AstRef extends SubstrateRef {

  private final @NotNull ASTNode myNode;

  AstRef(@NotNull ASTNode node) { myNode = node; }

  @Override
  public @NotNull ASTNode getNode() {
    return myNode;
  }

  @Override
  public boolean isValid() {
    FileASTNode fileElement = SharedImplUtil.findFileElement(myNode);
    PsiElement file = fileElement == null ? null : fileElement.getPsi();
    return file != null && file.isValid();
  }

  @Override
  public @NotNull PsiFile getContainingFile() {
    PsiFile file = SharedImplUtil.getContainingFile(myNode);
    if (file == null) throw PsiInvalidElementAccessException.createByNode(myNode, null);
    return file;
  }
}

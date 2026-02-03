// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.JShellFileImpl;
import com.intellij.psi.impl.source.tree.IJShellElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellParserDefinition extends JavaParserDefinition {
  public static final IFileElementType FILE_ELEMENT_TYPE = new JShellFileElementType();

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new JShellFileImpl(viewProvider);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE_ELEMENT_TYPE;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof IJShellElementType) {
      return ((IJShellElementType)type).createPsi(node);
    }
    return super.createElement(node);
  }
}

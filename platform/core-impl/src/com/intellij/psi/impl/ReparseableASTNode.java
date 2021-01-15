// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ReparseableASTNode {
  void applyReplaceOnReparse(@NotNull ASTNode newChild);

  default void applyDeleteOnReparse(@NotNull ASTNode oldChild) {
    throw new UnsupportedOperationException();
  }

  default void applyInsertOnReparse(@NotNull ASTNode newChild, ASTNode anchor) {
    throw new UnsupportedOperationException();
  }

  default void applyReplaceFileOnReparse(@NotNull PsiFile psiFile, @NotNull FileASTNode newNode) {
    throw new UnsupportedOperationException();
  }

}

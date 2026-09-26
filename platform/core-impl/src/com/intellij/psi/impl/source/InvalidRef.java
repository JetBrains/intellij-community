// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import org.jetbrains.annotations.NotNull;

final class InvalidRef extends SubstrateRef {
  private final @NotNull StubBasedPsiElementBase<?> myPsi;

  InvalidRef(@NotNull StubBasedPsiElementBase<?> psi) { myPsi = psi; }

  @Override
  public @NotNull ASTNode getNode() {
    throw new PsiInvalidElementAccessException(myPsi);
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public @NotNull PsiFile getContainingFile() {
    throw new PsiInvalidElementAccessException(myPsi);
  }
}

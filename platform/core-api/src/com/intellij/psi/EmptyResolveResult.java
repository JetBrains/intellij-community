// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

public final class EmptyResolveResult implements ResolveResult {

  public static final ResolveResult INSTANCE = new EmptyResolveResult();

  private EmptyResolveResult() {}

  @Override
  public @Nullable PsiElement getElement() {
    return null;
  }

  @Override
  public boolean isValidResult() {
    return false;
  }
}

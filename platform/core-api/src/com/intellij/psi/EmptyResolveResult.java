// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

public final class EmptyResolveResult implements ResolveResult {

  public static final ResolveResult INSTANCE = new EmptyResolveResult();

  private EmptyResolveResult() {}

  @Nullable
  @Override
  public PsiElement getElement() {
    return null;
  }

  @Override
  public boolean isValidResult() {
    return false;
  }
}

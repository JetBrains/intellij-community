// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FalseFilter implements ElementFilter {
  public static final FalseFilter INSTANCE = new FalseFilter();

  private FalseFilter() {}

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    return false;
  }

  @Override
  public String toString() {
    return "false";
  }
}
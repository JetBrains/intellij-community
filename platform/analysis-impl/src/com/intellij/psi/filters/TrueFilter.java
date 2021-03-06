// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;

public final class TrueFilter implements ElementFilter {
  public static final ElementFilter INSTANCE = new TrueFilter();

  private TrueFilter() { }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    return true;
  }

  @Override
  public String toString() {
    return "true";
  }
}
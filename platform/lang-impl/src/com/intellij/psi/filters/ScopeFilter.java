// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.position.PositionElementFilter;

public final class ScopeFilter extends PositionElementFilter {
  public ScopeFilter() { }

  public ScopeFilter(ElementFilter filter) {
    setFilter(filter);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    return context != null && getFilter().isAcceptable(context, context);
  }

  @Override
  public String toString() {
    return "scope(" + getFilter() + ")";
  }
}
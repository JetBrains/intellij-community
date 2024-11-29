// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.position;

import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.ObjectPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

public class FilterPattern extends ObjectPattern<Object,FilterPattern> {
  private final @Nullable ElementFilter myFilter;

  public FilterPattern(final @Nullable ElementFilter filter) {
    super(new InitialPatternCondition<Object>(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return filter == null ||
               o != null &&
               filter.isClassAcceptable(o.getClass()) &&
               filter.isAcceptable(o, o instanceof PsiElement ? (PsiElement)o : null);
      }
    });
    myFilter = filter;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof FilterPattern)) return false;

    final FilterPattern that = (FilterPattern)o;

    if (myFilter != null ? !myFilter.equals(that.myFilter) : that.myFilter != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFilter != null ? myFilter.hashCode() : 0;
  }

  @Override
  public String toString() {
    return super.toString() + " & " + myFilter;
  }
}

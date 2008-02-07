/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.filters.position;

import com.intellij.patterns.ObjectPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.util.ProcessingContext;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class FilterPattern extends ObjectPattern<Object,FilterPattern> {
  @Nullable private final ElementFilter myFilter;

  public FilterPattern(@Nullable final ElementFilter filter) {
    super(new InitialPatternCondition<Object>(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return filter == null ||
               o != null &&
               filter.isClassAcceptable(o.getClass()) &&
               filter.isAcceptable(o, o instanceof PsiElement ? (PsiElement)o : null);
      }
    });
    myFilter = filter;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof FilterPattern)) return false;

    final FilterPattern that = (FilterPattern)o;

    if (myFilter != null ? !myFilter.equals(that.myFilter) : that.myFilter != null) return false;

    return true;
  }

  public int hashCode() {
    return (myFilter != null ? myFilter.hashCode() : 0);
  }
}

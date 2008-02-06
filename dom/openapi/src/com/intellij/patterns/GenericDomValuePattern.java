/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class GenericDomValuePattern<T> extends DomElementPattern<GenericDomValue<T>, GenericDomValuePattern<T>>{
  protected GenericDomValuePattern() {
    super(new InitialPatternCondition(GenericDomValue.class) {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof GenericDomValue;
      }
    });
  }

  protected GenericDomValuePattern(final Class<T> aClass) {
    super(new InitialPatternCondition(aClass) {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof GenericDomValue && aClass.equals(DomUtil.getGenericValueParameter(((GenericDomValue)o).getDomElementType()));
      }

    });
  }

  public GenericDomValuePattern<T> withStringValue(final ElementPattern pattern) {
    return with(new PatternCondition<GenericDomValue<T>>("withStringValue") {
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(genericDomValue.getStringValue(), matchingContext, traverseContext);
      }

    });
  }

  public GenericDomValuePattern<T> withValue(@NotNull final T value) {
    return withValue(StandardPatterns.object(value));
  }

  public GenericDomValuePattern<T> withValue(final ElementPattern pattern) {
    return with(new PatternCondition<GenericDomValue<T>>("withValue") {
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(genericDomValue.getValue(), matchingContext, traverseContext);
      }
    });
  }
}

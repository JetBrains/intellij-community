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
    super(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof GenericDomValue;
      }

      public String toString() {
        return "genericDomValue()";
      }

    });
  }

  protected GenericDomValuePattern(final Class<T> aClass) {
    super(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof GenericDomValue && aClass.equals(DomUtil.getGenericValueParameter(((GenericDomValue)o).getDomElementType()));
      }

      public String toString() {
        return "genericDomValue(" + aClass.getName() + ")";
      }
    });
  }

  public GenericDomValuePattern<T> withStringValue(final ElementPattern pattern) {
    return with(new PatternCondition<GenericDomValue<T>>() {
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(genericDomValue.getStringValue(), matchingContext, traverseContext);
      }

      public String toString() {
        return "withStringValue(" + pattern.toString() + ")";
      }
    });
  }

  public GenericDomValuePattern<T> withValue(@NotNull final T value) {
    return withValue(StandardPatterns.object(value));
  }

  public GenericDomValuePattern<T> withValue(final ElementPattern pattern) {
    return with(new PatternCondition<GenericDomValue<T>>() {
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(genericDomValue.getValue(), matchingContext, traverseContext);
      }

      public String toString() {
        return "withValue(" + pattern.toString() + ")";
      }
    });
  }
}

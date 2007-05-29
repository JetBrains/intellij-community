/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public abstract class WrappingConverter extends Converter<Object> {
  public Object fromString(@Nullable @NonNls String s, final ConvertContext context) {
    final Converter converter = getConverter((GenericDomValue)context.getInvocationElement());
    if (converter != null) {
      return converter.fromString(s, context);
    }
    else {
      return s;
    }
  }

  public String toString(@Nullable Object t, final ConvertContext context) {
    final Converter converter = getConverter((GenericDomValue)context.getInvocationElement());
    if (converter != null) {
      return converter.toString(t, context);
    }
    else {
      return String.valueOf(t);
    }
  }

  @Nullable
  public abstract Converter<?> getConverter(@NotNull final GenericDomValue domElement);

  public static Converter getDeepestConverter(final Converter converter, final GenericDomValue domValue) {
    Converter cur = converter;
    for (Converter next; cur instanceof WrappingConverter; cur = next) {
      next = ((WrappingConverter)cur).getConverter(domValue);
      if (next == null) break;
    }
    return cur;
  }
}

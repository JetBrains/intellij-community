// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public abstract class ReducingParser extends TokenParser{
  @Override
  public final boolean parseToken(final PrattBuilder builder) {
    builder.advance();
    final IElementType type = parseFurther(builder);
    if (type == null) return false;

    builder.reduce(type);
    return true;
  }

  public abstract @Nullable IElementType parseFurther(final PrattBuilder builder);
}

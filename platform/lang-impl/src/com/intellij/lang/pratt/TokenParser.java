// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TokenParser {

  public abstract boolean parseToken(PrattBuilder builder);

  public static TokenParser infix(final int rightPriority, final @NotNull IElementType compositeType) {
    return infix(rightPriority, compositeType, null);
  }

  public static TokenParser infix(final int rightPriority, final @NotNull IElementType compositeType, final @Nullable String errorMessage) {
    return new ReducingParser() {
      @Override
      public @NotNull IElementType parseFurther(final PrattBuilder builder) {
        builder.createChildBuilder(rightPriority, errorMessage).parse();
        return compositeType;
      }
    };
  }

  public static TokenParser postfix(final @NotNull IElementType compositeType) {
    return new ReducingParser() {
      @Override
      public @NotNull IElementType parseFurther(final PrattBuilder builder) {
        return compositeType;
      }
    };
  }

}

/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class TokenParser {
  public static final TokenParser EMPTY = new TokenParser() {
    public boolean parseToken(final PrattBuilder builder) {
      builder.error(JavaErrorMessages.message("unexpected.token"));
      return false;
    }
  };

  public abstract boolean parseToken(PrattBuilder builder);

  public static TokenParser infix(final int rightPriority, @NotNull final IElementType compositeType) {
    return infix(rightPriority, compositeType, null);
  }

  public static TokenParser infix(final int rightPriority, @NotNull final IElementType compositeType, @Nullable final String errorMessage) {
    return new ReducingParser() {
      @Nullable
      public IElementType parseFurther(final PrattBuilder builder) {
        builder.parse(rightPriority, errorMessage);
        return compositeType;
      }
    };
  }

  public static TokenParser postfix(@NotNull final IElementType compositeType) {
    return new ReducingParser() {
      @Nullable
      public IElementType parseFurther(final PrattBuilder builder) {
        return compositeType;
      }
    };
  }

}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PrattParsingUtil {
  private PrattParsingUtil() {
  }

  public static void searchFor(PrattBuilder builder, PrattTokenType @NotNull ... types) {
    searchFor(builder, true, types);
  }

  public static boolean searchFor(final PrattBuilder builder, final boolean consume, final PrattTokenType... types) {
    final TokenSet set = TokenSet.create(types);
    if (!set.contains(builder.getTokenType())) {
      builder.assertToken(types[0]);
      while (!set.contains(builder.getTokenType()) && !builder.isEof()) {
        builder.advance();
      }
    }
    if (consume) {
      builder.advance();
    }
    return !builder.isEof();
  }

  public static @Nullable IElementType parseOption(final PrattBuilder builder, int rightPriority) {
    final MutableMarker marker = builder.mark();
    final IElementType type = builder.createChildBuilder(rightPriority).parse();
    if (type == null) {
      marker.rollback();
    } else {
      marker.finish();
    }
    return type;
  }
}

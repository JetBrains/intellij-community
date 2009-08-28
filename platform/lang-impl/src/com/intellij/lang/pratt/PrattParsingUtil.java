/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PrattParsingUtil {
  private PrattParsingUtil() {
  }

  public static void searchFor(PrattBuilder builder, @NotNull PrattTokenType... types) {
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

  @Nullable
  public static IElementType parseOption(final PrattBuilder builder, int rightPriority) {
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

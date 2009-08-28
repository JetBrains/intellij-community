/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class AppendTokenParser extends TokenParser {
  public static final AppendTokenParser JUST_APPEND = new AppendTokenParser() {
    @Nullable
    protected IElementType parseAppend(final PrattBuilder builder) {
      return null;
    }
  };

  public boolean parseToken(final PrattBuilder builder) {
    final MutableMarker marker = builder.mark();
    builder.advance();
    marker.finish(parseAppend(builder));
    return true;
  }

  @Nullable protected abstract IElementType parseAppend(PrattBuilder builder);
  
}

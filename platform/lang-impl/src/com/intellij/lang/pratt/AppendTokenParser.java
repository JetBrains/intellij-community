// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public abstract class AppendTokenParser extends TokenParser {
  public static final AppendTokenParser JUST_APPEND = new AppendTokenParser() {
    @Override
    protected @Nullable IElementType parseAppend(final PrattBuilder builder) {
      return null;
    }
  };

  @Override
  public boolean parseToken(final PrattBuilder builder) {
    final MutableMarker marker = builder.mark();
    builder.advance();
    marker.finish(parseAppend(builder));
    return true;
  }

  protected abstract @Nullable IElementType parseAppend(PrattBuilder builder);

}

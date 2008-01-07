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
public abstract class ReducingParser extends TokenParser{
  public final boolean parseToken(final PrattBuilder builder) {
    builder.advance();
    final IElementType type = parseFurther(builder);
    if (type == null) return false;
    
    builder.reduce(type);
    return true;
  }

  @Nullable public abstract IElementType parseFurther(final PrattBuilder builder);
}

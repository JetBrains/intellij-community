// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @see PlatformPatterns#elementType()
 */
public class IElementTypePattern extends ObjectPattern<IElementType, IElementTypePattern> {
  protected IElementTypePattern() {
    super(IElementType.class);
  }

  public IElementTypePattern or(final IElementType @NotNull ... types) {
    return tokenSet(TokenSet.create(types));
  }

  public IElementTypePattern tokenSet(final @NotNull TokenSet tokenSet) {
    return with(new PatternCondition<IElementType>("tokenSet") {
      @Override
      public boolean accepts(final @NotNull IElementType type, final ProcessingContext context) {
        return tokenSet.contains(type);
      }
    });
  }
}

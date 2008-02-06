/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class IElementTypePattern extends ObjectPattern<IElementType, IElementTypePattern> {
  protected IElementTypePattern() {
    super(IElementType.class);
  }

  public IElementTypePattern or(@NotNull final IElementType... types){
    return tokenSet(TokenSet.create(types));
  }

  public IElementTypePattern tokenSet(@NotNull final TokenSet tokenSet){
    return with(new PatternCondition<IElementType>("tokenSet") {
      public boolean accepts(@NotNull final IElementType type, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return tokenSet.contains(type);
      }
    });
  }

}

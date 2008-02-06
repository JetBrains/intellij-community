/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiTypePattern extends ObjectPattern<PsiType,PsiTypePattern> {
  protected PsiTypePattern() {
    super(PsiType.class);
  }

  public PsiTypePattern arrayOf(final ElementPattern pattern) {
    return with(new PatternCondition<PsiType>("arrayOf") {
      public boolean accepts(@NotNull final PsiType psiType, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return psiType instanceof PsiArrayType &&
               pattern.getCondition().accepts(((PsiArrayType)psiType).getComponentType(), matchingContext, traverseContext);
      }
    });
  }

  public PsiTypePattern classType(final ElementPattern pattern) {
    return with(new PatternCondition<PsiType>("classType") {
      public boolean accepts(@NotNull final PsiType psiType, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return psiType instanceof PsiClassType &&
               pattern.getCondition().accepts(((PsiClassType)psiType).resolve(), matchingContext, traverseContext);
      }
    });
  }
}

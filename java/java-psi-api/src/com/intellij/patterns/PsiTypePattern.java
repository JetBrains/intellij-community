// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class PsiTypePattern extends ObjectPattern<PsiType,PsiTypePattern> {
  protected PsiTypePattern() {
    super(PsiType.class);
  }

  public PsiTypePattern arrayOf(final ElementPattern pattern) {
    return with(new PatternCondition<PsiType>("arrayOf") {
      @Override
      public boolean accepts(final @NotNull PsiType psiType, final ProcessingContext context) {
        return psiType instanceof PsiArrayType &&
               pattern.accepts(((PsiArrayType)psiType).getComponentType(), context);
      }
    });
  }

  public PsiTypePattern classType(final ElementPattern<? extends PsiClass> pattern) {
    return with(new PatternCondition<PsiType>("classType") {
      @Override
      public boolean accepts(final @NotNull PsiType psiType, final ProcessingContext context) {
        return psiType instanceof PsiClassType &&
               pattern.accepts(((PsiClassType)psiType).resolve(), context);
      }
    });
  }
}

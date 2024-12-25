// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.PsiField;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

public class PsiFieldPattern extends PsiMemberPattern<PsiField, PsiFieldPattern>{
  public PsiFieldPattern() {
    super(new InitialPatternCondition<PsiField>(PsiField.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o instanceof PsiField;
      }
    });
  }
}

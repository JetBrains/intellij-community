// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.PsiField;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

public class PsiFieldPattern extends PsiMemberPattern<PsiField, PsiFieldPattern>{
  public PsiFieldPattern() {
    super(new InitialPatternCondition<PsiField>(PsiField.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof PsiField;
      }
    });
  }
}

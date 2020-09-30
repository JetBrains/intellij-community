// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.patterns;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class PsiNameValuePairPattern extends PsiElementPattern<PsiNameValuePair, PsiNameValuePairPattern> {
  static final PsiNameValuePairPattern NAME_VALUE_PAIR_PATTERN = new PsiNameValuePairPattern();

  private PsiNameValuePairPattern() {
    super(PsiNameValuePair.class);
  }

  @Override
  @NotNull
  public PsiNameValuePairPattern withName(@NotNull @NonNls final String requiredName) {
    return with(new PatternCondition<PsiNameValuePair>("withName") {
      @Override
      public boolean accepts(@NotNull final PsiNameValuePair psiNameValuePair, final ProcessingContext context) {
        String actualName = psiNameValuePair.getName();
        return requiredName.equals(actualName) || actualName == null && "value".equals(requiredName);
      }
    });
  }

  @NotNull
  @Override
  public PsiNameValuePairPattern withName(@NotNull final ElementPattern<String> name) {
    return with(new PsiNamePatternCondition<PsiNameValuePair>("withName", name) {
      @Override
      public String getPropertyValue(@NotNull Object o) {
        if (o instanceof PsiNameValuePair) {
          final String nameValue = ((PsiNameValuePair)o).getName();
          return StringUtil.notNullize(nameValue, "value");
        }
        return null;
      }
    });
  }
}

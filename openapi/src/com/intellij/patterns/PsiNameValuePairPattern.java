/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.patterns;

import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiNameValuePairPattern extends PsiElementPattern<PsiNameValuePair, PsiNameValuePairPattern> {
  protected PsiNameValuePairPattern() {
    super(PsiNameValuePair.class);
  }

  public PsiNameValuePairPattern withName(@NotNull @NonNls final String requiredName) {
    return with(new PatternCondition<PsiNameValuePair>() {
      public boolean accepts(@NotNull final PsiNameValuePair psiNameValuePair, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        String actualName = psiNameValuePair.getName();
        return requiredName.equals(actualName) || actualName == null && "value".equals(requiredName);
      }
    });
  }
}

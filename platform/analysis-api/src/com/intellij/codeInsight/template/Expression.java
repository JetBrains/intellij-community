// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Expression {

  public abstract @Nullable Result calculateResult(ExpressionContext context);

  public @Nullable Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  public abstract LookupElement @Nullable [] calculateLookupItems(ExpressionContext context);

  public @Nullable @NlsContexts.PopupAdvertisement String getAdvertisingText() {
    return null;
  }

  /**
   * @return true if {@link Expression#calculateResult} or {@link Expression#calculateQuickResult}
   * require committed PSI for their calculation or false otherwise
   */
  public boolean requiresCommittedPSI() {
    return true;
  }

  /**
   * @return focus degree to use for expression's lookup.
   * @see LookupFocusDegree
   */
  public @NotNull LookupFocusDegree getLookupFocusDegree() {
    return LookupFocusDegree.FOCUSED;
  }
}

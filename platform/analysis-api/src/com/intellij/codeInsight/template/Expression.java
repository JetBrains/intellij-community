// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Expression {
  @Nullable
  public abstract Result calculateResult(ExpressionContext context);

  @Nullable
  public abstract Result calculateQuickResult(ExpressionContext context);

  public abstract LookupElement @Nullable [] calculateLookupItems(ExpressionContext context);

  @Nullable
  public String getAdvertisingText() {
    return null;
  }

  /**
   * @return true if {@link Expression#calculateResult(ExpressionContext)} or
   *                 {@link Expression#calculateQuickResult(ExpressionContext)}
   *         require committed PSI for their calculation or false otherwise
   */
  public boolean requiresCommittedPSI() {
    return true;
  }

  /**
   * @return focus degree to use for expression's lookup.
   * @see LookupFocusDegree
   */
  @NotNull
  public LookupFocusDegree getLookupFocusDegree() {
    return LookupFocusDegree.FOCUSED;
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents guard in case label like {@code String s when s.string() > 10 }
 */
@ApiStatus.Experimental
public interface PsiPatternGuard extends PsiCaseLabelElement {
  /**
   * @return pattern which is being guarded
   */
  @NotNull
  PsiPattern getPattern();

  /**
   * @return expression which guards the pattern (after {@code when}) or null if pattern is incomplete
   */
  @Nullable
  PsiExpression getGuardingExpression();
}

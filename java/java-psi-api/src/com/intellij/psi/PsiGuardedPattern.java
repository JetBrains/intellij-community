// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents guarded pattern like {@code String s && s.length() > 10 }
 */
public interface PsiGuardedPattern extends PsiPattern {
  /**
   * @return pattern which is being guarded
   */
  @NotNull
  PsiPrimaryPattern getPrimaryPattern();

  /**
   * @return expression which guards the pattern (after {@code &&}) or null if pattern is incomplete
   */
  @Nullable
  PsiExpression getGuardingExpression();
}

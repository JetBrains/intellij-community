// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Pattern variable inside the PsiRecordPattern
 * <p>
 * Example: {@code Rec(int x) rec } - record variable here is {@code rec}
 */
public interface PsiRecordPatternVariable extends PsiPatternVariable {
  /**
   * @return pattern to which this variable belongs
   */
  @Override
  @NotNull PsiRecordPattern getPattern();
}

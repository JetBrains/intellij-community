// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

public interface PsiParenthesizedPattern extends PsiPrimaryPattern {
  /**
   * @return pattern inside parenthesis or null if pattern is incomplete
   */
  @Nullable
  PsiPattern getPattern();
}

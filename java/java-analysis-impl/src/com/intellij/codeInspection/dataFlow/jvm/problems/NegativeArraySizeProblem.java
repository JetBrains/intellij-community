// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.problems;

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class NegativeArraySizeProblem extends JvmDfaProblem<PsiExpression> {
  public NegativeArraySizeProblem(@NotNull PsiExpression anchor) {
    super(anchor);
  }
}

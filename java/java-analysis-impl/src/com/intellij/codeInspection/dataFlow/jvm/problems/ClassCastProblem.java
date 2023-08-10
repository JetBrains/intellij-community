// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.problems;

import com.intellij.psi.PsiTypeCastExpression;
import org.jetbrains.annotations.NotNull;

public class ClassCastProblem extends JvmDfaProblem<PsiTypeCastExpression> {
  public ClassCastProblem(@NotNull PsiTypeCastExpression anchor) {
    super(anchor);
  }
}

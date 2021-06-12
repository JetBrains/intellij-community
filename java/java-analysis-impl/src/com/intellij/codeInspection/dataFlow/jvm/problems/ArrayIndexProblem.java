// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.problems;

import com.intellij.psi.PsiArrayAccessExpression;
import org.jetbrains.annotations.NotNull;

public class ArrayIndexProblem extends JvmDfaProblem<PsiArrayAccessExpression> {
  public ArrayIndexProblem(@NotNull PsiArrayAccessExpression anchor) {
    super(anchor);
  }
}

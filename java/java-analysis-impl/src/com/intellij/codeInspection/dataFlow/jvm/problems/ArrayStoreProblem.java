// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.problems;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class ArrayStoreProblem extends JvmDfaProblem<PsiAssignmentExpression> {
  private final @NotNull PsiType myFrom;
  private final @NotNull PsiType myTo;

  public ArrayStoreProblem(@NotNull PsiAssignmentExpression anchor, @NotNull PsiType from, @NotNull PsiType to) {
    super(anchor);
    myFrom = from;
    myTo = to;
  }

  public @NotNull PsiType getFromType() {
    return myFrom;
  }

  public @NotNull PsiType getToType() {
    return myTo;
  }
}

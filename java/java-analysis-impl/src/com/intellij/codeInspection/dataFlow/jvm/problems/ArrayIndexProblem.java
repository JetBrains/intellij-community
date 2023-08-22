// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.problems;

import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.psi.PsiArrayAccessExpression;
import org.jetbrains.annotations.NotNull;

public class ArrayIndexProblem extends JvmDfaProblem<PsiArrayAccessExpression> implements IndexOutOfBoundsProblem {
  public ArrayIndexProblem(@NotNull PsiArrayAccessExpression anchor) {
    super(anchor);
  }

  @Override
  public @NotNull DerivedVariableDescriptor getLengthDescriptor() {
    return SpecialField.ARRAY_LENGTH;
  }
}

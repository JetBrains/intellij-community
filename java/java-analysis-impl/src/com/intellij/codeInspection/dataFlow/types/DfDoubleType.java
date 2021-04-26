// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface DfDoubleType extends DfFloatingPointType {
  Set<Double> DOUBLE_ZERO_SET = Set.of(0.0, -0.0);

  @NotNull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.DOUBLE;
  }
}

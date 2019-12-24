// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

class DfDoubleConstantType extends DfConstantType<Double> implements DfDoubleType {
  DfDoubleConstantType(double value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfDoubleType) return DfTypes.DOUBLE;
    return DfTypes.TOP;
  }

  @NotNull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfDoubleType.super.getPsiType();
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    return new DfDoubleNotValueType(Collections.singleton(getValue()));
  }
}

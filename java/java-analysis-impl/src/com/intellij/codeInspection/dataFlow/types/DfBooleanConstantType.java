// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

class DfBooleanConstantType extends DfConstantType<Boolean> implements DfBooleanType {
  DfBooleanConstantType(boolean value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other.equals(this)) return this;
    if (other instanceof DfBooleanType) return DfTypes.BOOLEAN;
    return DfTypes.TOP;
  }

  @NotNull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfBooleanType.super.getPsiType();
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    return getValue() ? DfTypes.FALSE : DfTypes.TRUE;
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

class DfFloatConstantType extends DfConstantType<Float> implements DfFloatType {
  DfFloatConstantType(float value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfFloatType) return DfTypes.FLOAT;
    return DfTypes.TOP;
  }

  @NotNull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfFloatType.super.getPsiType();
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    return new DfFloatNotValueType(Collections.singleton(getValue()));
  }
}

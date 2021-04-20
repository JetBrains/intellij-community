// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

class DfDoubleConstantType extends DfConstantType<Double> implements DfDoubleType {
  DfDoubleConstantType(double value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfDoubleType) return DfTypes.DOUBLE;
    return DfType.TOP;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    Double value = getValue();
    if (Double.isNaN(value)) {
      return relationType == RelationType.NE ? DfTypes.DOUBLE : BOTTOM;
    }
    switch (relationType) {
      case EQ:
        return value == 0.0 ? DfTypes.DOUBLE_ZERO : this;
      case LT:
      case GT:
        return new DfDoubleNotValueType(value == 0.0 ? Set.of(0.0, -0.0, Double.NaN) : Set.of(value, Double.NaN));
      case NE:
        return new DfDoubleNotValueType(value == 0.0 ? Set.of(0.0, -0.0) : Set.of(value));
      case LE:
      case GE:
        return new DfDoubleNotValueType(Set.of(Double.NaN));
      default:
        return DfTypes.DOUBLE;
    }
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

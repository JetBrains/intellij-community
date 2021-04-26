// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0f license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

class DfFloatConstantType extends DfConstantType<Float> implements DfFloatType {
  DfFloatConstantType(float value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfFloatType) return DfTypes.FLOAT;
    return DfType.TOP;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    Float value = getValue();
    if (Float.isNaN(value)) {
      return relationType == RelationType.NE ? DfTypes.FLOAT : BOTTOM;
    }
    switch (relationType) {
      case EQ:
        return value == 0.0f ? DfTypes.FLOAT_ZERO : this;
      case LT:
      case GT:
        return new DfFloatNotValueType(value == 0.0f ? Set.of(0.0f, -0.0f, Float.NaN) : Set.of(value, Float.NaN));
      case NE:
        return new DfFloatNotValueType(value == 0.0f ? FLOAT_ZERO_SET : Set.of(value));
      case LE:
      case GE:
        return new DfFloatNotValueType(Set.of(Float.NaN));
      default:
        return DfTypes.FLOAT;
    }
  }

  @NotNull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfFloatType.super.getPsiType();
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    return new DfFloatNotValueType(Set.of(getValue()));
  }

  @Override
  public @NotNull String toString() {
    return getValue()+"f";
  }
}

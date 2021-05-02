// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

class DfFloatConstantType extends DfConstantType<Float> implements DfFloatType {
  DfFloatConstantType(float value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfFloatRangeType) {
      return other.join(this);
    }
    if (other instanceof DfFloatConstantType) {
      float val1 = getValue();
      float val2 = ((DfFloatConstantType)other).getValue();
      if (Float.isNaN(val1)) {
        return DfFloatRangeType.create(val2, val2, false, true);
      }
      if (Float.isNaN(val2)) {
        return DfFloatRangeType.create(val1, val1, false, true);
      }
      float from = Math.min(val1, val2);
      float to = Math.max(val1, val2);
      // We don't support disjoint sets, so the best we can do is to return a range from min to max
      return DfFloatRangeType.create(from, to, false, false);
    }
    return DfType.TOP;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    float value = getValue();
    if (Float.isNaN(value)) {
      return relationType == RelationType.NE ? DfTypes.FLOAT : BOTTOM;
    }
    return DfFloatRangeType.fromRelation(relationType, value, value);
  }

  @NotNull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfFloatType.super.getPsiType();
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    float value = getValue();
    if (Float.isNaN(value)) {
      return DfFloatRangeType.create(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, false, false);
    }
    return DfFloatRangeType.create(value, value, true, true);
  }
}

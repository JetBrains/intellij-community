// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DfDoubleConstantType extends DfConstantType<Double> implements DfDoubleType {
  DfDoubleConstantType(double value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    return Objects.requireNonNull(join(other, false));
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    return join(other, true);
  }

  private DfType join(@NotNull DfType other, boolean exact) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfDoubleRangeType) {
      return ((DfDoubleRangeType)other).join(this, exact);
    }
    if (other instanceof DfDoubleConstantType) {
      double val1 = getValue();
      double val2 = ((DfDoubleConstantType)other).getValue();
      if (Double.isNaN(val1)) {
        return DfDoubleRangeType.create(val2, val2, false, true);
      }
      if (Double.isNaN(val2)) {
        return DfDoubleRangeType.create(val1, val1, false, true);
      }
      double from = Math.min(val1, val2);
      double to = Math.max(val1, val2);
      // We don't support disjoint sets, so the best we can do is to return a range from min to max
      return exact ? null : DfDoubleRangeType.create(from, to, false, false);
    }
    return exact ? null : DfType.TOP;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    double value = getValue();
    if (Double.isNaN(value)) {
      return relationType == RelationType.NE ? DfTypes.DOUBLE : BOTTOM;
    }
    return DfDoubleRangeType.fromRelation(relationType, value, value);
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    double value = getValue();
    if (Double.isNaN(value)) {
      return DfDoubleRangeType.create(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, false);
    }
    return DfDoubleRangeType.create(value, value, true, true);
  }
}

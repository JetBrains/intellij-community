// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Objects;

public class DfBigIntConstantType extends DfConstantType<BigInteger> {
  DfBigIntConstantType(@NotNull BigInteger value) {
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
    if (other instanceof DfBigIntRangeType) {
      return ((DfBigIntRangeType)other).join(this, exact);
    }
    if (other instanceof DfBigIntConstantType) {
      BigInteger val1 = getValue();
      BigInteger val2 = ((DfBigIntConstantType)other).getValue();
      BigInteger from = val1.min(val2);
      BigInteger to = val1.max(val2);
      // We don't support disjoint sets, so the best we can do is to return a range from min to max
      return exact
             ? null
             : DfBigIntRangeType.create(DfBigIntRangeType.InfBigInteger.create(from), DfBigIntRangeType.InfBigInteger.create(to), false);
    }
    return exact ? null : DfType.TOP;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    BigInteger value = getValue();
    return DfBigIntRangeType.fromRelation(relationType, DfBigIntRangeType.InfBigInteger.create(value),
                                          DfBigIntRangeType.InfBigInteger.create(value));
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    BigInteger value = getValue();
    return DfBigIntRangeType.create(DfBigIntRangeType.InfBigInteger.create(value), DfBigIntRangeType.InfBigInteger.create(value), true);
  }

  @Override
  public @NotNull String toString() {
    return DfBigIntRangeType.format(DfBigIntRangeType.InfBigInteger.create(getValue()));
  }
}

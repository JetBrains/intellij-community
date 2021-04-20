// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

final class DfDoubleNotValueType extends DfAntiConstantType<Double> implements DfDoubleType {
  DfDoubleNotValueType(Set<Double> values) {
    super(values);
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM || other.equals(this)) return true;
    if (other instanceof DfDoubleNotValueType) return ((DfDoubleNotValueType)other).myNotValues.containsAll(myNotValues);
    if (other instanceof DfDoubleConstantType) return !myNotValues.contains(((DfDoubleConstantType)other).getValue());
    if (other == DfTypes.DOUBLE_ZERO) return !myNotValues.contains(0.0) && !myNotValues.contains(-0.0);
    return false;
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (other == DfTypes.DOUBLE_ZERO) {
      Set<Double> notValues = new HashSet<>(myNotValues);
      notValues.remove(0.0);
      notValues.remove(-0.0);
      assert !notValues.isEmpty();
      return new DfDoubleNotValueType(notValues);
    }
    if (other instanceof DfDoubleNotValueType) {
      Set<Double> notValues = new HashSet<>(myNotValues);
      notValues.retainAll(((DfDoubleNotValueType)other).myNotValues);
      return notValues.isEmpty() ? DfTypes.DOUBLE : new DfDoubleNotValueType(notValues);
    }
    return DfType.TOP;
  }

  @NotNull
  @Override
  public DfType meet(@NotNull DfType other) {
    if (isSuperType(other)) return other;
    if (other.isSuperType(this)) return this;
    if (other instanceof DfDoubleConstantType && myNotValues.contains(((DfDoubleConstantType)other).getValue())) return DfType.BOTTOM;
    if (other == DfTypes.DOUBLE_ZERO) {
      assert myNotValues.contains(0.0) || myNotValues.contains(-0.0);
      return new DfDoubleConstantType(myNotValues.contains(0.0) ? -0.0 : 0.0);
    }
    if (other instanceof DfDoubleNotValueType) {
      Set<Double> notValues = new HashSet<>(myNotValues);
      notValues.addAll(((DfDoubleNotValueType)other).myNotValues);
      return new DfDoubleNotValueType(notValues);
    }
    return DfType.BOTTOM;
  }

  @Override
  public @Nullable DfType tryNegate() {
    if (myNotValues.size() == 1) return new DfDoubleConstantType(myNotValues.iterator().next());
    if (myNotValues.equals(Set.of(0.0, -0.0))) return DfTypes.DOUBLE_ZERO;
    return null;
  }

  @Override
  public @NotNull String toString() {
    return JavaAnalysisBundle.message("type.presentation.except.values", PsiKeyword.DOUBLE, myNotValues);
  }
}

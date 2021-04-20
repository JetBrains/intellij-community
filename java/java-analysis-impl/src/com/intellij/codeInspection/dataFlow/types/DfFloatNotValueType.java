// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

final class DfFloatNotValueType extends DfAntiConstantType<Float> implements DfFloatType {
  DfFloatNotValueType(Set<Float> values) {
    super(values);
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM || other.equals(this)) return true;
    if (other instanceof DfFloatNotValueType) return ((DfFloatNotValueType)other).myNotValues.containsAll(myNotValues);
    if (other instanceof DfFloatConstantType) return !myNotValues.contains(((DfFloatConstantType)other).getValue());
    if (other == DfTypes.FLOAT_ZERO) return !myNotValues.contains(0.0f) && !myNotValues.contains(-0.0f);
    return false;
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (other == DfTypes.FLOAT_ZERO) {
      Set<Float> notValues = new HashSet<>(myNotValues);
      notValues.remove(0.0f);
      notValues.remove(-0.0f);
      assert !notValues.isEmpty();
      return new DfFloatNotValueType(notValues);
    }
    if (other instanceof DfFloatNotValueType) {
      Set<Float> notValues = new HashSet<>(myNotValues);
      notValues.retainAll(((DfFloatNotValueType)other).myNotValues);
      return notValues.isEmpty() ? DfTypes.FLOAT : new DfFloatNotValueType(notValues);
    }
    return DfType.TOP;
  }

  @NotNull
  @Override
  public DfType meet(@NotNull DfType other) {
    if (isSuperType(other)) return other;
    if (other.isSuperType(this)) return this;
    if (other instanceof DfFloatConstantType && myNotValues.contains(((DfFloatConstantType)other).getValue())) return DfType.BOTTOM;
    if (other == DfTypes.FLOAT_ZERO) {
      assert myNotValues.contains(0.0f) || myNotValues.contains(-0.0f);
      return new DfFloatConstantType(myNotValues.contains(0.0f) ? -0.0f : 0.0f);
    }
    if (other instanceof DfFloatNotValueType) {
      Set<Float> notValues = new HashSet<>(myNotValues);
      notValues.addAll(((DfFloatNotValueType)other).myNotValues);
      return new DfFloatNotValueType(notValues);
    }
    return DfType.BOTTOM;
  }

  @Override
  protected String renderValue(Float value) {
    return value + "f";
  }

  @Override
  public @Nullable DfType tryNegate() {
    if (myNotValues.size() == 1) return new DfFloatConstantType(myNotValues.iterator().next());
    if (myNotValues.equals(Set.of(0.0f, -0.0f))) return DfTypes.FLOAT_ZERO;
    return null;
  }

  @Override
  public @NotNull String toString() {
    return JavaAnalysisBundle.message("type.presentation.except.values", PsiKeyword.FLOAT, StringUtil.join(myNotValues, ", "));
  }
}

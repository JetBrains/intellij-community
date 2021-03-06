// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

final class DfFloatNotValueType extends DfAntiConstantType<Float> implements DfFloatType {
  DfFloatNotValueType(Set<Float> values) {
    super(values);
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfTypes.BOTTOM || other.equals(this)) return true;
    if (other instanceof DfFloatNotValueType) return ((DfFloatNotValueType)other).myNotValues.containsAll(myNotValues);
    if (other instanceof DfFloatConstantType) return !myNotValues.contains(((DfFloatConstantType)other).getValue());
    return false;
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (other instanceof DfFloatNotValueType) {
      Set<Float> notValues = new HashSet<>(myNotValues);
      notValues.retainAll(((DfFloatNotValueType)other).myNotValues);
      return notValues.isEmpty() ? DfTypes.FLOAT : new DfFloatNotValueType(notValues);
    }
    return DfTypes.TOP;
  }

  @NotNull
  @Override
  public DfType meet(@NotNull DfType other) {
    if (isSuperType(other)) return other;
    if (other.isSuperType(this)) return this;
    if (other instanceof DfFloatConstantType && myNotValues.contains(((DfFloatConstantType)other).getValue())) return DfTypes.BOTTOM;
    if (other instanceof DfFloatNotValueType) {
      Set<Float> notValues = new HashSet<>(myNotValues);
      notValues.addAll(((DfFloatNotValueType)other).myNotValues);
      return new DfFloatNotValueType(notValues);
    }
    return DfTypes.BOTTOM;
  }

  @Override
  public String toString() {
    return JavaAnalysisBundle.message("type.presentation.except.values", PsiKeyword.FLOAT, StringUtil.join(myNotValues, ", "));
  }
}

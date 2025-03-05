// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;

public interface DfDoubleType extends DfFloatingPointType {
  @Override
  default @NotNull PsiPrimitiveType getPsiType() {
    return PsiTypes.doubleType();
  }

  @Override
  default @NotNull DfType correctForRelationResult(@NotNull RelationType relation, boolean result) {
    if (result == (relation != RelationType.NE)) {
      return meet(new DfDoubleRangeType(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, false));
    }
    return this;
  }

  @Override
  default @NotNull DfType meetRelation(@NotNull RelationType relationType,
                                       @NotNull DfType other) {
    DfType result = meet(other.fromRelation(relationType));
    if (result == DfType.BOTTOM && relationType != RelationType.NE) {
      if (!isSuperType(DfTypes.DOUBLE_NAN) && other.isSuperType(DfTypes.DOUBLE_NAN)) {
        return this;
      }
    }
    return result;
  }
}

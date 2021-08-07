// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public interface DfFloatType extends DfFloatingPointType {
  @NotNull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.FLOAT;
  }

  @Override
  default @NotNull DfType correctForRelationResult(@NotNull RelationType relation, boolean result) {
    if (result == (relation != RelationType.NE)) {
      return meet(new DfFloatRangeType(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, false, false));
    }
    return this;
  }

  @Override
  @NotNull
  default DfType meetRelation(@NotNull RelationType relationType,
                              @NotNull DfType other) {
    DfType result = meet(other.fromRelation(relationType));
    if (result == DfType.BOTTOM && relationType != RelationType.NE) {
      if (!isSuperType(DfTypes.FLOAT_NAN) && other.isSuperType(DfTypes.FLOAT_NAN)) {
        return this;
      }
    }
    return result;
  }
}

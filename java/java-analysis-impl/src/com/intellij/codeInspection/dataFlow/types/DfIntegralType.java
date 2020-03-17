// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an integral primitive (int or long)
 */
public interface DfIntegralType extends DfPrimitiveType {
  @NotNull
  LongRangeSet getRange();

  @NotNull
  default DfType meetRelation(@NotNull RelationType relation, @NotNull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (other instanceof DfIntegralType) {
      return meetRange(((DfIntegralType)other).getRange().fromRelation(relation));
    }
    return DfTypes.BOTTOM;
  }

  @NotNull DfType meetRange(@NotNull LongRangeSet range);
}

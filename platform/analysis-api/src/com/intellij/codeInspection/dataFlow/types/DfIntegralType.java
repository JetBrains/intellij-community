// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an integral primitive whose value can be represented as {@link LongRangeSet}.
 */
public interface DfIntegralType extends DfType {
  @NotNull
  LongRangeSet getRange();

  default @NotNull LongRangeSet getWideRange() {
    return getRange();
  }

  /**
   * Perform binary operation between this type and other type
   * @param other other operand
   * @param op operation to perform
   * @return result of the operation
   */
  @NotNull DfType eval(@NotNull DfType other, @NotNull LongRangeBinOp op);

  @NotNull DfType meetRange(@NotNull LongRangeSet range);
}

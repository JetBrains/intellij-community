// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

abstract class DfAbstractRangeType implements DfIntegralType {
  private final @NotNull LongRangeSet myRange;
  private final @Nullable LongRangeSet myWideRange;

  DfAbstractRangeType(@NotNull LongRangeSet range, @Nullable LongRangeSet wideRange) {
    myRange = range;
    myWideRange = wideRange;
  }

  @NotNull
  @Override
  public LongRangeSet getRange() {
    return myRange;
  }

  @NotNull
  @Override
  public LongRangeSet getWideRange() {
    return myWideRange == null ? myRange : myWideRange;
  }

  @Override
  public int hashCode() {
    return myRange.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
           obj != null && obj.getClass() == getClass() && 
           ((DfAbstractRangeType)obj).myRange.equals(myRange) &&
           Objects.equals(((DfAbstractRangeType)obj).myWideRange, myWideRange);
  }
}

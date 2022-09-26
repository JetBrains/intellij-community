// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.rangeSet;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a specific integral type that could be used to perform math on LongRangeSet 
 */
public enum LongRangeType {
  INT32(true, 4), INT64(true, 8);
  
  private final boolean mySigned;
  private final int myBytes;
  private final LongRangeSet myRange;

  LongRangeType(boolean signed, int bytes) {
    mySigned = signed;
    myBytes = bytes;
    myRange = LongRangeSet.range(min(), max());
  }

  /**
   * @return number of bytes in binary representation of the type
   */
  public int bytes() {
    return myBytes;
  }

  /**
   * @return number of bits in binary representation of the type
   */
  public int bits() {
    return myBytes * 8;
  }

  /**
   * @return LongRangeSet
   */
  public @NotNull LongRangeSet fullRange() {
    return myRange;
  }
  
  public long min() {
    return mySigned ? -(1L << (bits() - 1)) : 0;
  }
  
  public long max() {
    return (1L << (mySigned ? bits() - 1 : bits())) - 1;
  }
  
  public long cast(long value) {
    return switch (this) {
      case INT32 -> (int)value;
      case INT64 -> value;
    };
  }
  
  public boolean subtractionMayOverflow(long a, long b) {
    if (myBytes == 8) {
      long diff = a - b;
      // Hacker's Delight 2nd Edition, 2-13 Overflow Detection
      return ((a ^ b) & (a ^ diff)) < 0;
    }
    long diff = a - b;
    return diff < min() || diff > max();
  }
}

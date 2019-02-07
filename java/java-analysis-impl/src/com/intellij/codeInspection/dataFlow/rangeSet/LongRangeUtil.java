// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.rangeSet;

/**
 * Helper methods for LongRangeSet implementation
 */
class LongRangeUtil {
  /**
   * Calculates greatest common divisor for two non-negative numbers
   * @param x x
   * @param y y
   * @return GCD(x, y)
   */
  static int gcd(int x, int y) {
    while (x != 0) {
      int z = x;
      x = y % z;
      y = z;
    }
    return y;
  }

  /**
   * Clears given bit in value 
   * @param value value to clear bit at
   * @param bitNumber number of bit to clear (0 = least significant; 63 = most significant)
   * @return updated value
   */
  static long clearBit(long value, int bitNumber) {
    return value & (~(1L << bitNumber));
  }

  /**
   * Sets given bit in value 
   * @param value value to set bit at
   * @param bitNumber number of bit to set (0 = least significant; 63 = most significant)
   * @return updated value
   */
  static long setBit(long value, int bitNumber) {
    return value | (1L << bitNumber);
  }

  /**
   * Tests if given bit is set in the value
   * @param value value to check
   * @param bitNumber number of bit to check (0 = least significant; 63 = most significant)
   * @return true of given big is set
   */
  static boolean isSet(long value, int bitNumber) {
    return (value & (1L << bitNumber)) != 0;
  }

  /**
   * Return a subset of bits from the value
   * @param value value to extract bits from
   * @param offset offset at which bits should be extracted (0 = starting from least significant)
   * @param count number of bits to extract
   * @return extracted bits
   */
  static long extractBits(long value, int offset, int count) {
    long shifted = value >>> offset;
    return count == Long.SIZE ? shifted : shifted & ((1L << count) - 1);
  }

  /**
   * Returns the remainder. Unlike Java % operation this returns positive remainder for negative numbers (e.g. remainder(-1, 5) = -4).
   * Strictly speaking the return value is the difference between dividend and 
   * the greatest number x so that {@code x * dividend <= divisor}.
   * 
   * @param dividend dividend
   * @param divisor divisor, assumed positive
   * @return the remainder.
   */
  static int remainder(long dividend, int divisor) {
    dividend = dividend % divisor;
    return (int)(dividend < 0 ? dividend + divisor : dividend);
  }

  /**
   * Rotates bits in the remainder bit mask to the right
   * @param bits remainder bit mask
   * @param mod divisor (<=64; no set bits greater than mod) 
   * @param amount number of bits to rotate by
   * @return result of rotation
   */
  static long rotateRemainders(long bits, int mod, int amount) {
    return extractBits(bits, amount, Long.SIZE) | (extractBits(bits, 0, amount) << (mod - amount));
  }
}

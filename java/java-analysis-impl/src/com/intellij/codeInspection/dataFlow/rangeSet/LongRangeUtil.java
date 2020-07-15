// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.rangeSet;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper methods for LongRangeSet implementation
 */
final class LongRangeUtil {
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

  /**
   * Represents a 64-bit value where some of bits are unknown
   */
  static class BitString {
    /**
     * Totally unknown value: any bit may have any value
     */
    static final BitString UNSURE = new BitString(0, 0);

    final long myBits;
    final long myMask;

    /**
     * Constructs a partially known value
     * @param bits value
     * @param mask mask which specifies known bits; zeros represent unknown bits (they value in {@code bits} is ignored)
     */
    BitString(long bits, long mask) {
      myBits = bits & mask;
      myMask = mask;
    }

    /**
     * Unites (joins) this BitString with other
     *
     * @param other a BitString to join
     * @return resulting BitString
     */
    @NotNull BitString unite(BitString other) {
      long diff = myBits ^ other.myBits;
      return new BitString(myBits, myMask & other.myMask & ~diff);
    }

    /**
     * Intersects this BitString with other
     *
     * @param other a BitString to intersect with
     * @return resulting BitString, null if intersection is empty
     */
    @Nullable BitString intersect(BitString other) {
      long intersectMask = myMask & other.myMask;
      if ((myBits & intersectMask) != (other.myBits & intersectMask)) return null;
      return new BitString(myBits | other.myBits, myMask | other.myMask);
    }

    /**
     * Returns given bit
     * @param bit a bit number (0 = LSB)
     * @return YES for set bit, NO for clear bit, UNSURE for unknown bit
     */
    @NotNull ThreeState get(int bit) {
      return isSet(myMask, bit) ? ThreeState.fromBoolean(isSet(myBits, bit)) : ThreeState.UNSURE;
    }

    /**
     * Performs bitwise-and over this and other BitString
     * @param other other operand
     * @return result of bitwise-and
     */
    @NotNull BitString and(BitString other) {
      long andBits = myBits & other.myBits;
      // 0 & ? = ? & 0 = 0; 1 & 1 = 1; ? & 1 = 1 & ? = ? & ? = ?
      long andMask = (myMask ^ myBits) | (other.myMask ^ other.myBits) | andBits;
      return new BitString(andBits, andMask);
    }

    /**
     * Performs bitwise-or over this and other BitString
     * @param other other operand
     * @return result of bitwise-or
     */
    @NotNull BitString or(BitString other) {
      long orBits = myBits | other.myBits;
      // 1 | ? = ? | 1 = 1; 0 | 0 = 0; ? | 0 = 0 | ? = ? | ? = ?
      long orMask = ((myMask ^ myBits) & (other.myMask ^ other.myBits)) | orBits;
      return new BitString(orBits, orMask);
    }

    /**
     * Performs bitwise-xor over this and other BitString
     * @param other other operand
     * @return result of bitwise-xor
     */
    @NotNull BitString xor(BitString other) {
      long xorBits = myBits ^ other.myBits;
      // if bit is unknown in either operand, it's unknown in the result; otherwise we may xor normally
      long xorMask = myMask & other.myMask;
      return new BitString(xorBits, xorMask);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("[");
      for(int i=Long.SIZE-1; i>=0; i--) {
        sb.append(isSet(myMask, i) ? isSet(myBits, i) ? '1' : '0' : '?');
        if (i != 0 && i % 8 == 0) {
          sb.append(".");
        }
      }
      sb.append("]");
      return sb.toString();
    }

    /**
     * Returns a BitString for values between from and to.
     *
     * @param from lower bound, inclusive
     * @param to upper bound, inclusive
     * @return a BitString which covers at least all the values between from and to (may also cover some more values)
     */
    static @NotNull BitString fromRange(long from, long to) {
      if (from == to) {
        return new BitString(from, -1L);
      }
      long bits = 0;
      long mask = -1;
      while (true) {
        int fromBit = Long.SIZE - 1 - Long.numberOfLeadingZeros(from);
        int toBit = Long.SIZE - 1 - Long.numberOfLeadingZeros(to);
        if (fromBit != toBit) {
          for (int i = 0; i <= Math.max(fromBit, toBit); i++) {
            mask = clearBit(mask, i);
          }
          break;
        }
        if (fromBit == 0) break;
        bits = setBit(bits, fromBit);
        from = clearBit(from, fromBit);
        to = clearBit(to, fromBit);
      }
      return new BitString(bits, mask);
    }
  }
}

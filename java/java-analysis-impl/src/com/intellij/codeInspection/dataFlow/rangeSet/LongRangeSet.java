// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.rangeSet;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ThreeState;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static com.intellij.codeInspection.dataFlow.rangeSet.LongRangeUtil.*;

/**
 * An immutable set of long values optimized for small number of ranges.
 *
 * @author Tagir Valeev
 */
public abstract class LongRangeSet {
  private static final String JETBRAINS_RANGE = "org.jetbrains.annotations.Range";
  private static final String CHECKER_RANGE = "org.checkerframework.common.value.qual.IntRange";
  private static final String CHECKER_GTE_NEGATIVE_ONE = "org.checkerframework.checker.index.qual.GTENegativeOne";
  private static final String CHECKER_NON_NEGATIVE = "org.checkerframework.checker.index.qual.NonNegative";
  private static final String CHECKER_POSITIVE = "org.checkerframework.checker.index.qual.Positive";
  private static final String JSR305_NONNEGATIVE = "javax.annotation.Nonnegative";
  private static final String VALIDATION_MIN = "javax.validation.constraints.Min";
  private static final String VALIDATION_MAX = "javax.validation.constraints.Max";
  private static final List<String> ANNOTATIONS = Arrays.asList(CHECKER_RANGE,
                                                                CHECKER_GTE_NEGATIVE_ONE,
                                                                CHECKER_NON_NEGATIVE,
                                                                CHECKER_POSITIVE,
                                                                JSR305_NONNEGATIVE,
                                                                VALIDATION_MIN,
                                                                VALIDATION_MAX);

  LongRangeSet() {}

  /**
   * Subtracts given set from the current. May return bigger set (containing some additional elements) if exact subtraction is impossible.
   *
   * @param other set to subtract
   * @return a new set
   */
  @NotNull
  public abstract LongRangeSet subtract(@NotNull LongRangeSet other);

  public LongRangeSet without(long value) {
    return subtract(point(value));
  }

  /**
   * @return true if set is empty
   */
  public boolean isEmpty() {
    return this == Empty.EMPTY;
  }

  /**
   * Intersects current set with other
   *
   * @param other other set to intersect with
   * @return a new set
   */
  @NotNull
  public abstract LongRangeSet intersect(@NotNull LongRangeSet other);

  /**
   * Merge current set with other. May return bigger set if exact representation is impossible.
   *
   * @param other other set to merge with
   * @return a new set
   */
  @NotNull
  public abstract LongRangeSet unite(@NotNull LongRangeSet other);

  /**
   * @return a minimal value contained in the set
   * @throws NoSuchElementException if set is empty
   */
  public abstract long min();

  /**
   * @return a maximal value contained in the set
   * @throws NoSuchElementException if set is empty
   */
  public abstract long max();

  /**
   * @return a constant value if this set represents a constant; null otherwise
   */
  @Nullable
  public Long getConstantValue() {
    return null;
  }

  /**
   * Checks if current set and other set have at least one common element
   *
   * @param other other set to check whether intersection exists
   * @return true if this set intersects other set
   */
  public abstract boolean intersects(LongRangeSet other);

  /**
   * Checks whether current set contains given value
   *
   * @param value value to find
   * @return true if current set contains given value
   */
  public abstract boolean contains(long value);

  /**
   * Checks whether current set contains all the values from other set
   *
   * @param other a sub-set candidate
   * @return true if current set contains all the values from other
   */
  public abstract boolean contains(LongRangeSet other);

  /**
   * Creates a new set which contains all possible values satisfying given predicate regarding the current set.
   * <p>
   *   E.g. if current set is {0..10} and relation is "GT", then result will be {1..Long.MAX_VALUE} (values which can be greater than
   *   some value from the current set)
   *
   * @param relation relation to be applied to current set (JavaTokenType.EQEQ/NE/GT/GE/LT/LE)
   * @return new set or null if relation is unsupported
   */
  public LongRangeSet fromRelation(@Nullable RelationType relation) {
    if (isEmpty() || relation == null) return null;
    switch (relation) {
      case EQ:
        return this;
      case NE: {
        long min = min();
        if (min == max()) return all().without(min);
        return all();
      }
      case GT: {
        long min = min();
        return min == Long.MAX_VALUE ? empty() : range(min + 1, Long.MAX_VALUE);
      }
      case GE:
        return range(min(), Long.MAX_VALUE);
      case LE:
        return range(Long.MIN_VALUE, max());
      case LT: {
        long max = max();
        return max == Long.MIN_VALUE ? empty() : range(Long.MIN_VALUE, max - 1);
      }
      default:
        return null;
    }
  }

  public abstract String getPresentationText(PsiType type);

  /**
   * Performs a supported binary operation from token (defined in {@link JavaTokenType}).
   *
   * @param token  a token which corresponds to the operation
   * @param right  a right-hand operand
   * @param isLong true if operation should be performed on long types (otherwise int is assumed)
   * @return the resulting LongRangeSet which covers possible results of the operation (probably including some more elements);
   * or null if the supplied token is not supported.
   */
  @Contract("null, _, _ -> null")
  @Nullable
  public final LongRangeSet binOpFromToken(IElementType token, LongRangeSet right, boolean isLong) {
    if (token == null) return null;
    if (token.equals(JavaTokenType.PLUS)) {
      return plus(right, isLong);
    }
    if (token.equals(JavaTokenType.MINUS)) {
      return minus(right, isLong);
    }
    if (token.equals(JavaTokenType.AND)) {
      return bitwiseAnd(right);
    }
    if (token.equals(JavaTokenType.OR)) {
      return bitwiseOr(right, isLong);
    }
    if (token.equals(JavaTokenType.XOR)) {
      return bitwiseXor(right, isLong);
    }
    if (token.equals(JavaTokenType.PERC)) {
      return mod(right);
    }
    if (token.equals(JavaTokenType.DIV)) {
      return div(right, isLong);
    }
    if (token.equals(JavaTokenType.LTLT)) {
      return shiftLeft(right, isLong);
    }
    if (token.equals(JavaTokenType.GTGT)) {
      return shiftRight(right, isLong);
    }
    if (token.equals(JavaTokenType.GTGTGT)) {
      return unsignedShiftRight(right, isLong);
    }
    if (token.equals(JavaTokenType.ASTERISK)) {
      return mul(right, isLong);
    }
    return null;
  }

  @Nullable
  public LongRangeSet wideBinOpFromToken(@NotNull IElementType token, @NotNull LongRangeSet other, boolean isLong) {
    if (token.equals(JavaTokenType.PLUS) || token.equals(JavaTokenType.MINUS)) {
      return plusWiden(token.equals(JavaTokenType.MINUS) ? other.negate(isLong) : other, isLong);
    }
    if (token.equals(JavaTokenType.ASTERISK)) {
      return mulWiden(other, isLong);
    }
    return null;
  }

  private LongRangeSet mulWiden(LongRangeSet other, boolean isLong) {
    if (Point.ZERO.equals(this)) return this;
    if (Point.ZERO.equals(other)) return other;
    if (Point.ONE.equals(this)) return other;
    if (Point.ONE.equals(other)) return this;
    if (Point.ZERO.equals(this.mod(point(2))) || Point.ZERO.equals(other.mod(point(2)))) {
      return modRange(minValue(isLong), maxValue(isLong), 2, 1);
    }
    return null;
  }

  private LongRangeSet plusWiden(LongRangeSet other, boolean isLong) {
    if (this instanceof Point && other instanceof Point) {
      long val1 = ((Point)this).myValue;
      long val2 = ((Point)other).myValue;
      int tzb1 = val1 == 0 ? 0 : Long.numberOfTrailingZeros(val1);
      int tzb2 = val2 == 0 ? 0 : Long.numberOfTrailingZeros(val2);
      LongRangeSet constVal;
      int mod;
      if (tzb1 > tzb2) {
        constVal = other;
        mod = 1 << (Math.min(6, tzb1));
      } else {
        constVal = this;
        mod = 1 << (Math.min(6, tzb2));
      }
      if (mod < 2) return null;
      return modRange(minValue(isLong), maxValue(isLong), mod, 1).plus(constVal, isLong);
    }
    if (this instanceof Point && other instanceof ModRange) {
      ModRange modRange = (ModRange)other;
      long value = ((Point)this).myValue;
      if (value % modRange.myMod == 0) {
        return modRange(minValue(isLong), maxValue(isLong), modRange.myMod, modRange.myBits);
      }
      if (modRange.myBits == 1) {
        return this.plus(other, isLong);
      }
      if (value >= -64 && value < 64) {
        int gcd = gcd(Math.abs((int)value), modRange.myMod);
        if (gcd > 1) {
          long count = modRange.myMod / gcd;
          long bits = 0;
          for(int i=0; i<count; i++) {
            bits |= (modRange.myBits >>> (i * gcd)) & ((1L << gcd) - 1);
          }
          return modRange(minValue(isLong), maxValue(isLong), gcd, bits);
        }
      }
    }
    if (other instanceof Point && this instanceof ModRange) {
      return other.plusWiden(this, isLong);
    }
    return null;
  }

  public abstract boolean isCardinalityBigger(long cutoff);

  public abstract LongRangeSet castTo(PsiPrimitiveType type);

  /**
   * Returns a range which represents all the possible values after applying {@link Math#abs(int)} or {@link Math#abs(long)}
   * to the values from this set
   *
   * @param isLong whether {@link Math#abs(long)} is applied
   * @return a new range
   */
  @NotNull
  public abstract LongRangeSet abs(boolean isLong);

  /**
   * Returns a range which represents all the possible values after applying unary minus
   * to the values from this set
   *
   * @param isLong whether result should be truncated to {@code int}
   * @return a new range
   */
  @NotNull
  public abstract LongRangeSet negate(boolean isLong);

  /**
   * Returns a range which represents all the possible values after performing an addition between any value from this range
   * and any value from other range. The resulting range may contain some more values which cannot be produced by addition.
   * Guaranteed to be commutative.
   *
   * @param isLong whether result should be truncated to {@code int}
   * @return a new range
   */
  @NotNull
  public abstract LongRangeSet plus(LongRangeSet other, boolean isLong);

  /**
   * Returns a range which represents all the possible values after performing an addition between any value from this range
   * and any value from other range. The resulting range may contain some more values which cannot be produced by addition.
   *
   * @param isLong whether result should be truncated to {@code int}
   * @return a new range
   */
  @NotNull
  public LongRangeSet minus(LongRangeSet other, boolean isLong) {
    return plus(other.negate(isLong), isLong);
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x | y} operation for
   * all {@code x} from this set and for all {@code y} from the other set. The resulting set may contain
   * some more values.
   *
   * @param other other set to perform bitwise-or with
   * @return a new range
   */
  @NotNull
  public LongRangeSet bitwiseOr(LongRangeSet other, boolean isLong) {
    if (this.isEmpty() || other.isEmpty()) return empty();
    LongRangeSet result = fromBits(getBitwiseMask().or(other.getBitwiseMask()));
    return isLong ? result : result.intersect(Range.INT_RANGE);
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x ^ y} operation for
   * all {@code x} from this set and for all {@code y} from the other set. The resulting set may contain
   * some more values.
   *
   * @param other other set to perform bitwise-xor with
   * @return a new range
   */
  @NotNull
  public LongRangeSet bitwiseXor(LongRangeSet other, boolean isLong) {
    if (this.isEmpty() || other.isEmpty()) return empty();
    LongRangeSet result = fromBits(getBitwiseMask().xor(other.getBitwiseMask()));
    return isLong ? result : result.intersect(Range.INT_RANGE);
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x & y} operation for
   * all {@code x} from this set and for all {@code y} from the other set. The resulting set may contain
   * some more values.
   *
   * @param other other set to perform bitwise-and with
   * @return a new range
   */
  @NotNull
  public LongRangeSet bitwiseAnd(LongRangeSet other) {
    if (this.isEmpty() || other.isEmpty()) return empty();
    long[] left = splitAtZero(asRanges());
    long[] right = splitAtZero(other.asRanges());
    // More than three intervals --> convert to single interval to make result more compact (though probably less precise)
    if (left.length > 6) {
      left = splitAtZero(new long[]{left[0], left[left.length - 1]});
    }
    if (right.length > 6) {
      right = splitAtZero(new long[]{right[0], right[right.length - 1]});
    }
    BitString globalMask = getBitwiseMask().and(other.getBitwiseMask());
    globalMask = new BitString(globalMask.myBits | ~globalMask.myMask, -1L);
    LongRangeSet result = empty();
    for (int i = 0; i < left.length; i += 2) {
      for (int j = 0; j < right.length; j += 2) {
        result = result.unite(bitwiseAnd(left[i], left[i + 1], right[j], right[j + 1], globalMask));
      }
    }
    return result;
  }
  
  abstract public LongRangeSet mul(LongRangeSet multiplier, boolean isLong);

  BitString getBitwiseMask() {
    if (isEmpty()) {
      return BitString.UNSURE;
    }
    return BitString.fromRange(min(), max());
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x / y} operation for
   * all {@code x} from this set and for all {@code y} from the divisor set. The resulting set may contain
   * some more values. Division by zero yields an empty set of possible results.
   *
   * @param divisor divisor set to divide by
   * @param isLong whether the operation is performed on long type (if false, the int type is assumed). This only changes the special
   *               treatment of {@code MIN_VALUE/-1} division; other division results do not depend on the resulting type.
   * @return a new range
   */
  @NotNull
  public LongRangeSet div(LongRangeSet divisor, boolean isLong) {
    if (divisor.isEmpty() || divisor.equals(Point.ZERO)) return empty();
    LongRangeSet dividend = this;
    if (!isLong) {
      divisor = divisor.intersect(Range.INT_RANGE);
      dividend = dividend.intersect(Range.INT_RANGE);
    } 
    long[] left = splitAtZero(dividend.asRanges());
    long[] right = splitAtZero(new long[]{divisor.min(), divisor.max()});
    LongRangeSet result = empty();
    for (int i = 0; i < left.length; i += 2) {
      for (int j = 0; j < right.length; j += 2) {
        result = result.unite(divide(left[i], left[i + 1], right[j], right[j + 1], isLong));
      }
    }
    return result;
  }

  /**
   * Checks whether subtraction of this and other range may overflow
   * @param other range to subtract from this range
   * @param isLong whether subtraction should be performed on long values (otherwise int is assumed)
   * @return true if result may overflow, false if it never overflows
   */
  public boolean subtractionMayOverflow(LongRangeSet other, boolean isLong) {
    long leftMin = min();
    long leftMax = max();
    long rightMin = other.min();
    long rightMax = other.max();
    return isLong
           ? overflowsLong(leftMin, rightMax) || overflowsLong(leftMax, rightMin)
           : overflowsInt(leftMin, rightMax) || overflowsInt(leftMax, rightMin);
  }

  private static boolean overflowsInt(long a, long b) {
    long diff = a - b;
    return diff < Integer.MIN_VALUE || diff > Integer.MAX_VALUE;
  }

  private static boolean overflowsLong(long a, long b) {
    long diff = a - b;
    // Hacker's Delight 2nd Edition, 2-13 Overflow Detection
    return ((a ^ b) & (a ^ diff)) < 0;
  }

  @NotNull
  private static LongRangeSet divide(long dividendMin, long dividendMax, long divisorMin, long divisorMax, boolean isLong) {
    if (divisorMin == 0) {
      if (divisorMax == 0) return empty();
      divisorMin = 1;
    }
    if (dividendMin >= 0) {
      return divisorMin > 0
             ? range(dividendMin / divisorMax, dividendMax / divisorMin)
             : range(dividendMax / divisorMax, dividendMin / divisorMin);
    }
    if (divisorMin > 0) {
      return range(dividendMin / divisorMin, dividendMax / divisorMax);
    }
    long minValue = minValue(isLong);
    if (dividendMin == minValue && divisorMax == -1) {
      // MIN_VALUE/-1 = MIN_VALUE
      return point(minValue)
        .unite(divisorMin == -1 ? empty() : range(dividendMin / divisorMin, dividendMin / (divisorMax - 1)))
        .unite(dividendMax == minValue ? empty() : range(dividendMax / divisorMin, (dividendMin + 1) / divisorMax));
    }
    return range(dividendMax / divisorMin, dividendMin / divisorMax);
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x << y} operation for
   * all {@code x} from this set and for all {@code y} from the shiftSize set. The resulting set may contain
   * some more values.
   *
   * @param shiftSize set of possible shift sizes (number of bits to shift to the left)
   * @param isLong whether the operation is performed on long type (if false, the int type is assumed).
   * @return a new range
   */
  @NotNull
  public LongRangeSet shiftLeft(LongRangeSet shiftSize, boolean isLong) {
    if (isEmpty() || shiftSize.isEmpty()) return empty();
    if (shiftSize instanceof Point) {
      long shift = ((Point)shiftSize).myValue & ((isLong ? Long.SIZE : Integer.SIZE)-1);
      return point(1L << shift).mul(this, isLong);
    }
    return isLong ? Range.LONG_RANGE : Range.INT_RANGE;
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x >> y} operation for
   * all {@code x} from this set and for all {@code y} from the shiftSize set. The resulting set may contain
   * some more values.
   *
   * @param shiftSize set of possible shift sizes (number of bits to shift to the right)
   * @param isLong whether the operation is performed on long type (if false, the int type is assumed).
   * @return a new range
   */
  @NotNull
  public LongRangeSet shiftRight(LongRangeSet shiftSize, boolean isLong) {
    return doShiftRight(shiftSize, isLong, false);
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x >>> y} operation for
   * all {@code x} from this set and for all {@code y} from the shiftSize set. The resulting set may contain
   * some more values.
   *
   * @param shiftSize set of possible shift sizes (number of bits to shift to the right)
   * @param isLong whether the operation is performed on long type (if false, the int type is assumed).
   * @return a new range
   */
  @NotNull
  public LongRangeSet unsignedShiftRight(LongRangeSet shiftSize, boolean isLong) {
    return doShiftRight(shiftSize, isLong, true);
  }

  private LongRangeSet doShiftRight(LongRangeSet shiftSize, boolean isLong, boolean unsigned) {
    if (isEmpty() || shiftSize.isEmpty()) return empty();
    int maxShift = (isLong ? Long.SIZE : Integer.SIZE) - 1;
    if (shiftSize.min() < 0 || shiftSize.max() > maxShift) {
      shiftSize = shiftSize.bitwiseAnd(point(maxShift));
    }
    long min = shiftSize.min();
    long max = shiftSize.max();
    LongRangeSet negative = intersect(range(minValue(isLong), -1));
    LongRangeSet positive = intersect(range(0, maxValue(isLong)));
    LongRangeSet positiveResult = positive.shrPositive(min, max, isLong);
    LongRangeSet negativeResult;
    LongRangeSet negativeComplement = point(-1).minus(negative, isLong); // -1-negative
    if (unsigned) {
      if (min == 0) {
        positiveResult = positiveResult.unite(negative);
        if (max == 0) return positiveResult;
        min++;
      }
      // for x < 0, y > 0, x >>> y = (MAX_VALUE - ((-1-x) >> 1)) >> (y-1)
      negativeResult = point(maxValue(isLong)).minus(negativeComplement.shrPositive(1, 1, isLong), isLong)
        .shrPositive(min - 1, max - 1, isLong);
    } else {
      negativeResult = point(-1).minus(negativeComplement.shrPositive(min, max, isLong), isLong);
    }
    return positiveResult.unite(negativeResult);
  }

  private LongRangeSet shrPositive(long min, long max, boolean isLong) {
    if (isEmpty()) return empty();
    int maxShift = (isLong ? Long.SIZE : Integer.SIZE) - 1;
    if (max == maxShift) {
      return min == max ? Point.ZERO : Point.ZERO.unite(div(range(1L << min, 1L << (max - 1)), isLong));
    }
    return div(range(1L << min, 1L << max), isLong);
  }

  /**
   * Returns a range which represents all the possible values after applying {@code x % y} operation for
   * all {@code x} from this set and for all {@code y} from the divisor set. The resulting set may contain
   * some more values. Division by zero yields an empty set of possible results.
   *
   * @param divisor divisor set to divide by
   * @return a new range
   */
  @NotNull
  abstract public LongRangeSet mod(LongRangeSet divisor);

  private static long[] splitAtZero(long[] ranges) {
    for (int i = 0; i < ranges.length; i += 2) {
      if (ranges[i] < 0 && ranges[i + 1] >= 0) {
        long[] result = new long[ranges.length + 2];
        System.arraycopy(ranges, 0, result, 0, i + 1);
        result[i + 1] = -1;
        System.arraycopy(ranges, i + 1, result, i + 3, ranges.length - i - 1);
        return result;
      }
    }
    return ranges;
  }

  private static LongRangeSet bitwiseAnd(long leftFrom, long leftTo, long rightFrom, long rightTo, BitString globalMask) {
    if (leftFrom == leftTo && rightFrom == rightTo) {
      return point(leftFrom & rightFrom & (globalMask.myBits | ~globalMask.myMask));
    }
    if (leftFrom == leftTo && Long.bitCount(leftFrom+1) == 1) {
      return bitwiseMask(rightFrom, rightTo, leftFrom);
    }
    if (rightFrom == rightTo && Long.bitCount(rightFrom+1) == 1) {
      return bitwiseMask(leftFrom, leftTo, rightFrom);
    }
    BitString leftBits = BitString.fromRange(leftFrom, leftTo);
    BitString rightBits = BitString.fromRange(rightFrom, rightTo);
    return fromBits(leftBits.and(rightBits).and(globalMask));
  }

  /**
   * Returns the range after applying the mask to the input range which looks like 0..01..1 in binary
   * @param from input range start
   * @param to input range end
   * @param mask mask
   * @return range set after applying the mask
   */
  private static LongRangeSet bitwiseMask(long from, long to, long mask) {
    if (to - from > mask) return range(0, mask);
    long min = from & mask;
    long max = to & mask;
    assert min != max;
    if (min < max) return range(min, max);
    return new RangeSet(new long[] {0, max, min, mask});
  }

  /**
   * Creates a set which contains all the numbers satisfying the supplied BitString. The resulting set may
   * contain more values than necessary.
   *
   * @param bits a BitString
   * @return a new LongRangeSet
   */
  private static LongRangeSet fromBits(BitString bits) {
    if (bits.myMask == -1) {
      return point(bits.myBits);
    }
    long from = 0;
    int i = Long.SIZE - 1;
    while (i >= 0 && bits.get(i) != ThreeState.UNSURE) {
      if (bits.get(i) == ThreeState.YES) {
        from = setBit(from, i);
      }
      i--;
    }
    long to = ((i == Long.SIZE - 1 ? 0 : 1L << (i + 1)) - 1) | from;
    int j = 0;
    while(j < i && bits.get(j) != ThreeState.UNSURE) {
      if (bits.get(j) == ThreeState.NO) {
        to = clearBit(to, j);
      }
      j++;
    }
    if(i == j) {
      return point(from).unite(point(to));
    }
    long modBits = -1;
    for (int rem = 0; rem < Long.SIZE; rem++) {
      for (int pos = 0; pos < 6; pos++) {
        ThreeState bit = bits.get(pos);
        if (bit == ThreeState.fromBoolean(!isSet(rem, pos))) {
          modBits = clearBit(modBits, rem);
          break;
        }
      }
    }
    if (from >= 0 && to < 0) {
      from = Long.MIN_VALUE;
      to = Long.MAX_VALUE;
    }
    return from < to ? modRange(from, to, Long.SIZE, modBits) : modRange(to, from, Long.SIZE, modBits);
  }

  private static String formatNumber(long value) {
    if (value == Long.MAX_VALUE) return "Long.MAX_VALUE";
    if (value == Long.MAX_VALUE - 1) return "Long.MAX_VALUE-1";
    if (value == Long.MIN_VALUE) return "Long.MIN_VALUE";
    if (value == Long.MIN_VALUE + 1) return "Long.MIN_VALUE+1";
    if (value == Integer.MAX_VALUE) return "Integer.MAX_VALUE";
    if (value == Integer.MAX_VALUE - 1) return "Integer.MAX_VALUE-1";
    if (value == Integer.MIN_VALUE) return "Integer.MIN_VALUE";
    if (value == Integer.MIN_VALUE + 1) return "Integer.MIN_VALUE+1";
    return String.valueOf(value);
  }
  /**
   * Returns a stream of all values from this range. Be careful: could be huge
   *
   * @return a new stream
   */
  public abstract LongStream stream();

  /**
   * @return an empty set
   */
  public static LongRangeSet empty() {
    return Empty.EMPTY;
  }

  /**
   * @return a set containing all possible long values
   */
  @NotNull
  public static LongRangeSet all() {
    return Range.LONG_RANGE;
  }

  /**
   * Creates a set containing single given value
   *
   * @param value a value to be included into the set
   * @return a new set
   */
  public static LongRangeSet point(long value) {
    return value == 0 ? Point.ZERO : value == 1 ? Point.ONE : new Point(value);
  }

  /**
   * Creates a set containing single value which is equivalent to supplied boxed constant (if its type is supported)
   *
   * @param val constant to create a set from
   * @return new LongRangeSet or null if constant type is unsupported
   */
  @Nullable
  public static LongRangeSet fromConstant(Object val) {
    if (val instanceof Byte || val instanceof Short || val instanceof Integer || val instanceof Long) {
      return point(((Number)val).longValue());
    }
    else if (val instanceof Character) {
      return point(((Character)val).charValue());
    }
    return null;
  }

  /**
   * Creates a new set which contains all the numbers between from (inclusive) and to (inclusive)
   *
   * @param from lower bound
   * @param to upper bound (must be greater or equal to {@code from})
   * @return a new LongRangeSet
   */
  public static LongRangeSet range(long from, long to) {
    return from == to ? point(from) : new Range(from, to);
  }

  /**
   * Creates a new set which contains all the numbers between from (inclusive) and to (inclusive) 
   * which are equal to any of the set bits in supplied bit-mask modulo supplied mod
   * @param from lower bound
   * @param to upper bound (must be greater or equal to {@code from})
   * @param mod modulus; only modulus up to 64 is supported; bigger modulus is not tracked (instead full range will be returned)
   * @param bits a bit-mask which represents allowed remainders: 0b1 represents that only (x % mod == 0) numbers are included;
   *             0b10 represents that only (x % mod == 1) numbers are included and so on.
   * @return a new LongRangeSet
   */
  public static LongRangeSet modRange(long from, long to, long mod, long bits) {
    if (mod <= 0) throw new IllegalArgumentException();
    if (bits == 0) return empty();
    if (mod == 1 || mod > Long.SIZE) return range(from, to);
    int intMod = (int)mod;
    // Adjust from/to: they should point to minimal/maximal value which is actually allowed by mod bits
    long rotatedFrom = rotateRemainders(bits, intMod, remainder(from, intMod));
    from += Long.numberOfTrailingZeros(rotatedFrom);
    int toBit = (remainder(to, intMod) + 1) % intMod;
    long rotatedTo = rotateRemainders(bits, intMod, toBit);
    to -= intMod - (Long.SIZE - Long.numberOfLeadingZeros(rotatedTo));

    if (from > to) return empty();
    if (from == to) return new Point(from);
    // Try to reduce mod if the range is too small 
    long length = to - from;
    if (length > 0 && length <= intMod / 2) {
      for (int newMod = (int)length; newMod <= intMod / 2; newMod++) {
        if (intMod % newMod == 0) {
          long newBits = 0;
          // `to` could be Long.MAX_VALUE; so `i >= from` condition is important to react on possible overflow
          for (long i = from; i >= from && i <= to; i++) {
            if (isSet(bits, remainder(i, intMod))) {
              newBits = setBit(newBits, remainder(i, newMod));
            }
          }
          intMod = newMod;
          bits = newBits;
          if (bits == 0) return empty();
          break;
        }
      }
    }
    // Try to reduce mod if bits are symmetrical
    if (intMod % 2 == 0) {
      int halfMod = intMod / 2;
      long rightHalf = extractBits(bits, 0, halfMod);
      long leftHalf = extractBits(bits, halfMod, halfMod);
      if (rightHalf == leftHalf) {
        return modRange(from, to, halfMod, leftHalf);
      }
    }
    if (Long.bitCount(bits) == intMod) return range(from, to);

    ModRange range = new ModRange(from, to, intMod, bits);
    LongRangeSet fullRange = range(from, to);
    return range.contains(fullRange) ? fullRange : range;
  }

  abstract long[] asRanges();

  static String toString(long from, long to) {
    return formatNumber(from) + (from == to ? "" : (to - from == 1 ? ", " : "..") + formatNumber(to));
  }

  static long minValue(boolean isLong) {
    return isLong ? Long.MIN_VALUE : Integer.MIN_VALUE;
  }

  static long maxValue(boolean isLong) {
    return isLong ? Long.MAX_VALUE : Integer.MAX_VALUE;
  }

  /**
   * @return LongRangeSet describing possible array or string indices (from 0 to Integer.MAX_VALUE)
   */
  public static LongRangeSet indexRange() {
    return Range.INDEX_RANGE;
  }

  /**
   * Creates a range for given type (for primitives and boxed: values range)
   *
   * @param type type to create a range for
   * @return a range or null if type is not supported
   */
  @Nullable
  public static LongRangeSet fromType(PsiType type) {
    if (!(type instanceof PsiPrimitiveType) && !TypeConversionUtil.isPrimitiveWrapper(type)) return null;
    type = PsiPrimitiveType.getOptionallyUnboxedType(type);
    if (type != null) {
      if (type.equals(PsiType.BYTE)) {
        return Range.BYTE_RANGE;
      }
      if (type.equals(PsiType.CHAR)) {
        return Range.CHAR_RANGE;
      }
      if (type.equals(PsiType.SHORT)) {
        return Range.SHORT_RANGE;
      }
      if (type.equals(PsiType.INT)) {
        return Range.INT_RANGE;
      }
      if (type.equals(PsiType.LONG)) {
        return all();
      }
    }
    return null;
  }

  @NotNull
  public static LongRangeSet fromPsiElement(PsiModifierListOwner owner) {
    if (owner == null) return all();
    return StreamEx.of(AnnotationUtil.findAnnotation(owner, JETBRAINS_RANGE), owner.getAnnotation(JETBRAINS_RANGE))
                   .nonNull()
                   .append(AnnotationUtil.findAnnotations(owner, ANNOTATIONS))
                   .map(LongRangeSet::fromAnnotation).foldLeft(all(), LongRangeSet::intersect);
  }

  private static LongRangeSet fromAnnotation(PsiAnnotation annotation) {
    switch (Objects.requireNonNull(annotation.getQualifiedName())) {
      case JETBRAINS_RANGE:
      case CHECKER_RANGE:
        Long from = AnnotationUtil.getLongAttributeValue(annotation, "from");
        Long to = AnnotationUtil.getLongAttributeValue(annotation, "to");
        if(from != null && to != null && to >= from) {
          return range(from, to);
        }
        break;
      case VALIDATION_MIN:
        Long minValue = AnnotationUtil.getLongAttributeValue(annotation, "value");
        if (minValue != null && annotation.findDeclaredAttributeValue("groups") == null) {
          return range(minValue, Long.MAX_VALUE);
        }
        break;
      case VALIDATION_MAX:
        Long maxValue = AnnotationUtil.getLongAttributeValue(annotation, "value");
        if (maxValue != null && annotation.findDeclaredAttributeValue("groups") == null) {
          return range(Long.MIN_VALUE, maxValue);
        }
        break;
      case CHECKER_GTE_NEGATIVE_ONE:
        return range(-1, Long.MAX_VALUE);
      case JSR305_NONNEGATIVE:
      case CHECKER_NON_NEGATIVE:
        return range(0, Long.MAX_VALUE);
      case CHECKER_POSITIVE:
        return range(1, Long.MAX_VALUE);
    }
    return all();
  }

  static LongRangeSet fromRanges(long[] ranges, int bound) {
    if (bound == 0) {
      return Empty.EMPTY;
    }
    else if (bound == 2) {
      return range(ranges[0], ranges[1]);
    }
    else {
      return new RangeSet(Arrays.copyOfRange(ranges, 0, bound));
    }
  }

  /**
   * Creates a LongRangeSet of values {@code x} for which {@code remainders.contains(x % mod)}.
   * May include more values as well.
   * 
   * @param mod a divisor
   * @param remainders set of allowed remainders
   * @return set of values which may produce supplied remainders when divided by mod.
   */
  public static LongRangeSet fromRemainder(long mod, LongRangeSet remainders) {
    if (remainders.isEmpty()) return empty();
    long min = remainders.min() > 0 ? 1 : Long.MIN_VALUE;  
    long max = remainders.max() < 0 ? -1 : Long.MAX_VALUE;
    if (mod > 1 && mod <= Long.SIZE) {
      long bits = remainders.contains(0) ? 1 : 0;      
      for(int rem = 1; rem < mod; rem++) {
        if (remainders.contains(rem) || remainders.contains(rem - mod)) {
          bits = setBit(bits, rem);
        }
      }
      return modRange(min, max, mod, bits);
    }
    return range(min, max);
  }

  static final class Empty extends LongRangeSet {
    static final LongRangeSet EMPTY = new Empty();

    @NotNull
    @Override
    public LongRangeSet subtract(@NotNull LongRangeSet other) {
      return this;
    }

    @NotNull
    @Override
    public LongRangeSet intersect(@NotNull LongRangeSet other) {
      return this;
    }

    @NotNull
    @Override
    public LongRangeSet unite(@NotNull LongRangeSet other) {
      return other;
    }

    @Override
    public long min() {
      throw new NoSuchElementException();
    }

    @Override
    public long max() {
      throw new NoSuchElementException();
    }

    @Override
    public boolean intersects(LongRangeSet other) {
      return false;
    }

    @Override
    public boolean contains(long value) {
      return false;
    }

    @Override
    public boolean contains(LongRangeSet other) {
      return other.isEmpty();
    }

    @Override
    public String getPresentationText(PsiType type) {
      return "unknown";
    }

    @Override
    public boolean isCardinalityBigger(long cutoff) {
      return cutoff < 0;
    }

    @Override
    public LongRangeSet castTo(PsiPrimitiveType type) {
      if (TypeConversionUtil.isIntegralNumberType(type)) {
        return this;
      }
      throw new IllegalArgumentException(type.toString());
    }

    @NotNull
    @Override
    public LongRangeSet abs(boolean isLong) {
      return this;
    }

    @NotNull
    @Override
    public LongRangeSet negate(boolean isLong) {
      return this;
    }

    @NotNull
    @Override
    public LongRangeSet plus(LongRangeSet other, boolean isLong) {
      return this;
    }

    @Override
    public LongRangeSet mul(LongRangeSet multiplier, boolean isLong) {
      return this;
    }

    @NotNull
    @Override
    public LongRangeSet mod(LongRangeSet divisor) {
      return empty();
    }

    @Override
    public LongStream stream() {
      return LongStream.empty();
    }

    @Override
    long[] asRanges() {
      return new long[0];
    }

    @Override
    public int hashCode() {
      return 2154231;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this;
    }

    @Override
    public String toString() {
      return "{}";
    }
  }

  static final class Point extends LongRangeSet {
    static final Point ZERO = new Point(0);
    static final Point ONE = new Point(1);

    final long myValue;

    Point(long value) {
      myValue = value;
    }

    @Override
    public String getPresentationText(PsiType type) {
      return formatNumber(myValue);
    }

    @Override
    public boolean isCardinalityBigger(long cutoff) {
      return cutoff < 1;
    }

    @NotNull
    @Override
    public LongRangeSet subtract(@NotNull LongRangeSet other) {
      return other.contains(myValue) ? Empty.EMPTY : this;
    }

    @NotNull
    @Override
    public LongRangeSet intersect(@NotNull LongRangeSet other) {
      return other.contains(myValue) ? this : Empty.EMPTY;
    }

    @Override
    public long min() {
      return myValue;
    }

    @Override
    public long max() {
      return myValue;
    }

    @Override
    public Long getConstantValue() {
      return myValue;
    }

    @NotNull
    @Override
    public LongRangeSet unite(@NotNull LongRangeSet other) {
      if (other.isEmpty() || other == this) return this;
      if (other.contains(myValue)) return other;
      if (other instanceof Point) {
        long value1 = Math.min(myValue, ((Point)other).myValue);
        long value2 = Math.max(myValue, ((Point)other).myValue);
        return value1 + 1 == value2
               ? range(value1, value2)
               : new RangeSet(new long[]{value1, value1, value2, value2});
      }
      if (other instanceof ModRange) {
        return other.unite(this);
      }
      if (other instanceof Range) {
        if (myValue < other.min()) {
          return myValue + 1 == other.min()
                 ? range(myValue, other.max())
                 : new RangeSet(new long[]{myValue, myValue, other.min(), other.max()});
        }
        else {
          assert myValue > other.max();
          return myValue - 1 == other.max()
                 ? range(other.min(), myValue)
                 : new RangeSet(new long[]{other.min(), other.max(), myValue, myValue});
        }
      }
      long[] longs = other.asRanges();
      int pos = -Arrays.binarySearch(longs, myValue) - 1;
      assert pos >= 0 && pos % 2 == 0;
      boolean touchLeft = pos > 0 && longs[pos - 1] + 1 == myValue;
      boolean touchRight = pos < longs.length - 1 && myValue + 1 == longs[pos];
      long[] result;
      if (touchLeft) {
        if (touchRight) {
          result = new long[longs.length - 2];
          System.arraycopy(longs, 0, result, 0, pos - 1);
          System.arraycopy(longs, pos + 1, result, pos - 1, longs.length - pos - 1);
        }
        else {
          result = longs.clone();
          result[pos - 1] = myValue;
        }
      }
      else {
        if (touchRight) {
          result = longs.clone();
          result[pos] = myValue;
        }
        else {
          result = new long[longs.length + 2];
          System.arraycopy(longs, 0, result, 0, pos);
          result[pos] = result[pos + 1] = myValue;
          System.arraycopy(longs, pos, result, pos + 2, longs.length - pos);
        }
      }
      return fromRanges(result, result.length);
    }

    @Override
    public boolean intersects(LongRangeSet other) {
      return other.contains(myValue);
    }

    @Override
    public boolean contains(long value) {
      return myValue == value;
    }

    @Override
    public boolean contains(LongRangeSet other) {
      return other.isEmpty() || equals(other);
    }

    @Override
    public LongRangeSet castTo(PsiPrimitiveType type) {
      if (PsiType.LONG.equals(type)) return this;
      long newValue;
      if (PsiType.CHAR.equals(type)) {
        newValue = (char)myValue;
      }
      else if (PsiType.INT.equals(type)) {
        newValue = (int)myValue;
      }
      else if (PsiType.SHORT.equals(type)) {
        newValue = (short)myValue;
      }
      else if (PsiType.BYTE.equals(type)) {
        newValue = (byte)myValue;
      }
      else {
        throw new IllegalArgumentException(type.toString());
      }
      return newValue == myValue ? this : point(newValue);
    }

    @NotNull
    @Override
    public LongRangeSet abs(boolean isLong) {
      return myValue >= 0 || myValue == minValue(isLong) ? this : point(-myValue);
    }

    @NotNull
    @Override
    public LongRangeSet negate(boolean isLong) {
      return myValue == minValue(isLong) ? this : point(-myValue);
    }

    @NotNull
    @Override
    public LongRangeSet plus(LongRangeSet other, boolean isLong) {
      if (other.isEmpty()) return other;
      if (other instanceof Point) {
        long res = myValue + ((Point)other).myValue;
        return point(isLong ? res : (int)res);
      }
      return other.plus(this, isLong);
    }

    @Override
    public LongRangeSet mul(LongRangeSet multiplier, boolean isLong) {
      if (multiplier.isEmpty()) return multiplier;
      if (myValue == 0) return this;
      if (myValue == 1) return multiplier;
      if (myValue == -1) return multiplier.negate(isLong);
      if (multiplier instanceof Point) {
        long val = ((Point)multiplier).myValue;
        long res = myValue * val;
        return point(isLong ? res : (int)res);
      }
      boolean overflow = false;
      long min = multiplier.min();
      long max = multiplier.max();
      if (isLong) {
        try {
          min = Math.multiplyExact(min, myValue);
          max = Math.multiplyExact(max, myValue);
        }
        catch (ArithmeticException e) {
          overflow = true;
        }
      }
      else {
        min *= myValue;
        max *= myValue;
        if (min != (int)min || max != (int)max) {
          overflow = true;
        }
      }
      LongRangeSet result;
      if (overflow) {
        result = isLong ? Range.LONG_RANGE : Range.INT_RANGE;
      }
      else {
        result = min > max ? range(max, min) : range(min, max);
      }
      long abs = Math.abs(myValue);
      if (overflow) {
        abs = Long.lowestOneBit(abs);
      }
      if (abs < 0 || (abs > Long.SIZE && Long.bitCount(abs) == 1)) {
        abs = Long.SIZE;
      }
      return modRange(result.min(), result.max(), abs, 1L);
    }

    @NotNull
    @Override
    public LongRangeSet mod(LongRangeSet divisor) {
      if (divisor.isEmpty() || divisor.equals(ZERO)) return empty();
      if (myValue == 0) return this;
      if (divisor instanceof Point) {
        return LongRangeSet.point(myValue % ((Point)divisor).myValue);
      }
      if (myValue != Long.MIN_VALUE) {
        long abs = Math.abs(myValue);
        if (!divisor.intersects(LongRangeSet.range(-abs, abs))) {
          // like 10 % [15..20] == 10 regardless on exact divisor value
          return this;
        }
      }
      LongRangeSet addend = empty();
      if (divisor.contains(Long.MIN_VALUE)) {
        divisor = divisor.subtract(point(Long.MIN_VALUE));
        addend = point(myValue);
      }
      long max = Math.max(0, Math.max(Math.abs(divisor.min()), Math.abs(divisor.max())) - 1);
      if (myValue < 0) {
        return LongRangeSet.range(Math.max(myValue, -max), 0).unite(addend);
      } else {
        // 10 % [-4..7] is [0..6], but 10 % [-30..30] is [0..10]
        return LongRangeSet.range(0, Math.min(myValue, max)).unite(addend);
      }
    }

    @Override
    public LongStream stream() {
      return LongStream.of(myValue);
    }

    @Override
    long[] asRanges() {
      return new long[] {myValue, myValue};
    }

    @Override
    public int hashCode() {
      return Long.hashCode(myValue);
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      return o instanceof Point && myValue == ((Point)o).myValue;
    }

    @Override
    public String toString() {
      return "{" + formatNumber(myValue) + "}";
    }
  }

  static class Range extends LongRangeSet {
    static final Range BYTE_RANGE = new Range(Byte.MIN_VALUE, Byte.MAX_VALUE);
    static final Range CHAR_RANGE = new Range(Character.MIN_VALUE, Character.MAX_VALUE);
    static final Range SHORT_RANGE = new Range(Short.MIN_VALUE, Short.MAX_VALUE);
    static final Range INT_RANGE = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);
    static final Range LONG_RANGE = new Range(Long.MIN_VALUE, Long.MAX_VALUE);
    static final Range INDEX_RANGE = new Range(0, Integer.MAX_VALUE);

    final long myFrom; // inclusive
    final long myTo; // inclusive

    Range(long from, long to) {
      if (to <= from) { // to == from => must be Point
        throw new IllegalArgumentException(to + "<=" + from);
      }
      myFrom = from;
      myTo = to;
    }

    @Override
    public String getPresentationText(PsiType type) {
      LongRangeSet set = fromType(type);
      if (set != null) {
        if (set.min() == myFrom) {
          if (set.max() == myTo) {
            return "any value";
          }
          return "<= " + LongRangeSet.formatNumber(myTo);
        }
        else if (set.max() == myTo) {
          return ">= " + LongRangeSet.formatNumber(myFrom);
        }
      }
      if (myTo - myFrom == 1) {
        return myFrom + " or " + myTo;
      }
      return "in " + toString();
    }

    @Override
    public boolean isCardinalityBigger(long cutoff) {
      long diff = myTo - myFrom;
      return diff < 0 || diff >= cutoff;
    }

    @NotNull
    @Override
    public LongRangeSet subtract(@NotNull LongRangeSet other) {
      if (other.isEmpty()) return this;
      if (other == this) return Empty.EMPTY;
      if (other instanceof Point) {
        long value = ((Point)other).myValue;
        if (value < myFrom || value > myTo) return this;
        if (value == myFrom) return range(myFrom + 1, myTo);
        if (value == myTo) return range(myFrom, myTo - 1);
        return new RangeSet(new long[]{myFrom, value - 1, value + 1, myTo});
      }
      if (other instanceof Range) {
        LongRangeSet toJoin = Empty.EMPTY;
        long from = ((Range)other).myFrom;
        long to = ((Range)other).myTo;
        if (to < myFrom || from > myTo) return this;
        if (other instanceof ModRange) {
          ModRange modRange = (ModRange)other;
          long newBits = ~modRange.myBits;
          if (modRange.myMod < 64) {
            newBits &= (1L << modRange.myMod) - 1;
          }
          toJoin = modRange(Math.max(from, myFrom), Math.min(to, myTo), modRange.myMod, newBits);
        }
        if (from <= myFrom && to >= myTo) return toJoin;
        if (from > myFrom && to < myTo) {
          return new RangeSet(new long[]{myFrom, from - 1, to + 1, myTo}).unite(toJoin);
        }
        if (from <= myFrom) {
          return range(to + 1, myTo).unite(toJoin);
        }
        assert to >= myTo;
        return range(myFrom, from - 1).unite(toJoin);
      }
      long[] ranges = ((RangeSet)other).myRanges;
      LongRangeSet result = this;
      for (int i = 0; i < ranges.length; i += 2) {
        result = result.subtract(range(ranges[i], ranges[i + 1]));
        if (result.isEmpty()) return result;
      }
      return result;
    }

    @NotNull
    @Override
    public LongRangeSet intersect(@NotNull LongRangeSet other) {
      if (other == this) return this;
      if (other.isEmpty()) return other;
      if ((other instanceof ModRange && !(this instanceof ModRange)) || other instanceof Point) {
        return other.intersect(this);
      }
      if (other instanceof Range) {
        long from = ((Range)other).myFrom;
        long to = ((Range)other).myTo;
        if (from <= myFrom && to >= myTo) return this;
        if (from >= myFrom && to <= myTo) return other;
        if (from < myFrom) {
          from = myFrom;
        }
        if (to > myTo) {
          to = myTo;
        }
        return from <= to ? range(from, to) : Empty.EMPTY;
      }
      long[] ranges = ((RangeSet)other).myRanges;
      long[] result = new long[ranges.length];
      int index = 0;
      for (int i = 0; i < ranges.length; i += 2) {
        long[] res = intersect(range(ranges[i], ranges[i + 1])).asRanges();
        System.arraycopy(res, 0, result, index, res.length);
        index += res.length;
      }
      return fromRanges(result, index);
    }

    @NotNull
    @Override
    public LongRangeSet unite(@NotNull LongRangeSet other) {
      if (other.isEmpty() || other == this) return this;
      if (other instanceof Point) {
        return other.unite(this);
      }
      if (other instanceof Range) {
        if (other.min() <= max() && min() <= other.max() ||
            (other.max() < min() && other.max() + 1 == min()) ||
            (other.min() > max() && max() + 1 == other.min())) {
          return range(Math.min(min(), other.min()), Math.max(max(), other.max()));
        }
        if (other.max() < min()) {
          return new RangeSet(new long[]{other.min(), other.max(), min(), max()});
        }
        return new RangeSet(new long[]{min(), max(), other.min(), other.max()});
      }
      long[] longs = other.asRanges();
      int minIndex = Arrays.binarySearch(longs, min());
      if (minIndex < 0) {
        minIndex = -minIndex - 1;
        if (minIndex % 2 == 0 && minIndex > 0 && longs[minIndex - 1] + 1 == min()) {
          minIndex--;
        }
      }
      else if (minIndex % 2 == 0) {
        minIndex++;
      }
      int maxIndex = Arrays.binarySearch(longs, max());
      if (maxIndex < 0) {
        maxIndex = -maxIndex - 1;
        if (maxIndex % 2 == 0 && maxIndex < longs.length && max() + 1 == longs[maxIndex]) {
          maxIndex++;
        }
      }
      else if (maxIndex % 2 == 0) {
        maxIndex++;
      }
      long[] result = new long[longs.length + 2];
      System.arraycopy(longs, 0, result, 0, minIndex);
      int pos = minIndex;
      if (minIndex % 2 == 0) {
        result[pos++] = min();
      }
      if (maxIndex % 2 == 0) {
        result[pos++] = max();
      }
      System.arraycopy(longs, maxIndex, result, pos, longs.length - maxIndex);
      return fromRanges(result, longs.length + pos - maxIndex);
    }

    @Override
    public long min() {
      return myFrom;
    }

    @Override
    public long max() {
      return myTo;
    }

    @Override
    public boolean intersects(LongRangeSet other) {
      if (other.isEmpty()) return false;
      if (other instanceof RangeSet) {
        long[] otherRanges = ((RangeSet)other).myRanges;
        for (int i = 0; i < otherRanges.length && otherRanges[i] <= myTo; i += 2) {
          if (myTo >= otherRanges[i] && myFrom <= otherRanges[i + 1]) return true;
        }
        return false;
      }
      return myTo >= other.min() && myFrom <= other.max();
    }

    @Override
    public boolean contains(long value) {
      return myFrom <= value && myTo >= value;
    }

    @Override
    public boolean contains(LongRangeSet other) {
      return other.isEmpty() || other.min() >= myFrom && other.max() <= myTo;
    }

    @Override
    public LongRangeSet castTo(PsiPrimitiveType type) {
      if (PsiType.LONG.equals(type)) return this;
      if (PsiType.BYTE.equals(type)) {
        LongRangeSet result = mask(Byte.SIZE, type);
        assert BYTE_RANGE.contains(result) : this;
        return result;
      }
      if (PsiType.SHORT.equals(type)) {
        LongRangeSet result = mask(Short.SIZE, type);
        assert SHORT_RANGE.contains(result) : this;
        return result;
      }
      if (PsiType.INT.equals(type)) {
        LongRangeSet result = mask(Integer.SIZE, type);
        assert INT_RANGE.contains(result) : this;
        return result;
      }
      if (PsiType.CHAR.equals(type)) {
        if (myFrom <= Character.MIN_VALUE && myTo >= Character.MAX_VALUE) return CHAR_RANGE;
        if (myFrom >= Character.MIN_VALUE && myTo <= Character.MAX_VALUE) return this;
        return bitwiseAnd(point(0xFFFF));
      }
      throw new IllegalArgumentException(type.toString());
    }

    @NotNull
    private LongRangeSet mask(int size, PsiPrimitiveType type) {
      long addend = 1L << (size - 1);
      if (myFrom <= -addend && myTo >= addend - 1) return Objects.requireNonNull(fromType(type));
      if (myFrom >= -addend && myTo <= addend - 1) return this;
      long mask = (1L << size) - 1;
      return plus(myFrom, myTo, addend, addend, true).bitwiseAnd(point(mask)).plus(point(-addend), true);
    }

    @NotNull
    @Override
    public LongRangeSet abs(boolean isLong) {
      if (myFrom >= 0) return this;
      long minValue = minValue(isLong);
      long low = myFrom, hi = myTo;
      if (low <= minValue) {
        low = minValue + 1;
      }
      if (myTo <= 0) {
        hi = -low;
        low = -myTo;
      }
      else {
        hi = Math.max(-low, hi);
        low = 0;
      }
      if (low > hi) {
        return isLong ? LONG_RANGE : INT_RANGE;
      }
      if (myFrom <= minValue) {
        return new RangeSet(new long[]{minValue, minValue, low, hi});
      }
      else {
        return new Range(low, hi);
      }
    }

    @NotNull
    @Override
    public LongRangeSet negate(boolean isLong) {
      long minValue = minValue(isLong);
      if (myFrom <= minValue) {
        if (myTo >= maxValue(isLong)) {
          return isLong ? LONG_RANGE : INT_RANGE;
        }
        return new RangeSet(new long[]{minValue, minValue, -myTo, -(minValue + 1)});
      }
      return new Range(-myTo, -myFrom);
    }

    @NotNull
    @Override
    public LongRangeSet plus(LongRangeSet other, boolean isLong) {
      if (other.isEmpty()) return other;
      if (isLong && equals(LONG_RANGE) || !isLong && equals(INT_RANGE)) return this;
      if (other instanceof Point || other instanceof Range || (other instanceof RangeSet && ((RangeSet)other).myRanges.length > 6)) {
        return plus(myFrom, myTo, other.min(), other.max(), isLong);
      }
      long[] ranges = other.asRanges();
      LongRangeSet result = empty();
      for (int i = 0; i < ranges.length; i += 2) {
        result = result.unite(plus(myFrom, myTo, ranges[i], ranges[i + 1], isLong));
      }
      return result;
    }

    @Override
    public LongRangeSet mul(LongRangeSet multiplier, boolean isLong) {
      if (multiplier.isEmpty()) return multiplier;
      if (multiplier instanceof Point) return multiplier.mul(this, isLong);
      return isLong ? LONG_RANGE : INT_RANGE;
    }

    @NotNull
    private static LongRangeSet plus(long from1, long to1, long from2, long to2, boolean isLong) {
      long len1 = to1 - from1; // may overflow
      long len2 = to2 - from2; // may overflow
      if ((len1 < 0 && len2 < 0) || ((len1 < 0 || len2 < 0) && len1 + len2 + 1 >= 0)) { // total length more than 2^64
        return isLong ? LONG_RANGE : INT_RANGE;
      }
      long from = from1 + from2;
      long to = to1 + to2;
      if (!isLong) {
        if (to - from + 1 >= 0x1_0000_0000L) {
          return INT_RANGE;
        }
        from = (int)from;
        to = (int)to;
      }
      if (to < from) {
        return new RangeSet(new long[]{minValue(isLong), to, from, maxValue(isLong)});
      }
      else {
        return range(from, to);
      }
    }

    @NotNull
    @Override
    public LongRangeSet mod(LongRangeSet divisor) {
      if (divisor.isEmpty() || divisor.equals(Point.ZERO)) return empty();
      if (divisor instanceof Point && ((Point)divisor).myValue == Long.MIN_VALUE) {
        return this.contains(Long.MIN_VALUE) ? this.subtract(divisor).unite(Point.ZERO) : this;
      }
      if (divisor.contains(Long.MIN_VALUE)) {
        return possibleMod();
      }
      long min = divisor.min();
      long max = divisor.max();
      long maxDivisor = Math.max(Math.abs(min), Math.abs(max));
      long minDivisor = min > 0 ? min : max < 0 ? Math.abs(max) : 0;
      if (!intersects(LongRangeSet.range(Long.MIN_VALUE, -minDivisor)) &&
          !intersects(LongRangeSet.range(minDivisor, Long.MAX_VALUE))) {
        return this;
      }
      return possibleMod().intersect(range(-maxDivisor + 1, maxDivisor - 1));
    }

    private LongRangeSet possibleMod() {
      if(contains(0)) return this;
      if(min() > 0) return range(0, max());
      return range(min(), 0);
    }

    @Override
    public LongStream stream() {
      return LongStream.rangeClosed(myFrom, myTo);
    }

    @Override
    long[] asRanges() {
      return new long[] {myFrom, myTo};
    }

    @Override
    public int hashCode() {
      return Long.hashCode(myFrom) * 1337 + Long.hashCode(myTo);
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      return o != null && o.getClass() == getClass() && myFrom == ((Range)o).myFrom && myTo == ((Range)o).myTo;
    }

    @Override
    public String toString() {
      return "{" + toString(myFrom, myTo) + "}";
    }
  }

  static final class ModRange extends Range {
    private final int myMod;
    private final long myBits;

    ModRange(long from, long to, int mod, long bits) {
      super(from, to);
      assert mod > 1 && mod <= Long.SIZE;
      this.myMod = mod;
      this.myBits = bits;
      assert (bits & (~getMask())) == 0 : "bits outside of mask should be zero";
      assert bits != getMask() : "at least one bit in mask should be zero, otherwise simple Range could be used";
    }

    @Override
    public String getPresentationText(PsiType type) {
      LongRangeSet set = fromType(type);
      if (set != null) {
        set = modRange(set.min(), set.max(), myMod, myBits);
        String prefix = null;
        if (set.min() == myFrom) {
          prefix = set.max() == myTo ? "" : "<= " + LongRangeSet.formatNumber(myTo) + "; ";
        }
        else if (set.max() == myTo) {
          prefix = ">= " + LongRangeSet.formatNumber(myFrom) + "; ";
        }
        if (prefix != null) {
          return prefix + getSuffix();
        }
      }
      return "in " + super.toString() + "; " + getSuffix();
    }

    @Override
    public boolean isCardinalityBigger(long cutoff) {
      long bottom = myFrom - 1 - remainder(myFrom - 1, myMod) + myMod;
      long top = myTo - remainder(myTo, myMod);
      if (bottom >= myFrom && top > bottom && myTo >= top) {
        int count = Long.bitCount(myBits);
        long wholeCount = (top / myMod - bottom / myMod) * count;
        if (wholeCount < 0 || wholeCount > cutoff) return true;
        for (long i = myFrom; i < bottom; i++) {
          if (isSet(myBits, remainder(i, myMod))) wholeCount++;
        }
        for (long i = top; i <= myTo; i++) {
          if (isSet(myBits, remainder(i, myMod))) wholeCount++;
        }
        return wholeCount < 0 || wholeCount > cutoff;
      }
      return stream().limit(Math.max(0, cutoff + 1)).count() > cutoff;
    }

    @Override
    public boolean contains(long value) {
      return super.contains(value) && isSet(myBits, remainder(value, myMod));
    }

    @Override
    public @NotNull LongRangeSet subtract(@NotNull LongRangeSet other) {
      return super.subtract(other);
    }

    @NotNull
    @Override
    public LongRangeSet intersect(@NotNull LongRangeSet other) {
      LongRangeSet intersection = super.intersect(other);
      if (intersection instanceof Range || intersection instanceof Point) {
        long bits = myBits;
        int mod = myMod;
        if (other instanceof ModRange) {
          ModRange modRange = (ModRange)other;
          int lcm = lcm(modRange.myMod);
          if (lcm <= Long.SIZE) {
            bits = widenBits(lcm) & modRange.widenBits(lcm);
            mod = (byte)lcm;
          }
          else if (modRange.myMod > myMod) {
            bits = modRange.myBits; // new LCM doesn't fit the Long.SIZE: just select bigger mod
            mod = modRange.myMod;
          }
        }
        return modRange(intersection.min(), intersection.max(), mod, bits);
      }
      if (intersection instanceof RangeSet) {
        long min = intersection.min();
        long max = intersection.max();
        long diff = max - min;
        if (diff > 0 && diff < Long.SIZE) {
          for (byte newMod = (byte)(diff + 1); newMod <= Long.SIZE; newMod++) {
            if (newMod % myMod == 0) {
              long bits = widenBits(newMod);
              for (long pos = min; pos <= max; pos++) {
                int bit = remainder(pos, newMod);
                if (isSet(bits, bit) && !intersection.contains(pos)) {
                  bits = clearBit(bits, bit);
                }
              }
              return modRange(min, max, newMod, bits);
            }
          }
        }
      }
      return intersection;
    }

    @Override
    public boolean intersects(LongRangeSet other) {
      if (other instanceof Point) {
        return contains(((Point)other).myValue);
      }
      if (other instanceof ModRange) {
        ModRange modRange = (ModRange)other;
        int lcm = lcm(modRange.myMod);
        if (lcm <= Long.SIZE && (modRange.widenBits(lcm) & widenBits(lcm)) == 0) return false;
      }
      long[] otherRanges = other.asRanges();
      for (int i = 0; i < otherRanges.length && otherRanges[i] <= myTo; i += 2) {
        if (myTo >= otherRanges[i] && myFrom <= otherRanges[i + 1] &&
            !modRange(otherRanges[i], otherRanges[i + 1], myMod, myBits).isEmpty()) {
          return true;
        }
      }
      return false;
    }

    @NotNull
    @Override
    public LongRangeSet unite(@NotNull LongRangeSet other) {
      if (other instanceof ModRange) {
        ModRange modRange = (ModRange)other;
        int lcm = lcm(modRange.myMod);
        if (lcm <= Long.SIZE) {
          long bits = widenBits(lcm) | modRange.widenBits(lcm);
          if (myTo >= modRange.myFrom && myFrom <= modRange.myTo ||
              myTo < modRange.myFrom && modRange(myTo + 1, modRange.myFrom - 1, lcm, bits).isEmpty() ||
              modRange.myTo < myFrom && modRange(modRange.myTo + 1, myFrom - 1, lcm, bits).isEmpty()) {
            return modRange(Math.min(myFrom, modRange.myFrom), Math.max(myTo, modRange.myTo), lcm, bits);
          }
        }
      }
      if (other instanceof Point) {
        long val = ((Point)other).myValue;
        if (isSet(myBits, remainder(val, myMod))) {
          if (val >= myFrom && val <= myTo) return this;
          if (val < myFrom && modRange(val + 1, myFrom - 1, myMod, myBits).isEmpty() ||
              val > myTo && modRange(myTo + 1, val - 1, myMod, myBits).isEmpty()) {
            return modRange(Math.min(myFrom, val), Math.max(myTo, val), myMod, myBits);
          }
        }
        return other.unite(range(myFrom, myTo));
      }
      return super.unite(other);
    }

    @Override
    public boolean contains(LongRangeSet other) {
      if (other instanceof ModRange) {
        ModRange modRange = (ModRange)other;
        if (modRange.myFrom < myFrom || modRange.myTo > myTo) return false;
        int lcm = lcm(modRange.myMod);
        if (lcm <= Long.SIZE) {
          return (~widenBits(lcm) & modRange.widenBits(lcm)) == 0;
        }
      }
      long[] ranges = other.asRanges();
      for (int i = 0; i < ranges.length; i += 2) {
        if (!contains(ranges[i], ranges[i + 1])) return false;
      }
      return true;
    }

    @NotNull
    @Override
    public LongRangeSet negate(boolean isLong) {
      LongRangeSet negated = super.negate(isLong);
      if (negated instanceof Range) {
        // Leave 0 at the place, reverse the rest
        long negatedBits = (Long.reverse(myBits & -2) >>> (Long.SIZE - myMod - 1)) | (myBits & 1);
        return modRange(negated.min(), negated.max(), myMod, negatedBits);
      }
      return negated;
    }

    @NotNull
    @Override
    public LongRangeSet plus(LongRangeSet other, boolean isLong) {
      LongRangeSet set = super.plus(other, isLong);
      if (other instanceof Point ||
          other instanceof ModRange && ((ModRange)other).myMod == myMod && Long.bitCount(((ModRange)other).myBits) == 1) {
        long[] ranges = set.asRanges();
        LongRangeSet result = empty();
        for (int i = 0; i < ranges.length; i += 2) {
          result = result.unite(plus(ranges[i], ranges[i + 1], other, isLong));
        }
        return result;
      }
      return set;
    }

    private LongRangeSet plus(long min, long max, LongRangeSet other, boolean isLong) {
      if (Integer.bitCount(myMod) == 1 || !subtractionMayOverflow(other.negate(isLong), isLong)) {
        int bit = other instanceof Point ? remainder(((Point)other).myValue, myMod) : Long.numberOfTrailingZeros(((ModRange)other).myBits);
        long bits = rotateRemainders(myBits, myMod, myMod - bit);
        return modRange(min, max, myMod, bits);
      }
      return range(min, max);
    }

    @NotNull
    @Override
    public LongRangeSet mod(LongRangeSet divisor) {
      if (divisor instanceof Point) {
        if (((Point)divisor).myValue > 1 && ((Point)divisor).myValue <= Long.SIZE) {
          int divisorValue = (int)((Point)divisor).myValue;
          int lcm = lcm(divisorValue);
          if (lcm <= Long.SIZE) {
            long from = Math.min(0, Math.max(myFrom, -divisorValue + 1));
            long to = Math.max(0, Math.min(myTo, divisorValue - 1));
            long possibleMods = widenBits(lcm);
            while (Long.SIZE - Long.numberOfLeadingZeros(possibleMods) > divisorValue) {
              possibleMods = extractBits(possibleMods, divisorValue, Long.SIZE) | 
                             extractBits(possibleMods, 0, divisorValue);
            }
            return modRange(from, to, divisorValue, possibleMods);
          }
        }
      }
      return range(myFrom, myTo).mod(divisor);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass() || !super.equals(o)) return false;
      return myMod == ((ModRange)o).myMod && myBits == ((ModRange)o).myBits;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), myMod, myBits);
    }

    @Override
    public String toString() {
      return super.toString() + ": " + getSuffix();
    }

    private String getSuffix() {
      String suffix;
      if (myMod == 2) {
        suffix = myBits == 1 ? "even" : "odd";
      }
      else if (myBits == 1) {
        suffix = "divisible by " + myMod;
      }
      else {
        suffix = IntStreamEx.of(BitSet.valueOf(new long[]{myBits})).joining(", ", "<", "> mod " + myMod);
      }
      return suffix;
    }

    @Override
    public LongStream stream() {
      return super.stream().filter(this::contains);
    }

    private boolean contains(long from, long to) {
      if (from < myFrom || to > myTo) return false;
      if (to == from) return contains(from);
      if (to - from < 0 || to - from >= myMod) return false;
      int fromBit = remainder(from, myMod);
      int toBit = remainder(to, myMod);
      if (fromBit < toBit) {
        return Long.numberOfTrailingZeros(~(myBits >>> fromBit)) > toBit - fromBit;
      }
      return Long.numberOfTrailingZeros(~myBits) > toBit &&
             Long.SIZE - Long.numberOfLeadingZeros((~myBits) & getMask()) <= fromBit;
    }

    private long widenBits(int targetMod) {
      assert targetMod <= Long.SIZE && targetMod % myMod == 0;
      long result = myBits;
      for (int shift = targetMod - myMod; shift > 0; shift -= myMod) {
        result |= myBits << shift;
      }
      return result;
    }
    
    @Override
    BitString getBitwiseMask() {
      int knownBits = Long.numberOfTrailingZeros(myMod);
      int powerOfTwo = 1 << knownBits;
      long result = -1;
      long mask = powerOfTwo - 1;
      for (int rem = 0; rem < myMod; rem++) {
        if (isSet(myBits, rem)) {
          int setBits = rem % powerOfTwo;
          if (result != -1) {
            long diffBits = result ^ setBits;
            mask &= ~diffBits;
          }
          result = setBits;
        }
      }
      BitString intersection = new BitString(result, mask).intersect(super.getBitwiseMask());
      assert intersection != null;
      return intersection;
    }

    private long getMask() {
      return -1L >>> (Long.SIZE - myMod);
    }

    private int lcm(int otherMod) {
      return myMod * otherMod / gcd(myMod, otherMod);
    }
  }

  static final class RangeSet extends LongRangeSet {
    final long[] myRanges;

    RangeSet(long[] ranges) {
      if (ranges.length < 4 || ranges.length % 2 != 0) {
        // 0 ranges = Empty; 1 range = Range
        throw new IllegalArgumentException("Bad length: " + ranges.length + " " + Arrays.toString(ranges));
      }
      for (int i = 0; i < ranges.length; i += 2) {
        if (ranges[i + 1] < ranges[i]) {
          throw new IllegalArgumentException("Bad sub-range #" + (i / 2) + " " + Arrays.toString(ranges));
        }
        if (i > 0 && (ranges[i - 1] == Long.MAX_VALUE || 1 + ranges[i - 1] > ranges[i])) {
          throw new IllegalArgumentException("Bad sub-ranges #" + (i / 2 - 1) + " and #" + (i / 2) + " " + Arrays.toString(ranges));
        }
      }
      myRanges = ranges;
    }

    @Override
    BitString getBitwiseMask() {
      BitString result = null;
      for (int i = 0; i < myRanges.length; i += 2) {
        BitString newBits = BitString.fromRange(myRanges[i], myRanges[i + 1]);
        result = result == null ? newBits : result.unite(newBits);
      }
      return result;
    }

    @NotNull
    @Override
    public LongRangeSet subtract(@NotNull LongRangeSet other) {
      if (other.isEmpty()) return this;
      if (other == this) return Empty.EMPTY;
      long[] result = new long[myRanges.length + other.asRanges().length];
      int index = 0;
      for (int i = 0; i < myRanges.length; i += 2) {
        LongRangeSet res = range(myRanges[i], myRanges[i + 1]).subtract(other);
        long[] ranges = res.asRanges();
        System.arraycopy(ranges, 0, result, index, ranges.length);
        index += ranges.length;
      }
      return fromRanges(result, index);
    }

    @NotNull
    @Override
    public LongRangeSet intersect(@NotNull LongRangeSet other) {
      if (other == this) return this;
      if (other.isEmpty()) return other;
      if (other instanceof Point || other instanceof Range) {
        return other.intersect(this);
      }
      return subtract(all().subtract(other));
    }

    @NotNull
    @Override
    public LongRangeSet unite(@NotNull LongRangeSet other) {
      if (!(other instanceof RangeSet)) {
        return other.unite(this);
      }
      if(other == this) return this;
      if(other.contains(this)) return other;
      if(this.contains(other)) return this;
      LongRangeSet result = other;
      for(int i=0; i<myRanges.length; i+=2) {
        result = range(myRanges[i], myRanges[i+1]).unite(result);
      }
      return result;
    }

    @Override
    public long min() {
      return myRanges[0];
    }

    @Override
    public long max() {
      return myRanges[myRanges.length - 1];
    }

    @Override
    public boolean intersects(LongRangeSet other) {
      if (other.isEmpty()) return false;
      if (other instanceof Point) {
        return contains(((Point)other).myValue);
      }
      long[] otherRanges = other.asRanges();
      int a = 0, b = 0;
      while (true) {
        long aFrom = myRanges[a];
        long aTo = myRanges[a + 1];
        long bFrom = otherRanges[b];
        long bTo = otherRanges[b + 1];
        if (aFrom <= bTo && bFrom <= aTo) return true;
        if (aFrom > bTo) {
          b += 2;
          if (b >= otherRanges.length) return false;
        }
        else {
          a += 2;
          if (a >= myRanges.length) return false;
        }
      }
    }

    @Override
    public boolean contains(long value) {
      for (int i = 0; i < myRanges.length; i += 2) {
        if (value >= myRanges[i] && value <= myRanges[i + 1]) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean contains(LongRangeSet other) {
      if (other.isEmpty() || other == this) return true;
      if (other instanceof Point) {
        return contains(((Point)other).myValue);
      }
      LongRangeSet result = other;
      for (int i = 0; i < myRanges.length; i += 2) {
        result = result.subtract(range(myRanges[i], myRanges[i + 1]));
        if (result.isEmpty()) return true;
      }
      return false;
    }

    @Override
    public String getPresentationText(PsiType type) {
      LongRangeSet set = fromType(type);
      if (set != null) {
        LongRangeSet diff = set.subtract(this);
        if (diff instanceof Point) {
          return "!= " + diff.min();
        }
        if (diff instanceof Range && !diff.intersects(this)) {
          String min =
            diff.min() == set.min() ? "" : diff.min() == set.min() + 1 ? formatNumber(set.min()) : "<= " + formatNumber(diff.min() - 1);
          String max =
            diff.max() == set.max() ? "" : diff.max() == set.max() - 1 ? formatNumber(set.max()) : ">= " + formatNumber(diff.max() + 1);
          return StreamEx.of(min, max).without("").joining(" or ");
        }
      }
      if (myRanges.length == 4 && myRanges[0] == myRanges[1] && myRanges[2] == myRanges[3]) {
        return myRanges[0] + " or " + myRanges[2];
      }
      return "in " + toString();
    }

    @Override
    public boolean isCardinalityBigger(long cutoff) {
      long totalDiff = 0;
      for (int i = 0; i < myRanges.length; i += 2) {
        long diff = myRanges[i + 1] - myRanges[i];
        if (diff < 0) return true;
        totalDiff += diff + 1;
        if (totalDiff < 0 || totalDiff > cutoff) return true;
      }
      return false;
    }

    @Override
    public LongRangeSet castTo(PsiPrimitiveType type) {
      LongRangeSet result = empty();
      for (int i = 0; i < myRanges.length; i += 2) {
        result = result.unite(range(myRanges[i], myRanges[i + 1]).castTo(type));
      }
      return result;
    }

    @NotNull
    @Override
    public LongRangeSet abs(boolean isLong) {
      LongRangeSet result = empty();
      for (int i = 0; i < myRanges.length; i += 2) {
        result = result.unite(range(myRanges[i], myRanges[i + 1]).abs(isLong));
      }
      return result;
    }

    @NotNull
    @Override
    public LongRangeSet negate(boolean isLong) {
      LongRangeSet result = empty();
      for (int i = 0; i < myRanges.length; i += 2) {
        result = result.unite(range(myRanges[i], myRanges[i + 1]).negate(isLong));
      }
      return result;
    }

    @NotNull
    @Override
    public LongRangeSet plus(LongRangeSet other, boolean isLong) {
      if (myRanges.length > 6) {
        return range(min(), max()).plus(other, isLong);
      }
      LongRangeSet result = empty();
      for (int i = 0; i < myRanges.length; i += 2) {
        result = result.unite(range(myRanges[i], myRanges[i + 1]).plus(other, isLong));
      }
      return result;
    }

    @Override
    public LongRangeSet mul(LongRangeSet multiplier, boolean isLong) {
      if (multiplier.isEmpty()) return multiplier;
      if (multiplier instanceof Point) return multiplier.mul(this, isLong);
      return isLong ? Range.LONG_RANGE : Range.INT_RANGE;
    }

    @NotNull
    @Override
    public LongRangeSet mod(LongRangeSet divisor) {
      if(divisor.isEmpty()) return empty();
      LongRangeSet result = empty();
      for (int i = 0; i < myRanges.length; i += 2) {
        result = result.unite(range(myRanges[i], myRanges[i + 1]).mod(divisor));
      }
      return result;
    }

    @Override
    public LongStream stream() {
      return IntStream.range(0, myRanges.length / 2)
        .mapToObj(idx -> LongStream.rangeClosed(myRanges[idx * 2], myRanges[idx * 2 + 1]))
        .reduce(LongStream::concat).orElseGet(LongStream::empty);
    }

    @Override
    long[] asRanges() {
      return myRanges;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myRanges);
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      return o instanceof RangeSet && Arrays.equals(myRanges, ((RangeSet)o).myRanges);
    }

    @Override
    public String toString() {
      StringJoiner sb = new StringJoiner(", ", "{", "}");
      for (int i = 0; i < myRanges.length; i += 2) {
        sb.add(LongRangeSet.toString(myRanges[i], myRanges[i + 1]));
      }
      return sb.toString();
    }
  }
}

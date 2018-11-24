/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow.rangeSet;

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * An immutable set of long values optimized for small number of ranges.
 *
 * @author Tagir Valeev
 */
public abstract class LongRangeSet {
  LongRangeSet() {}

  /**
   * Subtracts given set from the current
   *
   * @param other set to subtract
   * @return a new set
   */
  public abstract LongRangeSet subtract(LongRangeSet other);

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
  public abstract LongRangeSet intersect(LongRangeSet other);

  /**
   * Merge current set with other
   *
   * @param other other set to merge with
   * @return a new set
   */
  public LongRangeSet union(LongRangeSet other) {
    if(other.isEmpty() || other == this) return this;
    if(other.contains(this)) return other;
    // TODO: optimize
    return Range.LONG_RANGE.subtract(Range.LONG_RANGE.subtract(this).intersect(Range.LONG_RANGE.subtract(other)));
  }

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
  public LongRangeSet fromRelation(@Nullable DfaRelationValue.RelationType relation) {
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

  /**
   * Returns a range which represents all the possible values after applying {@link Math#abs(int)} or {@link Math#abs(long)}
   * to the values from this set
   *
   * @param isLong whether {@link Math#abs(long)} is applied
   * @return a new range
   */
  public abstract LongRangeSet abs(boolean isLong);

  /**
   * Returns a range which represents all the possible values after applying {@code x & y} operation for
   * all {@code x} from this set and for all {@code y} from the other set. The resulting set may contain
   * some more values.
   *
   * @param other other set to perform bitwise-and with
   * @return a new range
   */
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
    LongRangeSet result = all();
    for (int i = 0; i < left.length; i += 2) {
      for (int j = 0; j < right.length; j += 2) {
        result = result.subtract(bitwiseAnd(left[i], left[i + 1], right[j], right[j + 1]));
      }
    }
    return all().subtract(result);
  }

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

  private static LongRangeSet bitwiseAnd(long leftFrom, long leftTo, long rightFrom, long rightTo) {
    if (leftFrom == leftTo && rightFrom == rightTo) {
      return point(leftFrom & rightFrom);
    }
    ThreeState[] leftBits = bits(leftFrom, leftTo);
    ThreeState[] rightBits = bits(rightFrom, rightTo);
    ThreeState[] resultBits = new ThreeState[Long.SIZE];
    for (int i = 0; i < Long.SIZE; i++) {
      if (leftBits[i] == ThreeState.NO || rightBits[i] == ThreeState.NO) {
        resultBits[i] = ThreeState.NO;
      }
      else if (leftBits[i] == ThreeState.UNSURE || rightBits[i] == ThreeState.UNSURE) {
        resultBits[i] = ThreeState.UNSURE;
      }
      else {
        resultBits[i] = ThreeState.YES;
      }
    }
    return fromBits(resultBits);
  }

  /**
   * Creates a set which contains all the numbers satisfying the supplied bit vector.
   * Vector format is the same as returned by {@link #bits(long, long)}. The resulting set may
   * contain more values than necessary.
   *
   * @param bits a bit vector
   * @return a new LongRangeSet
   */
  private static LongRangeSet fromBits(ThreeState[] bits) {
    long from = 0;
    int i = 0;
    while (i < Long.SIZE && bits[i] != ThreeState.UNSURE) {
      if (bits[i] == ThreeState.YES) {
        from |= (1L << (Long.SIZE - 1 - i));
      }
      i++;
    }
    long to = ((1L << (Long.SIZE - i)) - 1) | from;
    int j = Long.SIZE - 1;
    while(j > i && bits[j] != ThreeState.UNSURE) {
      if (bits[j] == ThreeState.NO) {
        to &= ~(1L << Long.SIZE - 1 - j);
      }
      j--;
    }
    if(i == j) {
      return point(from).union(point(to));
    }
    return from < to ? range(from, to) : range(to, from);
  }

  /**
   * Returns a bit vector for values between from and to.
   *
   * @param from lower bound
   * @param to upper bound
   * @return an array of 64 ThreeState values (NO = zero bit for all values, YES = one bit for all values,
   * UNSURE = both one and zero possible)
   */
  private static ThreeState[] bits(long from, long to) {
    ThreeState[] bits = new ThreeState[Long.SIZE];
    Arrays.setAll(bits, idx -> ThreeState.NO);
    while (true) {
      int fromBit = Long.numberOfLeadingZeros(from);
      int toBit = Long.numberOfLeadingZeros(to);
      if (fromBit != toBit) {
        for (int i = Math.min(fromBit, toBit); i < Long.SIZE; i++) {
          bits[i] = ThreeState.UNSURE;
        }
        break;
      }
      if (fromBit == 64) break;
      bits[fromBit] = ThreeState.YES;
      long clearMask = ~(1L << (Long.SIZE - 1 - fromBit));
      from &= clearMask;
      to &= clearMask;
    }
    return bits;
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
    return new Point(value);
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

  @Nullable
  public static LongRangeSet fromDfaValue(DfaValue value) {
    if (value instanceof DfaRangeValue) {
      return ((DfaRangeValue)value).getValue();
    }
    if (value instanceof DfaConstValue) {
      return fromConstant(((DfaConstValue)value).getValue());
    }
    if (value instanceof DfaVariableValue) {
      return fromType(((DfaVariableValue)value).getVariableType());
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
    return from == to ? new Point(from) : new Range(from, to);
  }

  abstract long[] asRanges();

  static String toString(long from, long to) {
    return from == to ? String.valueOf(from) : from + (to - from == 1 ? ", " : "..") + to;
  }

  static long minValue(boolean isLong) {
    return isLong ? Long.MIN_VALUE : Integer.MIN_VALUE;
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
    if (type == null) {
      return null;
    }
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

  static final class Empty extends LongRangeSet {
    static final LongRangeSet EMPTY = new Empty();

    @Override
    public LongRangeSet subtract(LongRangeSet other) {
      return this;
    }

    @Override
    public LongRangeSet intersect(LongRangeSet other) {
      return this;
    }

    @Override
    public LongRangeSet union(LongRangeSet other) {
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
    public LongRangeSet abs(boolean isLong) {
      return this;
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
    final long myValue;

    Point(long value) {
      myValue = value;
    }

    @Override
    public LongRangeSet subtract(LongRangeSet other) {
      return other.contains(myValue) ? Empty.EMPTY : this;
    }

    @Override
    public LongRangeSet intersect(LongRangeSet other) {
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
    public LongRangeSet abs(boolean isLong) {
      return myValue >= 0 || myValue == minValue(isLong) ? this : point(-myValue);
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
      return o != null && o instanceof Point && myValue == ((Point)o).myValue;
    }

    @Override
    public String toString() {
      return "{" + myValue + "}";
    }
  }

  static final class Range extends LongRangeSet {
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
    public LongRangeSet subtract(LongRangeSet other) {
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
        long from = ((Range)other).myFrom;
        long to = ((Range)other).myTo;
        if (to < myFrom || from > myTo) return this;
        if (from <= myFrom && to >= myTo) return Empty.EMPTY;
        if (from > myFrom && to < myTo) {
          return new RangeSet(new long[]{myFrom, from - 1, to + 1, myTo});
        }
        if (from <= myFrom) {
          return range(to + 1, myTo);
        }
        if (to >= myTo) {
          return range(myFrom, from - 1);
        }
        throw new InternalError("Impossible: " + this + ":" + other);
      }
      long[] ranges = ((RangeSet)other).myRanges;
      LongRangeSet result = this;
      for (int i = 0; i < ranges.length; i += 2) {
        result = result.subtract(range(ranges[i], ranges[i + 1]));
        if (result.isEmpty()) return result;
      }
      return result;
    }

    @Override
    public LongRangeSet intersect(LongRangeSet other) {
      if (other == this) return this;
      if (other.isEmpty()) return other;
      if (other instanceof Point) {
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
        return other.intersects(this);
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
      if (myFrom <= minValue) {
        return new RangeSet(new long[]{minValue, minValue, low, hi});
      }
      else {
        return new Range(low, hi);
      }
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
      return o != null && o instanceof Range && myFrom == ((Range)o).myFrom && myTo == ((Range)o).myTo;
    }

    @Override
    public String toString() {
      return "{" + toString(myFrom, myTo) + "}";
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
    public LongRangeSet subtract(LongRangeSet other) {
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

    @Override
    public LongRangeSet intersect(LongRangeSet other) {
      if (other == this) return this;
      if (other.isEmpty()) return other;
      if (other instanceof Point || other instanceof Range) {
        return other.intersect(this);
      }
      return subtract(all().subtract(other));
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
      return other.subtract(this).isEmpty();
    }

    @Override
    public LongRangeSet abs(boolean isLong) {
      LongRangeSet result = all();
      for (int i = 0; i < myRanges.length; i += 2) {
        result = result.subtract(range(myRanges[i], myRanges[i + 1]).abs(isLong));
      }
      return all().subtract(result);
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
      return o != null && o instanceof RangeSet && Arrays.equals(myRanges, ((RangeSet)o).myRanges);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("{");
      for (int i = 0; i < myRanges.length; i += 2) {
        if (i > 0) sb.append(", ");
        sb.append(LongRangeSet.toString(myRanges[i], myRanges[i + 1]));
      }
      sb.append("}");
      return sb.toString();
    }
  }
}

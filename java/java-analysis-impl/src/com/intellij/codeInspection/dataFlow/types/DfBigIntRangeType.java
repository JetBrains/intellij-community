// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

class DfBigIntRangeType implements DfType {

  private static final NumberFormat formatter = new DecimalFormat("0.####################E0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

  static class InfBigInteger implements Comparable<InfBigInteger> {

    @NotNull
    private final BigInteger myValue;
    @NotNull
    private final InfType myInfType;

    final static InfBigInteger POSITIVE_INFINITY = new InfBigInteger(null, InfType.POSITIVE_INFINITY);
    final static InfBigInteger NEGATIVE_INFINITY = new InfBigInteger(null, InfType.NEGATIVE_INFINITY);

    private InfBigInteger(@Nullable BigInteger value, @NotNull InfType type) {
      myInfType = type;
      if (type == InfType.CERTAIN) {
        assert value != null;
        myValue = value;
      }
      else {
        myValue = BigInteger.ZERO;
      }
    }

    @Override
    public int compareTo(@NotNull DfBigIntRangeType.InfBigInteger other) {
      if (myInfType == other.myInfType) {
        if (myInfType == InfType.CERTAIN) {
          return myValue.compareTo(other.myValue);
        }
        return 0;
      }
      if (myInfType == InfType.NEGATIVE_INFINITY) {
        return -1;
      }
      if (myInfType == InfType.POSITIVE_INFINITY) {
        return 1;
      }
      if (other.myInfType == InfType.NEGATIVE_INFINITY) {
        return 1;
      }
      //other.myInfType == InfType.POSITIVE_INFINITY
      return -1;
    }

    public DfBigIntRangeType.InfBigInteger max(DfBigIntRangeType.InfBigInteger other) {
      return this.compareTo(other) < 0 ? other : this;
    }

    public DfBigIntRangeType.InfBigInteger min(DfBigIntRangeType.InfBigInteger other) {
      return this.compareTo(other) < 0 ? this : other;
    }

    public int compareTo(BigInteger other) {
      if (myInfType == InfType.NEGATIVE_INFINITY) {
        return -1;
      }
      if (myInfType == InfType.POSITIVE_INFINITY) {
        return 1;
      }
      return myValue.compareTo(other);
    }


    public boolean isPositiveInfinity() {
      return myInfType == InfType.POSITIVE_INFINITY;
    }

    public boolean isNegativeInfinity() {
      return myInfType == InfType.NEGATIVE_INFINITY;
    }


    static InfBigInteger create(@NotNull BigInteger value) {
      return new InfBigInteger(value, InfType.CERTAIN);
    }
  }

  enum InfType {
    CERTAIN, NEGATIVE_INFINITY, POSITIVE_INFINITY
  }

  @NotNull
  private final InfBigInteger myFrom;

  @NotNull
  private final InfBigInteger myTo;
  private final boolean myInvert;

  DfBigIntRangeType(@NotNull InfBigInteger from,
                    @NotNull InfBigInteger to,
                    boolean invert) {
    myFrom = from;
    myTo = to;
    myInvert = invert;
  }

  static DfType create(@NotNull InfBigInteger from,
                       @NotNull InfBigInteger to, boolean invert) {
    if (from.compareTo(to) > 0) {
      return DfType.BOTTOM;
    }
    if (to.isPositiveInfinity() && from.isNegativeInfinity()) {
      if (invert) {
        return DfType.BOTTOM;
      }
      else {
        return new DfBigIntRangeType(from, to, false);
      }
    }
    if (to.isPositiveInfinity()) {
      to = nextDown(from);
      from = InfBigInteger.NEGATIVE_INFINITY;
      invert = !invert;
    }
    if (!invert && !to.isPositiveInfinity() && !to.isNegativeInfinity() && from.compareTo(to) == 0) {
      return new DfBigIntConstantType(from.myValue);
    }
    return new DfBigIntRangeType(from, to, invert);
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM || other.equals(this)) return true;
    if (other instanceof DfBigIntConstantType) {
      BigInteger val = ((DfBigIntConstantType)other).getValue();
      int from = myFrom.compareTo(val);
      int to = -myTo.compareTo(val);
      return (from <= 0 && to <= 0) != myInvert;
    }
    if (other instanceof DfBigIntRangeType range) {
      if (!myInvert && myFrom.isNegativeInfinity() && myTo.isPositiveInfinity()) return true;
      int from = myFrom.compareTo(range.myFrom);
      int to = range.myTo.compareTo(myTo);
      if (myInvert) {
        if (range.myInvert) {
          return from >= 0 && to >= 0;
        }
        else {
          return range.myTo.compareTo(myFrom) < 0 || range.myFrom.compareTo(myTo) > 0;
        }
      }
      else {
        return !range.myInvert && from <= 0 && to <= 0;
      }
    }
    return false;
  }

  @Override
  public @NotNull DfType join(@NotNull DfType other) {
    return join(other, false);
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    return join(other, true);
  }

  DfType join(@NotNull DfType other, boolean exact) {
    if (other.isSuperType(this)) return other;
    if (this.isSuperType(other)) return this;
    if (other instanceof DfBigIntConstantType) {
      BigInteger value = ((DfBigIntConstantType)other).getValue();
      return joinRange(InfBigInteger.create(value), InfBigInteger.create(value), exact);
    }
    if (!(other instanceof DfBigIntRangeType)) {
      return exact ? null : TOP;
    }
    DfBigIntRangeType range = (DfBigIntRangeType)other;
    DfBigIntRangeType res = this;
    if (range.myInvert) {
      if (!range.myFrom.isNegativeInfinity()) {
        res = res.joinRange(InfBigInteger.NEGATIVE_INFINITY, nextDown(range.myFrom), exact);
        if (res == null) return null;
      }
      if (!range.myTo.isPositiveInfinity()) {
        res = res.joinRange(nextUp(range.myTo), InfBigInteger.POSITIVE_INFINITY, exact);
        if (res == null) return null;
      }
    }
    else {
      res = res.joinRange(range.myFrom, range.myTo, exact);
    }
    return res;
  }

  private DfBigIntRangeType joinRange(InfBigInteger from, InfBigInteger to, boolean exact) {
    if (from.compareTo(to) > 0) return this;
    if (myInvert) {
      if (to.compareTo(myFrom) < 0 || from.compareTo(myTo) > 0) return this;
      int fromCmp = myFrom.compareTo(from);
      int toCmp = to.compareTo(myTo);
      if (fromCmp < 0 && toCmp < 0) {
        return exact ? null : (DfBigIntRangeType)create(InfBigInteger.NEGATIVE_INFINITY, InfBigInteger.POSITIVE_INFINITY, false);
      }
      if (fromCmp >= 0 && toCmp >= 0) {
        return (DfBigIntRangeType)create(InfBigInteger.NEGATIVE_INFINITY, InfBigInteger.POSITIVE_INFINITY, false);
      }
      if (fromCmp >= 0) {
        return (DfBigIntRangeType)create(nextUp(to), myTo, true);
      }
      return (DfBigIntRangeType)create(myFrom, nextDown(from), true);
    }
    else {
      if (myTo.compareTo(nextDown(from)) < 0 || to.compareTo(nextDown(myFrom)) < 0) {
        if (exact) return null;
      }
      return (DfBigIntRangeType)create(myFrom.min(from), myTo.max(to), false);
    }
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    if (other.isSuperType(this)) return this;
    if (this.isSuperType(other)) return other;
    if (!(other instanceof DfBigIntRangeType)) return DfType.BOTTOM;
    DfBigIntRangeType range = (DfBigIntRangeType)other;
    if (!myInvert) {
      if (!range.myInvert) {
        DfBigIntRangeType.InfBigInteger from = myFrom.max(range.myFrom);
        DfBigIntRangeType.InfBigInteger to = myTo.min(range.myTo);
        return create(from, to, false);
      }
      else {
        int fromCmp = myFrom.compareTo(range.myFrom);
        int toCmp = range.myTo.compareTo(myTo);
        if (fromCmp >= 0) {
          return create(myFrom.max(nextUp(range.myTo)), myTo, false);
        }
        if (toCmp >= 0) {
          return create(myFrom, myTo.min(nextDown(range.myFrom)), false);
        }
        if (myFrom.isNegativeInfinity() && myTo.isPositiveInfinity()) {
          return create(range.myFrom, range.myTo, true);
        }
        // disjoint [myFrom, nextDown(range.myFrom)] U [nextUp(range.myTo), myTo] -- not supported
        return create(myFrom, myTo, false);
      }
    }
    else {
      if (!range.myInvert) {
        return range.meet(this);
      }
      else {
        // both inverted
        if (myTo.compareTo(nextDown(range.myFrom)) >= 0 && range.myTo.compareTo(nextDown(myFrom)) >= 0) {
          // excluded ranges intersect or touch each other: we can exclude their union
          DfBigIntRangeType.InfBigInteger from = myFrom.min(range.myFrom);
          DfBigIntRangeType.InfBigInteger to = myTo.min(range.myTo);
          return create(from, to, true);
        }
        if (myFrom.compareTo(range.myFrom) < 0) {
          return create(myFrom, myTo, true);
        }
        return create(range.myFrom, range.myTo, true);
      }
    }
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    if (relationType == RelationType.EQ) {
      return this;
    }
    if (myInvert) {
      DfBigIntRangeType.InfBigInteger max = myTo.isPositiveInfinity() ? nextDown(myFrom) : InfBigInteger.POSITIVE_INFINITY;
      DfBigIntRangeType.InfBigInteger min = myFrom.isNegativeInfinity() ? nextUp(myTo) : InfBigInteger.NEGATIVE_INFINITY;
      return fromRelation(relationType, min, max);
    }
    return fromRelation(relationType, myFrom, myTo);
  }

  static @NotNull DfType fromRelation(@NotNull RelationType relationType,
                                      DfBigIntRangeType.InfBigInteger min,
                                      DfBigIntRangeType.InfBigInteger max) {
    switch (relationType) {
      case LE:
        return create(InfBigInteger.NEGATIVE_INFINITY, max, false);
      case LT:
        return create(InfBigInteger.NEGATIVE_INFINITY, nextDown(max), false);
      case GE:
        return create(min, InfBigInteger.POSITIVE_INFINITY, false);
      case GT:
        return create(nextUp(min), InfBigInteger.POSITIVE_INFINITY, false);
      case EQ:
        return create(min, max, false);
      case NE:
        if (min.compareTo(max) == 0) {
          return create(min, min, true);
        }
        return create(InfBigInteger.NEGATIVE_INFINITY, InfBigInteger.POSITIVE_INFINITY, false);
      default:
        return create(InfBigInteger.NEGATIVE_INFINITY, InfBigInteger.POSITIVE_INFINITY, false);
    }
  }

  @Override
  public @NotNull DfType tryNegate() {
    return create(myFrom, myTo, !myInvert);
  }

  @Override
  public @NotNull String toString() {
    String range;
    if (myInvert) {
      if (myFrom.compareTo(myTo) == 0) {
        range = "!= " + format(myFrom);
      }
      else {
        String first = myFrom.isNegativeInfinity() ? "" : formatRange(InfBigInteger.NEGATIVE_INFINITY, nextDown(myFrom));
        String second = myTo.isPositiveInfinity() ? "" : formatRange(nextUp(myTo), InfBigInteger.POSITIVE_INFINITY);
        range = StreamEx.of(first, second).without("").joining(" || ");
      }
    }
    else {
      range = formatRange(myFrom, myTo);
    }
    String result = "BigInt";
    if (!range.isEmpty()) {
      result += " " + range;
    }
    return result;
  }

  private static InfBigInteger nextDown(InfBigInteger val) {
    if (val.myInfType != InfType.CERTAIN) {
      return val;
    }
    return InfBigInteger.create(val.myValue.subtract(BigInteger.ONE));
  }

  private static InfBigInteger nextUp(InfBigInteger val) {
    if (val.myInfType != InfType.CERTAIN) {
      return val;
    }
    return InfBigInteger.create(val.myValue.add(BigInteger.ONE));
  }

  private static String formatRange(InfBigInteger from, InfBigInteger to) {
    int cmp = from.compareTo(to);
    if (cmp == 0) return format(from);
    if (from.isNegativeInfinity()) {
      if (to.isPositiveInfinity()) return "";
      return formatTo(to);
    }
    if (to.isPositiveInfinity()) {
      return formatFrom(from);
    }
    return formatFrom(from) + " && " + formatTo(to);
  }


  @NotNull
  static String format(InfBigInteger value) {
    if (value.isNegativeInfinity()) {
      return "Neg inf";
    }
    if (value.isPositiveInfinity()) {
      return "Pos inf";
    }
    final BigInteger bigInteger = value.myValue;
    if (bigInteger.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
      return formatter.format(bigInteger);
    }
    return bigInteger.toString();
  }

  @NotNull
  private static String formatFrom(InfBigInteger from) {
    final String formatted = format(from);
    if (formatted.contains("E")) {
      if (new BigDecimal(formatted).compareTo(new BigDecimal(from.myValue)) < 0) {
        return "> " + formatted;
      }
      else {
        return ">= " + formatted;
      }
    }
    else {
      return ">= " + formatted;
    }
  }

  @NotNull
  private static String formatTo(InfBigInteger to) {
    final String formatted = format(to);
    if (formatted.contains("E")) {
      if (new BigDecimal(formatted).compareTo(new BigDecimal(to.myValue)) > 0) {
        return "< " + formatted;
      }
      else {
        return "<= " + formatted;
      }
    }
    else {
      return "<= " + formatted;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DfBigIntRangeType type = (DfBigIntRangeType)o;
    return type.myFrom.compareTo(myFrom) == 0 &&
           type.myTo.compareTo(myTo) == 0 &&
           myInvert == type.myInvert;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFrom, myTo, myInvert);
  }
}

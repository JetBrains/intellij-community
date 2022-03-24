// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class DfDoubleRangeType implements DfDoubleType {
  private final double myFrom, myTo;
  private final boolean myInvert, myNaN;

  DfDoubleRangeType(double from, double to, boolean invert, boolean nan) {
    myFrom = from;
    myTo = to;
    myInvert = invert;
    myNaN = nan;
  }
  
  static DfType create(double from, double to, boolean invert, boolean nan) {
    assert !Double.isNaN(from);
    assert !Double.isNaN(to);
    if (Double.compare(from, to) > 0) {
      return nan ? new DfDoubleConstantType(Double.NaN) : DfType.BOTTOM;
    }
    if (to == Double.POSITIVE_INFINITY && from == Double.NEGATIVE_INFINITY) {
      if (invert) {
        return nan ? new DfDoubleConstantType(Double.NaN) : DfType.BOTTOM;
      }
      return new DfDoubleRangeType(from, to, false, nan);
    }
    if (to == Double.POSITIVE_INFINITY) {
      to = nextDown(from);
      from = Double.NEGATIVE_INFINITY;
      invert = !invert;
    }
    if (!nan && !invert && Double.compare(from, to) == 0) {
      return new DfDoubleConstantType(from);
    }
    if (!nan && invert && from == Double.NEGATIVE_INFINITY && to == nextDown(Double.POSITIVE_INFINITY)) {
      return new DfDoubleConstantType(Double.POSITIVE_INFINITY);
    }
    return new DfDoubleRangeType(from, to, invert, nan);
  } 

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM || other.equals(this)) return true;
    if (other instanceof DfDoubleConstantType) {
      double val = ((DfDoubleConstantType)other).getValue();
      if (Double.isNaN(val)) return myNaN;
      int from = Double.compare(myFrom, val);
      int to = Double.compare(val, myTo);
      return (from <= 0 && to <= 0) != myInvert;
    }
    if (other instanceof DfDoubleRangeType) {
      DfDoubleRangeType range = (DfDoubleRangeType)other;
      if (range.myNaN && !myNaN) return false;
      if (!myInvert && myFrom == Double.NEGATIVE_INFINITY && myTo == Double.POSITIVE_INFINITY) return true;
      int from = Double.compare(myFrom, range.myFrom);
      int to = Double.compare(range.myTo, myTo);
      if (myInvert) {
        if (range.myInvert) {
          return from >= 0 && to >= 0;
        } else {
          return Double.compare(range.myTo, myFrom) < 0 || Double.compare(range.myFrom, myTo) > 0; 
        }
      } else {
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
    if (other instanceof DfDoubleConstantType) {
      double value = ((DfDoubleConstantType)other).getValue();
      if (Double.isNaN(value)) {
        return create(myFrom, myTo, myInvert, true);
      }
      return joinRange(value, value, exact);
    }
    if (!(other instanceof DfDoubleRangeType)) {
      return exact ? null : TOP;
    }
    DfDoubleRangeType range = (DfDoubleRangeType)other;
    DfDoubleRangeType res = range.myNaN && !myNaN ? new DfDoubleRangeType(myFrom, myTo, myInvert, true) : this;
    if (range.myInvert) {
      if (range.myFrom > Double.NEGATIVE_INFINITY) {
        res = res.joinRange(Double.NEGATIVE_INFINITY, nextDown(range.myFrom), exact);
        if (res == null) return null;
      }
      if (range.myTo < Double.POSITIVE_INFINITY) {
        res = res.joinRange(nextUp(range.myTo), Double.POSITIVE_INFINITY, exact);
        if (res == null) return null;
      }
    } else {
      res = res.joinRange(range.myFrom, range.myTo, exact);
    }
    return res;
  }

  private DfDoubleRangeType joinRange(double from, double to, boolean exact) {
    if (Double.compare(from, to) > 0) return this;
    if (myInvert) {
      if (Double.compare(to, myFrom) < 0 || Double.compare(from, myTo) > 0) return this;
      int fromCmp = Double.compare(myFrom, from);
      int toCmp = Double.compare(to, myTo);
      if (fromCmp >= 0 && toCmp >= 0 || fromCmp < 0 && toCmp < 0) {
        return exact ? null : (DfDoubleRangeType)create(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, myNaN);
      }
      if (fromCmp >= 0) {
        return (DfDoubleRangeType)create(nextUp(to), myTo, true, myNaN);
      }
      return (DfDoubleRangeType)create(myFrom, nextDown(from), true, myNaN);
    } else {
      if (Double.compare(myTo, nextDown(from)) < 0 || Double.compare(to, nextDown(myFrom)) < 0) {
        if (myFrom == Double.NEGATIVE_INFINITY && to == Double.POSITIVE_INFINITY) {
          return (DfDoubleRangeType)create(nextUp(myTo), nextDown(from), true, myNaN);
        }
        if (exact) return null;
      }
      return (DfDoubleRangeType)create(Math.min(myFrom, from), Math.max(myTo, to), false, myNaN);
    }
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    if (other.isSuperType(this)) return this;
    if (this.isSuperType(other)) return other;
    if (!(other instanceof DfDoubleRangeType)) return DfType.BOTTOM;
    DfDoubleRangeType range = (DfDoubleRangeType)other;
    boolean nan = range.myNaN && myNaN;
    if (!myInvert) {
      if (!range.myInvert) {
        double from = Math.max(myFrom, range.myFrom);
        double to = Math.min(myTo, range.myTo);
        return create(from, to, false, nan); 
      } else {
        int fromCmp = Double.compare(myFrom, range.myFrom);
        int toCmp = Double.compare(range.myTo, myTo);
        if (fromCmp >= 0) {
          return create(Math.max(myFrom, nextUp(range.myTo)), myTo, false, nan);
        }
        if (toCmp >= 0) {
          return create(myFrom, Math.min(myTo, nextDown(range.myFrom)), false, nan);
        }
        if (myFrom == Double.NEGATIVE_INFINITY && myTo == Double.POSITIVE_INFINITY) {
          return create(range.myFrom, range.myTo, true, nan);
        }
        // disjoint [myFrom, nextDown(range.myFrom)] U [nextUp(range.myTo), myTo] -- not supported
        return create(myFrom, myTo, false, nan);
      }
    } else {
      if (!range.myInvert) {
        return range.meet(this);
      } else {
        // both inverted
        if (myTo >= Math.nextDown(range.myFrom) && range.myTo >= Math.nextDown(myFrom)) {
          // excluded ranges intersect or touch each other: we can exclude their union
          double from = Math.min(myFrom, range.myFrom);
          double to = Math.max(myTo, range.myTo);
          return create(from, to, true, nan);
        }
        // excluded ranges don't intersect: we cannot encode this case
        // just keep one of the ranges (with lesser from, for stability)
        if (myFrom < range.myFrom) {
          return create(myFrom, myTo, true, nan);
        }
        return create(range.myFrom, range.myTo, true, nan);
      }
    }
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    if (relationType == RelationType.EQ) {
      DfType result = myNaN ? this : create(myFrom, myTo, myInvert, true);
      DfType zero = DfTypes.doubleRange(-0.0, 0.0);
      return meet(zero) != BOTTOM ? result.join(zero) : result;
    }
    if (myInvert) {
      double max = myTo == Double.POSITIVE_INFINITY ? nextDown(myFrom) : Double.POSITIVE_INFINITY;
      double min = myFrom == Double.NEGATIVE_INFINITY ? nextUp(myTo) : Double.NEGATIVE_INFINITY;
      return fromRelation(relationType, min, max);
    }
    return fromRelation(relationType, myFrom, myTo);
  }

  static @NotNull DfType fromRelation(@NotNull RelationType relationType, double min, double max) {
    assert !Double.isNaN(min);
    assert !Double.isNaN(max);
    switch (relationType) {
      case LE:
        return create(Double.NEGATIVE_INFINITY, max == 0.0 ? 0.0 : max, false, true);
      case LT:
        return max == Double.NEGATIVE_INFINITY ? DfTypes.DOUBLE_NAN :
               create(Double.NEGATIVE_INFINITY, Math.nextDown(max), false, true);
      case GE:
        return create(min == 0.0 ? -0.0 : min, Double.POSITIVE_INFINITY, false, true);
      case GT:
        return min == Double.POSITIVE_INFINITY ? DfTypes.DOUBLE_NAN :
               create(Math.nextUp(min), Double.POSITIVE_INFINITY, false, true);
      case EQ:
        if (min == 0.0) min = -0.0;
        if (max == 0.0) max = 0.0;
        return create(min, max, false, true);
      case NE:
        if (min == max) {
          if (min == 0.0) {
            return create(-0.0, 0.0, true, true);
          }
          return create(min, min, true, true);
        }
        return DfTypes.DOUBLE;
      default:
        return DfTypes.DOUBLE;
    }
  }

  @Override
  public @NotNull DfType tryNegate() {
    return create(myFrom, myTo, !myInvert, !myNaN);
  }

  @Override
  public @NotNull DfType castTo(@NotNull PsiPrimitiveType type) {
    if (type.equals(PsiType.LONG)) {
      LongRangeSet range;
      if (!myInvert) {
        range = LongRangeSet.range((long)myFrom, (long)myTo);
      } else {
        range = LongRangeSet.empty();
        if (myFrom > Double.NEGATIVE_INFINITY) {
          range = range.join(LongRangeSet.range(Long.MIN_VALUE, (long)nextDown(myFrom)));
        }
        if (myTo < Double.POSITIVE_INFINITY) {
          range = range.join(LongRangeSet.range((long)nextUp(myTo), Long.MAX_VALUE));
        }
      }
      if (myNaN) {
        range = range.join(LongRangeSet.point(0));
      }
      return DfTypes.longRange(range);
    }
    if (type.equals(PsiType.INT) || type.equals(PsiType.SHORT) || type.equals(PsiType.BYTE) || type.equals(PsiType.CHAR)) {
      LongRangeSet range;
      if (!myInvert) {
        range = LongRangeSet.range((int)myFrom, (int)myTo);
      } else {
        range = LongRangeSet.empty();
        if (myFrom > Double.NEGATIVE_INFINITY) {
          range = range.join(LongRangeSet.range(Integer.MIN_VALUE, (int)nextDown(myFrom)));
        }
        if (myTo < Double.POSITIVE_INFINITY) {
          range = range.join(LongRangeSet.range((int)nextUp(myTo), Integer.MAX_VALUE));
        }
      }
      if (myNaN) {
        range = range.join(LongRangeSet.point(0));
      }
      DfType result = DfTypes.intRange(range);
      if (result instanceof DfPrimitiveType && !type.equals(PsiType.INT)) {
        return ((DfPrimitiveType)result).castTo(type);
      }
      return result;
    }
    if (type.equals(PsiType.DOUBLE)) {
      return this;
    }
    return DfType.TOP;
  }

  @Override
  public @NotNull String toString() {
    String range;
    if (myInvert) {
      if (myFrom == myTo) {
        range = "!= " + (Double.compare(myFrom, -0.0) == 0 && Double.compare(myTo, 0.0) == 0 ? "\u00B10.0" : myFrom);
      } else {
        String first = myFrom == Double.NEGATIVE_INFINITY ? "" : formatRange(Double.NEGATIVE_INFINITY, nextDown(myFrom));
        String second = myTo == Double.POSITIVE_INFINITY ? "" : formatRange(nextUp(myTo), Double.POSITIVE_INFINITY);
        range = StreamEx.of(first, second).without("").joining(" || ");
      }
    } else {
      range = formatRange(myFrom, myTo);
    }
    String result = "double";
    if (!range.isEmpty()) {
      result += " " + range;
    }
    if (!myNaN) {
      result += " not NaN";
    }
    else if (!range.isEmpty()) {
      result += " (or NaN)";
    }
    return result; 
  }

  private static double nextDown(double val) {
    // Math.nextDown returns -MIN_VALUE for 0.0. This is suitable for relations
    // (see fromRelation) but not suitable for inverted range boundary
    if (Double.compare(val, 0.0) == 0) {
      return -0.0;
    }
    return Math.nextDown(val);
  }

  private static double nextUp(double val) {
    // Math.nextUp returns MIN_VALUE for -0.0. This is suitable for relations
    // (see fromRelation) but not suitable for inverted range boundary
    if (Double.compare(val, -0.0) == 0) {
      return 0.0;
    }
    return Math.nextUp(val);
  }

  private static String formatRange(double from, double to) {
    int cmp = Double.compare(from, to);
    if (cmp == 0) return Double.toString(from);
    if (Double.compare(from, -0.0) == 0 && Double.compare(to, 0.0) == 0) {
      return "\u00B10.0"; // \u00B1 = +/-
    }
    if (from == Double.NEGATIVE_INFINITY) {
      if (to == Double.POSITIVE_INFINITY) return "";
      return formatTo(to);
    }
    if (to == Double.POSITIVE_INFINITY) {
      return formatFrom(from);
    }
    return formatFrom(from) + " && " + formatTo(to);
  }

  @NotNull
  private static String formatFrom(double from) {
    double prev = nextDown(from);
    if (Double.toString(prev).length() < Double.toString(from).length()) {
      return "> " + prev;
    }
    return ">= " + from;
  }

  @NotNull
  private static String formatTo(double to) {
    double next = nextUp(to);
    if (Double.toString(next).length() < Double.toString(to).length()) {
      return "< " + next;
    }
    return "<= " + to;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DfDoubleRangeType type = (DfDoubleRangeType)o;
    return Double.compare(type.myFrom, myFrom) == 0 &&
           Double.compare(type.myTo, myTo) == 0 &&
           myInvert == type.myInvert &&
           myNaN == type.myNaN;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFrom, myTo, myInvert, myNaN);
  }
}

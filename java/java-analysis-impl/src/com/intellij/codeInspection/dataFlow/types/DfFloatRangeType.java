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

class DfFloatRangeType implements DfFloatType {
  private final float myFrom, myTo;
  private final boolean myInvert, myNaN;

  DfFloatRangeType(float from, float to, boolean invert, boolean nan) {
    myFrom = from;
    myTo = to;
    myInvert = invert;
    myNaN = nan;
  }

  static DfType create(float from, float to, boolean invert, boolean nan) {
    assert !Float.isNaN(from);
    assert !Float.isNaN(to);
    if (Float.compare(from, to) > 0) {
      return nan ? new DfFloatConstantType(Float.NaN) : DfType.BOTTOM;
    }
    if (to == Float.POSITIVE_INFINITY && from == Float.NEGATIVE_INFINITY) {
      if (invert) {
        return nan ? new DfFloatConstantType(Float.NaN) : DfType.BOTTOM;
      }
      return new DfFloatRangeType(from, to, false, nan);
    }
    if (to == Float.POSITIVE_INFINITY) {
      to = nextDown(from);
      from = Float.NEGATIVE_INFINITY;
      invert = !invert;
    }
    if (!nan && !invert && Float.compare(from, to) == 0) {
      return new DfFloatConstantType(from);
    }
    if (!nan && invert && from == Float.NEGATIVE_INFINITY && to == nextDown(Float.POSITIVE_INFINITY)) {
      return new DfFloatConstantType(Float.POSITIVE_INFINITY);
    }
    return new DfFloatRangeType(from, to, invert, nan);
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM || other.equals(this)) return true;
    if (other instanceof DfFloatConstantType) {
      float val = ((DfFloatConstantType)other).getValue();
      if (Float.isNaN(val)) return myNaN;
      int from = Float.compare(myFrom, val);
      int to = Float.compare(val, myTo);
      return (from <= 0 && to <= 0) != myInvert;
    }
    if (other instanceof DfFloatRangeType) {
      DfFloatRangeType range = (DfFloatRangeType)other;
      if (range.myNaN && !myNaN) return false;
      if (!myInvert && myFrom == Float.NEGATIVE_INFINITY && myTo == Float.POSITIVE_INFINITY) return true;
      int from = Float.compare(myFrom, range.myFrom);
      int to = Float.compare(range.myTo, myTo);
      if (myInvert) {
        if (range.myInvert) {
          return from >= 0 && to >= 0;
        } else {
          return Float.compare(range.myTo, myFrom) < 0 || Float.compare(range.myFrom, myTo) > 0;
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
    if (other instanceof DfFloatConstantType) {
      float value = ((DfFloatConstantType)other).getValue();
      if (Float.isNaN(value)) {
        return create(myFrom, myTo, myInvert, true);
      }
      return joinRange(value, value, exact);
    }
    if (!(other instanceof DfFloatRangeType)) {
      return exact ? null : TOP;
    }
    DfFloatRangeType range = (DfFloatRangeType)other;
    DfFloatRangeType res = range.myNaN && !myNaN ? new DfFloatRangeType(myFrom, myTo, myInvert, true) : this;
    if (range.myInvert) {
      if (range.myFrom > Float.NEGATIVE_INFINITY) {
        res = res.joinRange(Float.NEGATIVE_INFINITY, nextDown(range.myFrom), exact);
        if (res == null) return null;
      }
      if (range.myTo < Float.POSITIVE_INFINITY) {
        res = res.joinRange(nextUp(range.myTo), Float.POSITIVE_INFINITY, exact);
        if (res == null) return null;
      }
    } else {
      res = res.joinRange(range.myFrom, range.myTo, exact);
    }
    return res;
  }

  private DfFloatRangeType joinRange(float from, float to, boolean exact) {
    if (Float.compare(from, to) > 0) return this;
    if (myInvert) {
      if (Float.compare(to, myFrom) < 0 || Float.compare(from, myTo) > 0) return this;
      int fromCmp = Float.compare(myFrom, from);
      int toCmp = Float.compare(to, myTo);
      if (fromCmp >= 0 && toCmp >= 0 || fromCmp < 0 && toCmp < 0) {
        return exact ? null : (DfFloatRangeType)create(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, false, myNaN);
      }
      if (fromCmp >= 0) {
        return (DfFloatRangeType)create(nextUp(to), myTo, true, myNaN);
      }
      return (DfFloatRangeType)create(myFrom, nextDown(from), true, myNaN);
    } else {
      if (Float.compare(myTo, nextDown(from)) < 0 || Float.compare(to, nextDown(myFrom)) < 0) {
        if (myFrom == Float.NEGATIVE_INFINITY && to == Float.POSITIVE_INFINITY) {
          return (DfFloatRangeType)create(nextUp(myTo), nextDown(from), true, myNaN);
        }
        if (exact) return null;
      }
      return (DfFloatRangeType)create(Math.min(myFrom, from), Math.max(myTo, to), false, myNaN);
    }
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    if (other.isSuperType(this)) return this;
    if (this.isSuperType(other)) return other;
    if (!(other instanceof DfFloatRangeType)) return DfType.BOTTOM;
    DfFloatRangeType range = (DfFloatRangeType)other;
    boolean nan = range.myNaN && myNaN;
    if (!myInvert) {
      if (!range.myInvert) {
        float from = Math.max(myFrom, range.myFrom);
        float to = Math.min(myTo, range.myTo);
        return create(from, to, false, nan);
      } else {
        int fromCmp = Float.compare(myFrom, range.myFrom);
        int toCmp = Float.compare(range.myTo, myTo);
        if (fromCmp >= 0) {
          return create(Math.max(myFrom, nextUp(range.myTo)), myTo, false, nan);
        }
        if (toCmp >= 0) {
          return create(myFrom, Math.min(myTo, nextDown(range.myFrom)), false, nan);
        }
        if (myFrom == Float.NEGATIVE_INFINITY && myTo == Float.POSITIVE_INFINITY) {
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
          float from = Math.min(myFrom, range.myFrom);
          float to = Math.max(myTo, range.myTo);
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
      DfType zero = DfTypes.floatRange(-0.0f, 0.0f);
      return meet(zero) != BOTTOM ? result.join(zero) : result;
    }
    if (myInvert) {
      float max = myTo == Float.POSITIVE_INFINITY ? nextDown(myFrom) : Float.POSITIVE_INFINITY;
      float min = myFrom == Float.NEGATIVE_INFINITY ? nextUp(myTo) : Float.NEGATIVE_INFINITY;
      return fromRelation(relationType, min, max);
    }
    return fromRelation(relationType, myFrom, myTo);
  }

  static @NotNull DfType fromRelation(@NotNull RelationType relationType, float min, float max) {
    assert !Float.isNaN(min);
    assert !Float.isNaN(max);
    return switch (relationType) {
      case LE -> create(Float.NEGATIVE_INFINITY, max == 0.0f ? 0.0f : max, false, true);
      case LT -> max == Float.NEGATIVE_INFINITY ? DfTypes.FLOAT_NAN :
               create(Float.NEGATIVE_INFINITY, Math.nextDown(max), false, true);
      case GE -> create(min == 0.0f ? -0.0f : min, Float.POSITIVE_INFINITY, false, true);
      case GT -> min == Float.POSITIVE_INFINITY ? DfTypes.FLOAT_NAN :
               create(Math.nextUp(min), Float.POSITIVE_INFINITY, false, true);
      case EQ -> {
        if (min == 0.0f) min = -0.0f;
        if (max == 0.0f) max = 0.0f;
        yield create(min, max, false, true);
      }
      case NE -> {
        if (min == max) {
          if (min == 0.0f) {
            yield create(-0.0f, 0.0f, true, true);
          }
          yield create(min, min, true, true);
        }
        yield DfTypes.FLOAT;
      }
      default -> DfTypes.FLOAT;
    };
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
        if (myFrom > Float.NEGATIVE_INFINITY) {
          range = range.join(LongRangeSet.range(Long.MIN_VALUE, (long)nextDown(myFrom)));
        }
        if (myTo < Float.POSITIVE_INFINITY) {
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
        if (myFrom > Float.NEGATIVE_INFINITY) {
          range = range.join(LongRangeSet.range(Integer.MIN_VALUE, (int)nextDown(myFrom)));
        }
        if (myTo < Float.POSITIVE_INFINITY) {
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
    if (type.equals(PsiType.FLOAT)) {
      return this;
    }
    return DfType.TOP;
  }

  @Override
  public @NotNull String toString() {
    String range;
    if (myInvert) {
      if (myFrom == myTo) {
        range = "!= " + (Float.compare(myFrom, -0.0f) == 0 && Float.compare(myTo, 0.0f) == 0 ? "\u00B10.0f" : myFrom);
      } else {
        String first = myFrom == Float.NEGATIVE_INFINITY ? "" : formatRange(Float.NEGATIVE_INFINITY, nextDown(myFrom));
        String second = myTo == Float.POSITIVE_INFINITY ? "" : formatRange(nextUp(myTo), Float.POSITIVE_INFINITY);
        range = StreamEx.of(first, second).without("").joining(" || ");
      }
    } else {
      range = formatRange(myFrom, myTo);
    }
    String result = "float";
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

  private static float nextDown(float val) {
    // Math.nextDown returns -MIN_VALUE for 0.0f. This is suitable for relations
    // (see fromRelation) but not suitable for inverted range boundary
    if (Float.compare(val, 0.0f) == 0) {
      return -0.0f;
    }
    return Math.nextDown(val);
  }

  private static float nextUp(float val) {
    // Math.nextUp returns MIN_VALUE for -0.0f. This is suitable for relations
    // (see fromRelation) but not suitable for inverted range boundary
    if (Float.compare(val, -0.0f) == 0) {
      return 0.0f;
    }
    return Math.nextUp(val);
  }

  private static String formatRange(float from, float to) {
    int cmp = Float.compare(from, to);
    if (cmp == 0) return Float.toString(from);
    if (Float.compare(from, -0.0f) == 0 && Float.compare(to, 0.0f) == 0) {
      return "\u00B10.0f"; // \u00B1 = +/-
    }
    if (from == Float.NEGATIVE_INFINITY) {
      if (to == Float.POSITIVE_INFINITY) return "";
      return formatTo(to);
    }
    if (to == Float.POSITIVE_INFINITY) {
      return formatFrom(from);
    }
    return formatFrom(from) + " && " + formatTo(to);
  }

  @NotNull
  private static String formatFrom(float from) {
    float prev = nextDown(from);
    if (Float.toString(prev).length() < Float.toString(from).length()) {
      return "> " + prev;
    }
    return ">= " + from;
  }

  @NotNull
  private static String formatTo(float to) {
    float next = nextUp(to);
    if (Float.toString(next).length() < Float.toString(to).length()) {
      return "< " + next;
    }
    return "<= " + to;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DfFloatRangeType type = (DfFloatRangeType)o;
    return Float.compare(type.myFrom, myFrom) == 0 &&
           Float.compare(type.myTo, myTo) == 0 &&
           myInvert == type.myInvert &&
           myNaN == type.myNaN;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFrom, myTo, myInvert, myNaN);
  }
}

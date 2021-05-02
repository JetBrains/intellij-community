// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

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
      to = Math.nextDown(from);
      from = Float.NEGATIVE_INFINITY;
      invert = !invert;
    }
    if (!nan && !invert && Float.compare(from, to) == 0) {
      return new DfFloatConstantType(from);
    }
    if (!nan && invert && from == Float.NEGATIVE_INFINITY && to == Math.nextDown(Float.POSITIVE_INFINITY)) {
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
    if (other.isSuperType(this)) return other;
    if (this.isSuperType(other)) return this;
    if (other instanceof DfFloatConstantType) {
      float value = ((DfFloatConstantType)other).getValue();
      if (Float.isNaN(value)) {
        return create(myFrom, myTo, myInvert, true);
      }
      return joinRange(value, value);
    }
    if (!(other instanceof DfFloatRangeType)) {
      return TOP;
    }
    DfFloatRangeType range = (DfFloatRangeType)other;
    DfFloatRangeType res = range.myNaN && !myNaN ? new DfFloatRangeType(myFrom, myTo, myInvert, true) : this;
    if (range.myInvert) {
      if (range.myFrom > Float.NEGATIVE_INFINITY) {
        res = res.joinRange(Float.NEGATIVE_INFINITY, Math.nextDown(range.myFrom));
      }
      if (range.myTo < Float.POSITIVE_INFINITY) {
        res = res.joinRange(Math.nextUp(range.myTo), Float.POSITIVE_INFINITY);
      }
    } else {
      res = res.joinRange(range.myFrom, range.myTo);
    }
    return res;
  }

  private @NotNull DfFloatRangeType joinRange(float from, float to) {
    if (Float.compare(from, to) > 0) return this;
    if (myInvert) {
      float fromCmp = Float.compare(myFrom, from);
      float toCmp = Float.compare(to, myTo);
      if (fromCmp >= 0 && toCmp >= 0 || fromCmp < 0 && toCmp < 0) {
        return (DfFloatRangeType)create(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, false, myNaN);
      }
      if (fromCmp >= 0 && toCmp < 0) {
        return (DfFloatRangeType)create(Math.nextUp(to), myTo, true, myNaN);
      }
      if (fromCmp < 0 && toCmp >= 0) {
        return (DfFloatRangeType)create(myFrom, Math.nextDown(from), true, myNaN);
      }
      throw new AssertionError("Impossible!");
    } else {
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
        float fromCmp = Float.compare(myFrom, range.myFrom);
        float toCmp = Float.compare(range.myTo, myTo);
        if (fromCmp >= 0) {
          return create(Math.nextUp(range.myTo), myTo, false, nan);
        }
        if (toCmp >= 0) {
          return create(myFrom, Math.nextDown(range.myFrom), false, nan);
        }
        if (myFrom == Float.NEGATIVE_INFINITY && myTo == Float.POSITIVE_INFINITY) {
          return create(range.myFrom, range.myTo, false, nan);
        }
        // disjoint [myFrom, nextDown(range.myFrom)] U [nextUp(range.myTo), myTo] -- not supported
        return create(myFrom, myTo, false, nan);
      }
    } else {
      if (!range.myInvert) {
        return range.meet(this);
      } else {
        float from = Math.min(myFrom, range.myFrom);
        float to = Math.max(myTo, range.myTo);
        return create(from, to, true, nan);
      }
    }
  }
  
  private float min() {
    return myInvert ? myFrom == Float.NEGATIVE_INFINITY ? Math.nextUp(myTo) : Float.NEGATIVE_INFINITY : myFrom;
  }

  private float max() {
    return myInvert ? myTo == Float.POSITIVE_INFINITY ? Math.nextDown(myFrom) : Float.POSITIVE_INFINITY : myTo;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    float max = max();
    float min = min();
    if (myInvert && relationType == RelationType.EQ) {
      float from = myFrom, to = myTo;
      if (from == 0.0f) from = -0.0f;
      if (to == 0.0f) to = 0.0f;
      return create(from, to, true, true);
    }
    return fromRelation(relationType, min, max);
  }

  static @NotNull DfType fromRelation(@NotNull RelationType relationType, float min, float max) {
    assert !Float.isNaN(min);
    assert !Float.isNaN(max);
    switch (relationType) {
      case LE:
        if (max == 0.0f) { // 0.0f or -0.0f
          return create(Float.NEGATIVE_INFINITY, 0.0f, false, true);
        }
        return create(Float.NEGATIVE_INFINITY, max, false, true);
      case LT:
        if (max == 0.0f) { // 0.0f or -0.0f
          return create(Float.NEGATIVE_INFINITY, -Float.MIN_VALUE, false, true);
        }
        return max == Float.NEGATIVE_INFINITY ? DfTypes.FLOAT_NAN :
               create(Float.NEGATIVE_INFINITY, Math.nextDown(max), false, true);
      case GE:
        if (min == 0.0f) { // 0.0f or -0.0f
          return create(-0.0f, Float.POSITIVE_INFINITY, false, true);
        }
        return create(min, Float.POSITIVE_INFINITY, false, true);
      case GT:
        if (min == 0.0f) { // 0.0f or -0.0f
          return create(Float.MIN_VALUE, Float.POSITIVE_INFINITY, false, true);
        }
        return min == Float.POSITIVE_INFINITY ? DfTypes.FLOAT_NAN :
               create(Math.nextUp(min), Float.POSITIVE_INFINITY, false, true);
      case EQ:
        if (min == 0.0f) min = -0.0f;
        if (max == 0.0f) max = 0.0f;
        return create(min, max, false, true);
      case NE:
        if (min == max) {
          if (min == 0.0f) {
            return create(-0.0f, 0.0f, true, true);
          }
          return create(min, min, true, true);
        }
        return DfTypes.FLOAT;
      default:
        return DfTypes.FLOAT;
    }
  }

  @Override
  public @NotNull DfType tryNegate() {
    return create(myFrom, myTo, !myInvert, !myNaN);
  }

  @Override
  public @NotNull String toString() {
    String range;
    if (myInvert) {
      if (myFrom == myTo) {
        range = "!= " + (Float.compare(myFrom, -0.0f) == 0 && Float.compare(myTo, 0.0f) == 0 ? "\u00B10.0f" : myFrom);
      } else {
        String first = myFrom == Float.NEGATIVE_INFINITY ? "" : formatRange(Float.NEGATIVE_INFINITY, Math.nextDown(myFrom));
        String second = myTo == Float.POSITIVE_INFINITY ? "" : formatRange(Math.nextUp(myTo), Float.POSITIVE_INFINITY);
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

  private static String formatRange(float from, float to) {
    int cmp = Float.compare(from, to);
    if (cmp == 0) return Float.toString(from);
    if (Float.compare(from, -0.0f) == 0 && Float.compare(to, 0.0f) == 0) {
      return "\u00B10.0f";
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
    float prev = Math.nextDown(from);
    if (Float.toString(prev).length() < Float.toString(from).length()) {
      return "> " + prev;
    }
    return ">= " + from;
  }

  @NotNull
  private static String formatTo(float to) {
    float next = Math.nextUp(to);
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

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public abstract class Direction {
  public static final Direction Out = explicitDirection("Out");
  public static final Direction NullableOut = explicitDirection("NullableOut");
  public static final Direction Pure = explicitDirection("Pure");
  public static final Direction Access = explicitDirection("Access");
  public static final Direction Throw = explicitDirection("Throw");
  public static final Direction Volatile = explicitDirection("Volatile");

  private static final List<Direction> ourConcreteDirections = Arrays.asList(Out, NullableOut, Pure, Access, Throw, Volatile);
  private static final int CONCRETE_DIRECTIONS_OFFSET = ourConcreteDirections.size();
  private static final int IN_OUT_OFFSET = 2; // nullity mask is 0/1
  private static final int IN_THROW_OFFSET = 2 + Value.values().length;
  private static final int DIRECTIONS_PER_PARAM_ID = IN_THROW_OFFSET + Value.values().length;

  /**
   * Converts int to Direction object.
   *
   * @param directionKey int representation of direction
   * @return Direction object
   * @see #asInt()
   */
  @NotNull
  static Direction fromInt(int directionKey) {
    if (directionKey < CONCRETE_DIRECTIONS_OFFSET) {
      return ourConcreteDirections.get(directionKey);
    }
    int paramKey = directionKey - CONCRETE_DIRECTIONS_OFFSET;
    int paramId = paramKey / DIRECTIONS_PER_PARAM_ID;
    int subDirectionId = paramKey % DIRECTIONS_PER_PARAM_ID;
    // 0 - 1 - @NotNull, @Nullable, parameter
    if (subDirectionId < IN_OUT_OFFSET) {
      return new In(paramId, subDirectionId == 1);
    }
    if (subDirectionId < IN_THROW_OFFSET) {
      int valueId = subDirectionId - IN_OUT_OFFSET;
      return new InOut(paramId, Value.values()[valueId]);
    }
    int valueId = subDirectionId - IN_THROW_OFFSET;
    return new InThrow(paramId, Value.values()[valueId]);
  }

  /**
   * Encodes Direction object as non-negative int.
   *
   * @return unique int for direction
   */
  abstract int asInt();

  /**
   * @return true if this is a null->fail direction (care should be taken to separate it from @NotNull annotation)
   */
  boolean isNullFail() {
    return false;
  }

  @Override
  public int hashCode() {
    return asInt();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    return asInt() == ((Direction)obj).asInt();
  }

  @NotNull
  private static Direction explicitDirection(String name) {
    return new Direction() {
      @Override
      int asInt() {
        for (int i = 0; i < ourConcreteDirections.size(); i++) {
          if (ourConcreteDirections.get(i) == this) return i;
        }
        throw new InternalError("Explicit direction absent in ourConcreteDirections: " + name);
      }

      @Override
      public String toString() {
        return name;
      }
    };
  }

  abstract static class ParamIdBasedDirection extends Direction {
    final int paramIndex;

    protected ParamIdBasedDirection(int index) {
      paramIndex = index;
    }

    public int paramId() {
      return paramIndex;
    }

    @Override
    int asInt() {
      return CONCRETE_DIRECTIONS_OFFSET + DIRECTIONS_PER_PARAM_ID * this.paramId();
    }
  }

  static final class In extends ParamIdBasedDirection {
    final boolean nullable;

    In(int paramIndex, boolean nullable) {
      super(paramIndex);
      this.nullable = nullable;
    }

    @Override
    int asInt() {
      return super.asInt() + (nullable ? 1 : 0);
    }

    @Override
    public String toString() {
      return "In " + paramIndex + "(" + (nullable ? "nullable" : "not null") + ")";
    }
  }

  static abstract class ParamValueBasedDirection extends ParamIdBasedDirection {
    final Value inValue;

    ParamValueBasedDirection(int paramIndex, Value inValue) {
      super(paramIndex);
      this.inValue = inValue;
    }

    abstract @NotNull ParamValueBasedDirection withIndex(int paramIndex);
    
    abstract @NotNull ParamValueBasedDirection withValue(int paramIndex, @NotNull Value inValue);
  }

  static final class InOut extends ParamValueBasedDirection {
    InOut(int paramIndex, Value inValue) {
      super(paramIndex, inValue);
    }

    @Override
    @NotNull
    InOut withIndex(int paramIndex) {
      return new InOut(paramIndex, inValue);
    }

    @Override
    @NotNull
    ParamValueBasedDirection withValue(int paramIndex, @NotNull Value inValue) {
      return new InOut(paramIndex, inValue);
    }

    @Override
    int asInt() {
      return super.asInt() + IN_OUT_OFFSET + inValue.ordinal();
    }

    @Override
    public String toString() {
      return "InOut " + paramIndex + " " + inValue.toString();
    }
  }

  static final class InThrow extends ParamValueBasedDirection {
    InThrow(int paramIndex, Value inValue) {
      super(paramIndex, inValue);
    }

    @Override
    @NotNull
    InThrow withIndex(int paramIndex) {
      return new InThrow(paramIndex, inValue);
    }

    @Override
    boolean isNullFail() {
      return inValue == Value.Null;
    }

    @Override
    @NotNull
    ParamValueBasedDirection withValue(int paramIndex, @NotNull Value inValue) {
      return new InThrow(paramIndex, inValue);
    }

    @Override
    int asInt() {
      return super.asInt() + IN_THROW_OFFSET + inValue.ordinal();
    }

    @Override
    public String toString() {
      return "InThrow " + paramIndex + " " + inValue.toString();
    }
  }
}